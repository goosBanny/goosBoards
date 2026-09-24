package me.goosbanny.goosboards.protocol;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.wrapper.PacketWrapper;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerDestroyEntities;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnEntity;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TextDisplayEntityTrackerTest {

    @BeforeAll
    static void setup() {
        if (PacketEvents.getAPI() == null) {
            VirtualEntityTrackerTest.setupPacketEvents();
        }
    }

    @Test
    @DisplayName("Spawning text display allocates ID and sends spawn packet")
    void testSpawnTextDisplay() {
        QuarantinedIdAllocator allocator = new QuarantinedIdAllocator();
        List<PacketWrapper<?>> sentPackets = new ArrayList<>();
        TextDisplayEntityTracker tracker = new TextDisplayEntityTracker(allocator, sentPackets::add);

        Player mockPlayer = mock(Player.class);
        UUID playerId = UUID.randomUUID();
        when(mockPlayer.getUniqueId()).thenReturn(playerId);
        when(mockPlayer.isOnline()).thenReturn(true);

        UUID displayId = UUID.randomUUID();
        Vector3d pos = new Vector3d(10.0, 64.0, -20.0);

        int id = tracker.spawnTextDisplay(mockPlayer, displayId, pos);

        assertTrue(id >= 1_000_000_000);
        assertTrue(tracker.hasActiveDisplays(playerId));
        assertEquals(1, sentPackets.size());
        assertInstanceOf(WrapperPlayServerSpawnEntity.class, sentPackets.get(0));

        WrapperPlayServerSpawnEntity spawn = (WrapperPlayServerSpawnEntity) sentPackets.get(0);
        assertEquals(id, spawn.getEntityId());
    }

    @Test
    @DisplayName("Despawning text displays sends destroy packet and frees ID")
    void testDespawnTextDisplay() {
        QuarantinedIdAllocator allocator = new QuarantinedIdAllocator();
        List<PacketWrapper<?>> sentPackets = new ArrayList<>();
        TextDisplayEntityTracker tracker = new TextDisplayEntityTracker(allocator, sentPackets::add);

        Player mockPlayer = mock(Player.class);
        UUID playerId = UUID.randomUUID();
        when(mockPlayer.getUniqueId()).thenReturn(playerId);

        UUID displayId = UUID.randomUUID();
        int id = tracker.spawnTextDisplay(mockPlayer, displayId, new Vector3d(0, 0, 0));

        tracker.despawnTextDisplays(mockPlayer, displayId);

        assertFalse(tracker.hasActiveDisplays(playerId));
        assertEquals(2, sentPackets.size());
        assertInstanceOf(WrapperPlayServerDestroyEntities.class, sentPackets.get(1));

        WrapperPlayServerDestroyEntities destroy = (WrapperPlayServerDestroyEntities) sentPackets.get(1);
        assertArrayEquals(new int[]{id}, destroy.getEntityIds());
    }

    @Test
    @DisplayName("Player quit cleans up active text displays")
    void testPlayerQuitCleanup() {
        QuarantinedIdAllocator allocator = new QuarantinedIdAllocator();
        List<PacketWrapper<?>> sentPackets = new ArrayList<>();
        TextDisplayEntityTracker tracker = new TextDisplayEntityTracker(allocator, sentPackets::add);

        Player mockPlayer = mock(Player.class);
        UUID playerId = UUID.randomUUID();
        when(mockPlayer.getUniqueId()).thenReturn(playerId);

        tracker.spawnTextDisplay(mockPlayer, UUID.randomUUID(), new Vector3d(0, 0, 0));
        assertTrue(tracker.hasActiveDisplays(playerId));

        tracker.despawnAll(mockPlayer);

        assertFalse(tracker.hasActiveDisplays(playerId));
    }
}
