package me.goosbanny.goosboards.protocol;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.PacketEventsAPI;
import com.github.retrooper.packetevents.manager.player.PlayerManager;
import com.github.retrooper.packetevents.manager.server.ServerManager;
import com.github.retrooper.packetevents.manager.server.ServerVersion;
import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.protocol.player.ClientVersion;
import com.github.retrooper.packetevents.settings.PacketEventsSettings;
import com.github.retrooper.packetevents.wrapper.PacketWrapper;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerDestroyEntities;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnEntity;
import io.github.retrooper.packetevents.netty.NettyManagerImpl;
import me.goosbanny.goosboards.display.DisplayPlane;
import me.goosbanny.goosboards.raycast.Vector3d;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class VirtualEntityRigProtocolTest {

    private static PlayerManager mockPlayerManager;

    @BeforeAll
    static void setupPacketEvents() {
        @SuppressWarnings("unchecked")
        PacketEventsAPI<Object> api = Mockito.mock(PacketEventsAPI.class);
        ServerManager serverManager = Mockito.mock(ServerManager.class);
        mockPlayerManager = Mockito.mock(PlayerManager.class);
        PacketEventsSettings settings = new PacketEventsSettings();
        NettyManagerImpl nettyManager = new NettyManagerImpl();

        Mockito.when(api.getSettings()).thenReturn(settings);
        Mockito.when(api.getServerManager()).thenReturn(serverManager);
        Mockito.when(api.getPlayerManager()).thenReturn(mockPlayerManager);
        Mockito.when(api.getNettyManager()).thenReturn(nettyManager);
        Mockito.when(serverManager.getVersion()).thenReturn(ServerVersion.V_1_20_4);

        PacketEvents.setAPI(api);
    }

    @Test
    @DisplayName("Protocol >= 768 uses metadata index 9 for ItemStack")
    void testProtocol768MetadataIndex() {
        Player mockPlayer = Mockito.mock(Player.class);
        Mockito.when(mockPlayer.getName()).thenReturn("TestModernPlayer");
        Mockito.when(mockPlayerManager.getClientVersion(mockPlayer)).thenReturn(ClientVersion.V_1_21_2);

        DisplayPlane plane = new DisplayPlane(
                UUID.randomUUID(), "world", new Vector3d(0, 10, 0), 1, 1,
                new Vector3d(1, 0, 0), new Vector3d(0, -1, 0), 32.0, 16.0
        );

        QuarantinedIdAllocator allocator = new QuarantinedIdAllocator();
        List<PacketWrapper<?>> captured = new ArrayList<>();

        VirtualEntityRig rig = new VirtualEntityRig(mockPlayer, plane, allocator, captured::add);
        rig.spawn();

        WrapperPlayServerEntityMetadata metaPacket = null;
        for (PacketWrapper<?> packet : captured) {
            if (packet instanceof WrapperPlayServerEntityMetadata meta) {
                metaPacket = meta;
                break;
            }
        }

        assertNotNull(metaPacket, "Metadata packet must be dispatched on spawn");
        List<EntityData<?>> entityData = metaPacket.getEntityMetadata();
        boolean hasIndex9 = entityData.stream().anyMatch(d -> d.getIndex() == 9);
        assertTrue(hasIndex9, "Protocol >= 768 (1.21.2+) must assign item metadata to index 9");
    }

    @Test
    @DisplayName("Protocol < 768 uses metadata index 8 for ItemStack")
    void testProtocolLegacyMetadataIndex() {
        Player mockPlayer = Mockito.mock(Player.class);
        Mockito.when(mockPlayer.getName()).thenReturn("TestLegacyPlayer");
        Mockito.when(mockPlayerManager.getClientVersion(mockPlayer)).thenReturn(ClientVersion.V_1_20_2);

        DisplayPlane plane = new DisplayPlane(
                UUID.randomUUID(), "world", new Vector3d(0, 10, 0), 1, 1,
                new Vector3d(1, 0, 0), new Vector3d(0, -1, 0), 32.0, 16.0
        );

        QuarantinedIdAllocator allocator = new QuarantinedIdAllocator();
        List<PacketWrapper<?>> captured = new ArrayList<>();

        VirtualEntityRig rig = new VirtualEntityRig(mockPlayer, plane, allocator, captured::add);
        rig.spawn();

        WrapperPlayServerEntityMetadata metaPacket = null;
        for (PacketWrapper<?> packet : captured) {
            if (packet instanceof WrapperPlayServerEntityMetadata meta) {
                metaPacket = meta;
                break;
            }
        }

        assertNotNull(metaPacket, "Metadata packet must be dispatched on spawn");
        List<EntityData<?>> entityData = metaPacket.getEntityMetadata();
        boolean hasIndex8 = entityData.stream().anyMatch(d -> d.getIndex() == 8);
        assertTrue(hasIndex8, "Protocol < 768 (<= 1.21.1) must assign item metadata to index 8");
    }

    @Test
    @DisplayName("Despawn sends DestroyEntities packet and releases IDs")
    void testDespawn() {
        Player mockPlayer = Mockito.mock(Player.class);
        Mockito.when(mockPlayer.getName()).thenReturn("DespawnPlayer");
        Mockito.when(mockPlayerManager.getClientVersion(mockPlayer)).thenReturn(ClientVersion.V_1_21);

        DisplayPlane plane = new DisplayPlane(
                UUID.randomUUID(), "world", new Vector3d(0, 10, 0), 2, 2,
                new Vector3d(1, 0, 0), new Vector3d(0, -1, 0), 32.0, 16.0
        );

        QuarantinedIdAllocator allocator = new QuarantinedIdAllocator();
        List<PacketWrapper<?>> captured = new ArrayList<>();

        VirtualEntityRig rig = new VirtualEntityRig(mockPlayer, plane, allocator, captured::add);
        rig.spawn();

        captured.clear();
        rig.despawn();

        boolean hasDestroy = captured.stream().anyMatch(p -> p instanceof WrapperPlayServerDestroyEntities);
        assertTrue(hasDestroy, "Despawn must send WrapperPlayServerDestroyEntities packet");
        assertFalse(rig.isSpawned(), "Rig must report spawned=false after despawn");
    }
}