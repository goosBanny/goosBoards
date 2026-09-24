package me.goosbanny.goosboards.raycast;

import me.goosbanny.goosboards.display.DisplayPlane;

import com.sun.management.ThreadMXBean;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.management.ManagementFactory;
import java.util.UUID;

import me.goosbanny.goosboards.display.DistanceLodTracker;

import static org.junit.jupiter.api.Assertions.*;

class ZeroAllocRaycastTest {

    @Test
    @DisplayName("DisplayRaycaster.intersect operates on primitive scalars with zero heap object allocations")
    void testZeroAllocationPrimitiveRaycastLoop() {
        DisplayPlane plane = new DisplayPlane(
                UUID.randomUUID(),
                "world",
                new Vector3d(0, 64, 0),
                4, 3, // 4x3 blocks = 512x384 pixels
                new Vector3d(1, 0, 0),
                new Vector3d(0, -1, 0),
                32.0, 16.0
        );

        MutableRaycastHit hit = new MutableRaycastHit();

        // Eye looking at the center of the board
        double eyeX = 2.0;
        double eyeY = 62.5;
        double eyeZ = 5.0;
        double dirX = 0.0;
        double dirY = 0.0;
        double dirZ = -1.0;

        // Functional verification
        DisplayRaycaster.intersect(plane, eyeX, eyeY, eyeZ, dirX, dirY, dirZ, hit);
        assertTrue(hit.hit);
        assertEquals(5.0, hit.distance, 1e-6);
        assertEquals(256, (int) Math.round(hit.pixelX));
        assertEquals(192, (int) Math.round(hit.pixelY));

        // JVM Allocation Profiling
        ThreadMXBean threadBean = (ThreadMXBean) ManagementFactory.getThreadMXBean();
        boolean canMeasureAlloc = threadBean.isThreadAllocatedMemorySupported()
                && threadBean.isThreadAllocatedMemoryEnabled();

        // Warmup JIT to compile and inline scalar primitive math
        for (int i = 0; i < 100_000; i++) {
            DisplayRaycaster.intersect(plane, eyeX, eyeY, eyeZ, dirX, dirY, dirZ, hit);
        }

        if (canMeasureAlloc) {
            long threadId = Thread.currentThread().threadId();
            long allocBefore = threadBean.getThreadAllocatedBytes(threadId);

            // 100,000 hot loop raycasts
            for (int i = 0; i < 100_000; i++) {
                DisplayRaycaster.intersect(plane, eyeX, eyeY, eyeZ, dirX, dirY, dirZ, hit);
            }

            long allocAfter = threadBean.getThreadAllocatedBytes(threadId);
            long totalAlloc = allocAfter - allocBefore;

            // Across 100,000 raycasts, total allocation must be strictly 0 bytes
            assertEquals(0L, totalAlloc, "Primitive scalar raycast hot loop must allocate exactly 0 bytes on the heap");
        }
    }

    @Test
    @DisplayName("DistanceLodTracker scalar methods operate with zero heap object allocations")
    void testZeroAllocationScalarDistanceAndBackface() {
        DisplayPlane plane = new DisplayPlane(
                UUID.randomUUID(),
                "world",
                new Vector3d(0, 64, 0),
                4, 3,
                new Vector3d(1, 0, 0),
                new Vector3d(0, -1, 0),
                32.0, 16.0
        );

        double eyeX = 2.0;
        double eyeY = 62.5;
        double eyeZ = 5.0; // In front of the board (+Z normal)

        assertFalse(DistanceLodTracker.isViewingBackFace(eyeX, eyeY, eyeZ, plane));
        assertTrue(DistanceLodTracker.isViewingBackFace(eyeX, eyeY, -5.0, plane));

        ThreadMXBean threadBean = (ThreadMXBean) ManagementFactory.getThreadMXBean();
        boolean canMeasureAlloc = threadBean.isThreadAllocatedMemorySupported()
                && threadBean.isThreadAllocatedMemoryEnabled();

        boolean bhBool = false;
        double bhDist = 0.0;

        // Warm up JIT to ensure Tier 4 / C2 compilation completes before measurement
        for (int i = 0; i < 200_000; i++) {
            bhBool ^= DistanceLodTracker.isViewingBackFace(eyeX, eyeY, eyeZ, plane);
            bhDist += DistanceLodTracker.computeDistance(eyeX, eyeY, eyeZ, plane);
        }

        if (canMeasureAlloc) {
            long threadId = Thread.currentThread().threadId();
            threadBean.getThreadAllocatedBytes(threadId); // Prime ThreadMXBean call

            for (int i = 0; i < 100_000; i++) {
                bhBool ^= DistanceLodTracker.isViewingBackFace(eyeX, eyeY, eyeZ, plane);
                bhDist += DistanceLodTracker.computeDistance(eyeX, eyeY, eyeZ, plane);
            }

            long allocBefore = threadBean.getThreadAllocatedBytes(threadId);

            for (int i = 0; i < 100_000; i++) {
                bhBool ^= DistanceLodTracker.isViewingBackFace(eyeX, eyeY, eyeZ, plane);
                bhDist += DistanceLodTracker.computeDistance(eyeX, eyeY, eyeZ, plane);
            }

            long allocAfter = threadBean.getThreadAllocatedBytes(threadId);
            long totalAlloc = allocAfter - allocBefore;

            // Prevent dead code elimination
            if (bhDist < 0.0 && bhBool) {
                System.out.print("");
            }

            assertEquals(0L, totalAlloc, "Scalar back-face culling and distance checks must allocate strictly 0 bytes");
        }
    }
}