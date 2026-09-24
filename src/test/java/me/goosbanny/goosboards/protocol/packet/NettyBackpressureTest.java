package me.goosbanny.goosboards.protocol.packet;

import me.goosbanny.goosboards.render.diff.TileDiffer;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.PacketEventsAPI;
import com.github.retrooper.packetevents.manager.player.PlayerManager;
import com.github.retrooper.packetevents.manager.server.ServerManager;
import com.github.retrooper.packetevents.manager.server.ServerVersion;
import com.github.retrooper.packetevents.settings.PacketEventsSettings;
import io.github.retrooper.packetevents.impl.netty.NettyManagerImpl;
import io.netty.channel.Channel;
import me.goosbanny.goosboards.render.diff.TileDiffer.DirtyTile;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class NettyBackpressureTest {

    @BeforeAll
    static void setupPacketEvents() {
        if (PacketEvents.getAPI() == null || PacketEvents.getAPI().getNettyManager() == null) {
            PacketEventsAPI<?> api = mock(PacketEventsAPI.class);
            ServerManager serverManager = mock(ServerManager.class);
            PlayerManager playerManager = mock(PlayerManager.class);
            NettyManagerImpl nettyManager = new NettyManagerImpl();
            when(api.getSettings()).thenReturn(new PacketEventsSettings());
            when(api.getServerManager()).thenReturn(serverManager);
            when(api.getPlayerManager()).thenReturn(playerManager);
            when(api.getNettyManager()).thenReturn(nettyManager);
            when(serverManager.getVersion()).thenReturn(ServerVersion.V_1_20_4);
            PacketEvents.setAPI(api);
        }
    }

    @Test
    @DisplayName("Acceptance Criterion 3: Un-writable Netty channel drops packets and suppresses flushing")
    void testBackpressureDropsPacketsWhenNotWritable() {
        Channel channel = mock(Channel.class);
        when(channel.isActive()).thenReturn(true);
        when(channel.isWritable()).thenReturn(false); // Outbound buffer saturated

        DirtyTile tile = new DirtyTile(0, 0, 0, 12345L, new byte[128 * 128], 0, 0, 128, 128);
        List<DirtyTile> dirtyTiles = List.of(tile);
        int[] mapIds = new int[]{100};

        boolean written = BatchedMapSender.sendDirtyTiles(channel, mapIds, dirtyTiles);

        assertFalse(written, "Frame must be dropped when channel is not writable");
        verify(channel, never()).write(any());
        verify(channel, never()).flush();
    }

    @Test
    @DisplayName("When Netty channel recovers to writable, packets are written with single batch flush")
    void testPacketsSentWhenChannelWritable() {
        Channel channel = mock(Channel.class);
        when(channel.isActive()).thenReturn(true);
        when(channel.isWritable()).thenReturn(true); // Writable

        DirtyTile tile1 = new DirtyTile(0, 0, 0, 1111L, new byte[128 * 128]);
        DirtyTile tile2 = new DirtyTile(1, 0, 1, 2222L, new byte[128 * 128]);
        List<DirtyTile> dirtyTiles = List.of(tile1, tile2);
        int[] mapIds = new int[]{101, 102};

        boolean written = BatchedMapSender.sendDirtyTiles(channel, mapIds, dirtyTiles);

        assertTrue(written);
        verify(channel, times(2)).write(any());
        verify(channel, times(1)).flush();
    }

    @Test
    @DisplayName("Strict backpressure: channel that is not writable drops packets even on forced full flush")
    void testForcedFullFlushHonorsBackpressure() {
        Channel channel = mock(Channel.class);
        when(channel.isActive()).thenReturn(true);
        when(channel.isWritable()).thenReturn(false);

        DirtyTile tile = new DirtyTile(0, 0, 0, 9999L, new byte[128 * 128]);
        List<DirtyTile> dirtyTiles = List.of(tile);
        int[] mapIds = new int[]{200};

        boolean written = BatchedMapSender.sendDirtyTiles(channel, mapIds, dirtyTiles, true);

        assertFalse(written, "Forced full flush must drop packets when channel is not writable");
        verify(channel, never()).write(any());
        verify(channel, never()).flush();
    }
}