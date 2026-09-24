package me.goosbanny.goosboards.display;

import org.bukkit.Location;
import org.bukkit.World;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DisplayPosTest {

    @Test
    @DisplayName("DisplayPos correctly stores primitive coordinates and world UUID")
    void testPrimitiveStorage() {
        UUID worldId = UUID.randomUUID();
        DisplayPos pos = new DisplayPos(worldId, 10, 64, -20);

        assertEquals(worldId, pos.worldId());
        assertEquals(10, pos.x());
        assertEquals(64, pos.y());
        assertEquals(-20, pos.z());
    }

    @Test
    @DisplayName("DisplayPos.from(Location) extracts primitive block coordinates and world UID without retaining World")
    void testFromLocation() {
        UUID worldId = UUID.randomUUID();
        World mockWorld = mock(World.class);
        when(mockWorld.getUID()).thenReturn(worldId);

        Location loc = new Location(mockWorld, 10.8, 64.2, -20.9);
        DisplayPos pos = DisplayPos.from(loc);

        assertNotNull(pos);
        assertEquals(worldId, pos.worldId());
        assertEquals(10, pos.x());
        assertEquals(64, pos.y());
        assertEquals(-21, pos.z()); // Math.floor(-20.9) = -21 for getBlockZ

        assertTrue(pos.matchesWorld(mockWorld));
        assertTrue(pos.matchesWorld(worldId));

        UUID otherWorldId = UUID.randomUUID();
        assertFalse(pos.matchesWorld(otherWorldId));
    }

    @Test
    @DisplayName("DisplayPos value equality and hash code adhere to record semantics")
    void testEqualityAndHashCode() {
        UUID worldId = UUID.randomUUID();
        DisplayPos p1 = new DisplayPos(worldId, 1, 2, 3);
        DisplayPos p2 = new DisplayPos(worldId, 1, 2, 3);
        DisplayPos p3 = new DisplayPos(worldId, 1, 2, 4);

        assertEquals(p1, p2);
        assertEquals(p1.hashCode(), p2.hashCode());
        assertNotEquals(p1, p3);
    }
}
