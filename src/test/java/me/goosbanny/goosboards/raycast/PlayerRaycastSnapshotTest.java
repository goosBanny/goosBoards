package me.goosbanny.goosboards.raycast;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PlayerRaycastSnapshotTest {

    @Test
    @DisplayName("Safeguard 4: PlayerRaycastSnapshot synchronously extracts spatial state and look direction")
    void testSnapshotExtraction() {
        Player mockPlayer = mock(Player.class);
        World mockWorld = mock(World.class);
        UUID worldId = UUID.randomUUID();
        UUID playerId = UUID.randomUUID();

        when(mockWorld.getUID()).thenReturn(worldId);
        when(mockWorld.getName()).thenReturn("world_nether");
        when(mockPlayer.getUniqueId()).thenReturn(playerId);
        when(mockPlayer.isSneaking()).thenReturn(true);

        // Player facing South (yaw 0, pitch 0 -> look vector: dx=0, dy=0, dz=1)
        Location eyeLoc = new Location(mockWorld, 100.5, 65.0, -50.5, 0.0f, 0.0f);
        when(mockPlayer.getEyeLocation()).thenReturn(eyeLoc);

        PlayerRaycastSnapshot snapshot = PlayerRaycastSnapshot.from(mockPlayer);

        assertNotNull(snapshot);
        assertEquals(playerId, snapshot.playerId());
        assertEquals(worldId, snapshot.worldId());
        assertEquals("world_nether", snapshot.worldName());
        assertTrue(snapshot.isSneaking());

        // Coordinate checks
        assertEquals(100.5, snapshot.eyePosition().x(), 1e-6);
        assertEquals(65.0, snapshot.eyePosition().y(), 1e-6);
        assertEquals(-50.5, snapshot.eyePosition().z(), 1e-6);

        // Vector direction checks for yaw 0, pitch 0: dx=0, dy=0, dz=1
        assertEquals(0.0, snapshot.lookDirection().x(), 1e-4);
        assertEquals(0.0, snapshot.lookDirection().y(), 1e-4);
        assertEquals(1.0, snapshot.lookDirection().z(), 1e-4);
    }
}
