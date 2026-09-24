package me.goosbanny.goosboards.display;

import me.goosbanny.goosboards.raycast.Vector3d;
import me.goosbanny.goosboards.raycast.spatial.DisplaySpatialIndex;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProximityTrackerTest {

    private DisplaySpatialIndex mockSpatialIndex;
    private ProximityTracker proximityTracker;
    private Player mockPlayer;
    private World mockWorld;
    private UUID playerUuid;
    private UUID worldUuid;

    @BeforeEach
    void setUp() {
        mockSpatialIndex = mock(DisplaySpatialIndex.class);
        proximityTracker = new ProximityTracker(mockSpatialIndex, 32.0);

        playerUuid = UUID.randomUUID();
        worldUuid = UUID.randomUUID();

        mockWorld = mock(World.class);
        when(mockWorld.getUID()).thenReturn(worldUuid);
        when(mockWorld.getName()).thenReturn("world");

        mockPlayer = mock(Player.class);
        when(mockPlayer.getUniqueId()).thenReturn(playerUuid);
        when(mockPlayer.isOnline()).thenReturn(true);
        when(mockPlayer.getWorld()).thenReturn(mockWorld);
    }

    @Test
    @DisplayName("Sub-block movement skips spatial index query due to integer block caching")
    void testSubBlockMovementCached() {
        Location loc1 = new Location(mockWorld, 10.2, 64.0, 20.1);
        when(mockPlayer.getLocation()).thenReturn(loc1);

        DisplayPlane mockPlane = new DisplayPlane(
                UUID.randomUUID(), "world",
                new Vector3d(10, 64, 20),
                2, 2
        );
        when(mockSpatialIndex.nearbyBoards(any(), eq(32.0), eq("world"))).thenReturn(List.of(mockPlane));

        // First update at (10.2, 64.0, 20.1) -> block (10, 64, 20)
        proximityTracker.updatePlayerProximity(mockPlayer);
        verify(mockSpatialIndex, times(1)).nearbyBoards(any(), eq(32.0), eq("world"));
        assertTrue(proximityTracker.isNearAnyBoard(playerUuid));
        assertEquals(1, proximityTracker.getNearbyBoards(playerUuid).size());

        // Sub-block movement to (10.8, 64.0, 20.7) -> still block (10, 64, 20)
        Location loc2 = new Location(mockWorld, 10.8, 64.0, 20.7);
        when(mockPlayer.getLocation()).thenReturn(loc2);

        proximityTracker.updatePlayerProximity(mockPlayer);

        // Crucial check: spatial index must NOT be queried again!
        verify(mockSpatialIndex, times(1)).nearbyBoards(any(), eq(32.0), eq("world"));
    }

    @Test
    @DisplayName("Cross-block movement triggers spatial index recalculation")
    void testCrossBlockMovementRecalculates() {
        Location loc1 = new Location(mockWorld, 10.2, 64.0, 20.1);
        when(mockPlayer.getLocation()).thenReturn(loc1);
        when(mockSpatialIndex.nearbyBoards(any(), eq(32.0), eq("world"))).thenReturn(List.of());

        proximityTracker.updatePlayerProximity(mockPlayer);
        verify(mockSpatialIndex, times(1)).nearbyBoards(any(), eq(32.0), eq("world"));
        assertFalse(proximityTracker.isNearAnyBoard(playerUuid));

        // Cross-block movement to (15.2, 64.0, 20.1) -> block (15, 64, 20)
        Location loc2 = new Location(mockWorld, 15.2, 64.0, 20.1);
        when(mockPlayer.getLocation()).thenReturn(loc2);

        proximityTracker.updatePlayerProximity(mockPlayer);
        verify(mockSpatialIndex, times(2)).nearbyBoards(any(), eq(32.0), eq("world"));
    }

    @Test
    @DisplayName("Player quit and clear flush all internal position and board caches")
    void testQuitAndClear() {
        Location loc = new Location(mockWorld, 5.0, 64.0, 5.0);
        when(mockPlayer.getLocation()).thenReturn(loc);
        DisplayPlane testPlane = new DisplayPlane(UUID.randomUUID(), "world", new Vector3d(0, 64, 0), 1, 1);
        when(mockSpatialIndex.nearbyBoards(any(), eq(32.0), eq("world"))).thenReturn(List.of(testPlane));

        proximityTracker.updatePlayerProximity(mockPlayer);
        assertTrue(proximityTracker.isNearAnyBoard(playerUuid));

        proximityTracker.onPlayerQuit(playerUuid);
        assertFalse(proximityTracker.isNearAnyBoard(playerUuid));
        assertTrue(proximityTracker.getNearbyBoards(playerUuid).isEmpty());

        proximityTracker.updatePlayerProximity(mockPlayer);
        assertTrue(proximityTracker.isNearAnyBoard(playerUuid));

        proximityTracker.clear();
        assertFalse(proximityTracker.isNearAnyBoard(playerUuid));
    }
}
