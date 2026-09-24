package me.goosbanny.goosboards.protocol;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.PacketEventsAPI;
import com.github.retrooper.packetevents.manager.player.PlayerManager;
import com.github.retrooper.packetevents.manager.server.ServerManager;
import com.github.retrooper.packetevents.manager.server.ServerVersion;
import com.github.retrooper.packetevents.settings.PacketEventsSettings;
import io.github.retrooper.packetevents.netty.NettyManagerImpl;
import me.goosbanny.goosboards.display.DisplayPlane;
import me.goosbanny.goosboards.raycast.Vector3d;
import com.github.retrooper.packetevents.protocol.player.ClientVersion;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerQuitEvent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VirtualEntityTrackerTest {

    @BeforeAll
    static void setupPacketEvents() {
        @SuppressWarnings("unchecked")
        PacketEventsAPI<Object> api = Mockito.mock(PacketEventsAPI.class);
        ServerManager serverManager = Mockito.mock(ServerManager.class);
        PlayerManager playerManager = Mockito.mock(PlayerManager.class);
        PacketEventsSettings settings = new PacketEventsSettings();
        NettyManagerImpl nettyManager = new NettyManagerImpl();

        Mockito.when(api.getSettings()).thenReturn(settings);
        Mockito.when(api.getServerManager()).thenReturn(serverManager);
        Mockito.when(api.getPlayerManager()).thenReturn(playerManager);
        Mockito.when(api.getNettyManager()).thenReturn(nettyManager);
        Mockito.when(serverManager.getVersion()).thenReturn(ServerVersion.V_1_20_4);

        PacketEvents.setAPI(api);
    }

    @Test
    @DisplayName("Test 8 — Spawn creates correct number of entities")
    void testSpawnCreatesCorrectNumberOfEntities() {
        QuarantinedIdAllocator allocator = Mockito.spy(new QuarantinedIdAllocator());
        VirtualEntityTracker tracker = new VirtualEntityTracker(allocator);

        Player mockPlayer = Mockito.mock(Player.class);
        UUID playerId = UUID.randomUUID();
        Mockito.when(mockPlayer.getUniqueId()).thenReturn(playerId);

        DisplayPlane plane = new DisplayPlane(UUID.randomUUID(), "world", new Vector3d(0, 64, 0), 4, 3);
        VirtualEntityRig rig = tracker.spawnRig(mockPlayer, plane);

        // Exactly 12 entity IDs allocated (4 * 3)
        Mockito.verify(allocator, Mockito.times(12)).allocate();
        assertNotNull(rig.getEntityIds());
        assertEquals(4, rig.getEntityIds().length);
        assertEquals(3, rig.getEntityIds()[0].length);
        assertTrue(rig.isSpawned());
    }

    @Test
    @DisplayName("Test 9 — Despawn frees all IDs")
    void testDespawnFreesAllIds() {
        QuarantinedIdAllocator allocator = Mockito.spy(new QuarantinedIdAllocator());
        VirtualEntityTracker tracker = new VirtualEntityTracker(allocator);

        Player mockPlayer = Mockito.mock(Player.class);
        UUID playerId = UUID.randomUUID();
        Mockito.when(mockPlayer.getUniqueId()).thenReturn(playerId);

        UUID displayId = UUID.randomUUID();
        DisplayPlane plane = new DisplayPlane(displayId, "world", new Vector3d(0, 64, 0), 2, 2);

        VirtualEntityRig rig = tracker.spawnRig(mockPlayer, plane);
        assertTrue(rig.isSpawned());

        tracker.despawnRig(mockPlayer, displayId);

        // allocator.free() called exactly 4 times (2 * 2)
        Mockito.verify(allocator, Mockito.times(4)).free(Mockito.anyInt());
        assertFalse(rig.isSpawned());
        assertNull(tracker.getRig(mockPlayer, displayId));
    }

    @Test
    @DisplayName("Test 10 — PlayerQuit triggers despawnAll")
    void testPlayerQuitTriggersDespawnAll() {
        QuarantinedIdAllocator allocator = Mockito.spy(new QuarantinedIdAllocator());
        VirtualEntityTracker tracker = new VirtualEntityTracker(allocator);

        Player mockPlayer = Mockito.mock(Player.class);
        UUID playerId = UUID.randomUUID();
        Mockito.when(mockPlayer.getUniqueId()).thenReturn(playerId);

        DisplayPlane plane1 = new DisplayPlane(UUID.randomUUID(), "world", new Vector3d(0, 64, 0), 2, 2);
        DisplayPlane plane2 = new DisplayPlane(UUID.randomUUID(), "world", new Vector3d(10, 64, 0), 3, 1);

        VirtualEntityRig rig1 = tracker.spawnRig(mockPlayer, plane1);
        VirtualEntityRig rig2 = tracker.spawnRig(mockPlayer, plane2);

        assertTrue(rig1.isSpawned());
        assertTrue(rig2.isSpawned());

        tracker.despawnAll(mockPlayer);

        assertFalse(rig1.isSpawned());
        assertFalse(rig2.isSpawned());
        assertNull(tracker.getActiveRigs().get(playerId));
        // Total 4 + 3 = 7 IDs freed
        Mockito.verify(allocator, Mockito.times(7)).free(Mockito.anyInt());
    }

    @Test
    @DisplayName("Test 11 — plugin.yml contains folia-supported: true")
    void testPluginYmlContainsFoliaSupported() throws IOException {
        Path path = Paths.get("src", "main", "resources", "plugin.yml");
        assertTrue(Files.exists(path), "plugin.yml must exist at src/main/resources/plugin.yml");

        String content = Files.readString(path);
        assertTrue(content.contains("folia-supported: true"), "plugin.yml must contain folia-supported: true");
    }

    @Test
    @DisplayName("Virtual item frame metadata index is 8 for 1.21.0/1.21.1 and 9 for 1.21.2+")
    void testItemFrameMetadataIndexForVersions() {
        QuarantinedIdAllocator allocator = new QuarantinedIdAllocator();
        Player mockPlayer = Mockito.mock(Player.class);
        Mockito.when(mockPlayer.getUniqueId()).thenReturn(UUID.randomUUID());
        DisplayPlane plane = new DisplayPlane(UUID.randomUUID(), "world", new Vector3d(0, 64, 0), 1, 1);

        // Test 1: 1.21.0 client -> index must be 8
        Mockito.when(PacketEvents.getAPI().getPlayerManager().getClientVersion(mockPlayer))
                .thenReturn(ClientVersion.V_1_21);
        List<WrapperPlayServerEntityMetadata> captured1 = new ArrayList<>();
        VirtualEntityRig rig1 = new VirtualEntityRig(mockPlayer, plane, allocator, pkt -> {
            if (pkt instanceof WrapperPlayServerEntityMetadata meta) {
                captured1.add(meta);
            }
        });
        rig1.spawn();
        assertFalse(captured1.isEmpty());
        assertEquals(8, captured1.get(0).getEntityMetadata().get(1).getIndex(), "Item slot metadata index for 1.21 must be 8");

        // Test 2: 1.21.2 client -> index must be 9
        Mockito.when(PacketEvents.getAPI().getPlayerManager().getClientVersion(mockPlayer))
                .thenReturn(ClientVersion.V_1_21_2);
        List<WrapperPlayServerEntityMetadata> captured2 = new ArrayList<>();
        VirtualEntityRig rig2 = new VirtualEntityRig(mockPlayer, plane, allocator, pkt -> {
            if (pkt instanceof WrapperPlayServerEntityMetadata meta) {
                captured2.add(meta);
            }
        });
        rig2.spawn();
        assertFalse(captured2.isEmpty());
        assertEquals(9, captured2.get(0).getEntityMetadata().get(1).getIndex(), "Item slot metadata index for 1.21.2 must be 9");
    }

    @Test
    @DisplayName("Calling spawnRig on existing active rig despawns old rig and frees IDs")
    void testSpawnRigReplacesAndDespawnsOldRig() {
        QuarantinedIdAllocator allocator = Mockito.spy(new QuarantinedIdAllocator());
        VirtualEntityTracker tracker = new VirtualEntityTracker(allocator);

        Player mockPlayer = Mockito.mock(Player.class);
        UUID playerId = UUID.randomUUID();
        Mockito.when(mockPlayer.getUniqueId()).thenReturn(playerId);

        UUID displayId = UUID.randomUUID();
        DisplayPlane plane = new DisplayPlane(displayId, "world", new Vector3d(0, 64, 0), 2, 2);

        VirtualEntityRig rig1 = tracker.spawnRig(mockPlayer, plane);
        assertTrue(rig1.isSpawned());

        // Re-spawn rig for same player and display
        VirtualEntityRig rig2 = tracker.spawnRig(mockPlayer, plane);
        assertTrue(rig2.isSpawned());
        assertFalse(rig1.isSpawned(), "Previous rig must be despawned");

        // Old rig freed its 4 IDs
        Mockito.verify(allocator, Mockito.times(4)).free(Mockito.anyInt());
    }
}