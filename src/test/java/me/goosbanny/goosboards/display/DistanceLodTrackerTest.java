package me.goosbanny.goosboards.display;

import me.goosbanny.goosboards.display.DisplayPlane;
import me.goosbanny.goosboards.raycast.Vector3d;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class DistanceLodTrackerTest {

    @Test
    @DisplayName("Distance <= 6 blocks renders at full 20 FPS (every tick)")
    void testNearDistance20Fps() {
        double dist = 4.5;
        for (long tick = 0; tick < 20; tick++) {
            assertTrue(DistanceLodTracker.shouldRender(dist, false, tick),
                    "Tick " + tick + " should render at near distance (20 FPS)");
        }
    }

    @Test
    @DisplayName("Distance 6 < d <= 16 blocks renders at throttled 5 FPS (every 4 ticks)")
    void testMediumDistance5Fps() {
        double dist = 10.0;
        assertTrue(DistanceLodTracker.shouldRender(dist, false, 0L));
        assertFalse(DistanceLodTracker.shouldRender(dist, false, 1L));
        assertFalse(DistanceLodTracker.shouldRender(dist, false, 2L));
        assertFalse(DistanceLodTracker.shouldRender(dist, false, 3L));
        assertTrue(DistanceLodTracker.shouldRender(dist, false, 4L));
        assertFalse(DistanceLodTracker.shouldRender(dist, false, 5L));
        assertTrue(DistanceLodTracker.shouldRender(dist, false, 8L));
    }

    @Test
    @DisplayName("Distance > 16 blocks renders at throttled 1 FPS (every 20 ticks)")
    void testFarDistance1Fps() {
        double dist = 25.0;
        assertTrue(DistanceLodTracker.shouldRender(dist, false, 0L));
        for (long tick = 1; tick < 20; tick++) {
            assertFalse(DistanceLodTracker.shouldRender(dist, false, tick),
                    "Tick " + tick + " should be throttled at far distance");
        }
        assertTrue(DistanceLodTracker.shouldRender(dist, false, 20L));
    }

    @Test
    @DisplayName("Occluded viewers behind the board plane render at 0 FPS (completely skipped)")
    void testOcclusionCullsAllPackets() {
        for (long tick = 0; tick < 40; tick++) {
            assertFalse(DistanceLodTracker.shouldRender(2.0, true, tick),
                    "Occluded viewer must never receive packets regardless of distance");
        }
    }

    @Test
    @DisplayName("isOccluded correctly detects viewer position relative to board normal")
    void testOcclusionVectorMath() {
        // Display plane at (0, 64, 0) facing South (+Z normal)
        DisplayPlane plane = new DisplayPlane(
                UUID.randomUUID(),
                "world",
                new Vector3d(0, 64, 0),
                2, 2
        );

        // Player standing in front of board at Z = -5, looking towards center at Z = 0
        // D = center - eye = (0, 0, 0) - (0, 64, -5) = (0, 0, 5)
        // With normal pointing South/North:
        Vector3d frontPlayer = new Vector3d(0, 64, -10);
        Vector3d behindPlayer = new Vector3d(0, 64, 10);

        boolean occludedFront = DistanceLodTracker.isViewingBackFace(frontPlayer, plane);
        boolean occludedBehind = DistanceLodTracker.isViewingBackFace(behindPlayer, plane);

        // One is in front (not back-face), one is behind (viewing back face)
        assertNotEquals(occludedFront, occludedBehind);

        // Deprecated isOccluded delegate returns identical results
        assertEquals(occludedFront, DistanceLodTracker.isOccluded(frontPlayer, plane));
        assertEquals(occludedBehind, DistanceLodTracker.isOccluded(behindPlayer, plane));
    }
}