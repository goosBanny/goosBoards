package me.goosbanny.goosboards.render;

import me.goosbanny.goosboards.protocol.packet.BatchedMapSender;
import me.goosbanny.goosboards.render.diff.TileDiffer;

import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerMapData;
import io.netty.channel.Channel;
import me.goosbanny.goosboards.render.diff.TileDiffer.DirtyTile;
import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.PacketEventsAPI;
import com.github.retrooper.packetevents.manager.player.PlayerManager;
import com.github.retrooper.packetevents.manager.server.ServerManager;
import com.github.retrooper.packetevents.manager.server.ServerVersion;
import com.github.retrooper.packetevents.settings.PacketEventsSettings;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SingleInstanceBroadcastTest {

    @BeforeAll
    static void setupPacketEvents() {
        if (PacketEvents.getAPI() == null || PacketEvents.getAPI().getServerManager() == null) {
            PacketEventsAPI<?> api = mock(PacketEventsAPI.class);
            ServerManager serverManager = mock(ServerManager.class);
            PlayerManager playerManager = mock(PlayerManager.class);
            when(api.getSettings()).thenReturn(new PacketEventsSettings());
            when(api.getServerManager()).thenReturn(serverManager);
            when(api.getPlayerManager()).thenReturn(playerManager);
            when(serverManager.getVersion()).thenReturn(ServerVersion.V_1_20_4);
            PacketEvents.setAPI(api);
        }
    }

    @Test
    @DisplayName("Single-instance broadcast shares immutable packet reference across all viewer channels")
    void testSingleInstanceBroadcast() {
        int viewerCount = 5;
        List<Channel> channels = new ArrayList<>();
        for (int i = 0; i < viewerCount; i++) {
            Channel ch = mock(Channel.class);
            when(ch.isActive()).thenReturn(true);
            when(ch.isWritable()).thenReturn(true);
            channels.add(ch);
        }

        // Add 1 saturated channel (not writable) to verify backpressure
        Channel saturatedChannel = mock(Channel.class);
        when(saturatedChannel.isActive()).thenReturn(true);
        when(saturatedChannel.isWritable()).thenReturn(false);
        channels.add(saturatedChannel);

        DirtyTile tile1 = new DirtyTile(0, 0, 0, 101L, new byte[128 * 128], 10, 10, 20, 20);
        DirtyTile tile2 = new DirtyTile(1, 0, 1, 102L, new byte[128 * 128], 0, 0, 128, 128);
        List<DirtyTile> dirtyTiles = List.of(tile1, tile2);
        int[] mapIds = new int[]{501, 502};

        // 1. Prebuild packets once
        List<WrapperPlayServerMapData> packets = BatchedMapSender.prebuildPackets(mapIds, dirtyTiles);
        assertEquals(2, packets.size());
        WrapperPlayServerMapData pkt1 = packets.get(0);
        WrapperPlayServerMapData pkt2 = packets.get(1);

        // 2. Broadcast to all channels
        BatchedMapSender.broadcastDirtyPackets(channels, packets);

        // 3. Verify each writable channel received the exact same packet instance reference
        for (int i = 0; i < viewerCount; i++) {
            Channel ch = channels.get(i);
            ArgumentCaptor<WrapperPlayServerMapData> captor = ArgumentCaptor.forClass(WrapperPlayServerMapData.class);
            verify(ch, times(2)).write(captor.capture());
            verify(ch, times(1)).flush();

            List<WrapperPlayServerMapData> writtenPackets = captor.getAllValues();
            assertSame(pkt1, writtenPackets.get(0), "Viewer " + i + " must receive identical packet reference 1");
            assertSame(pkt2, writtenPackets.get(1), "Viewer " + i + " must receive identical packet reference 2");
        }

        // 4. Verify saturated channel had zero writes and zero flushes
        verify(saturatedChannel, never()).write(any());
        verify(saturatedChannel, never()).flush();
    }
}