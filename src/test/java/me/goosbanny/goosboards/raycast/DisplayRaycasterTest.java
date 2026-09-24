package me.goosbanny.goosboards.raycast;

import me.goosbanny.goosboards.display.DisplayPlane;
import me.goosbanny.goosboards.raycast.spatial.impl.ChunkBucketSpatialIndex;

import me.goosbanny.goosboards.protocol.packet.ClickPacketListener;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DisplayRaycasterTest {

    private DisplayPlane createPlane(
            Vector3d topLeft,
            double widthBlocks,
            double heightBlocks,
            double interactionRadius
    ) {
        return new DisplayPlane(
                UUID.randomUUID(),
                "world",
                topLeft,
                widthBlocks,
                heightBlocks,
                new Vector3d(1, 0, 0),   // right = +X
                new Vector3d(0, -1, 0),  // down = -Y
                32.0,
                interactionRadius
        );
    }

    @Test
    @DisplayName("Test 2.1 — Parallel ray (denom < 1e-6)")
    void testParallelRay() {
        // Horizontal plane lying on XZ plane with normal along Y
        DisplayPlane plane = new DisplayPlane(
                UUID.randomUUID(),
                "world",
                new Vector3d(0, 0, 0),
                4.0,
                3.0,
                new Vector3d(1, 0, 0),
                new Vector3d(0, 0, 1),
                32.0,
                16.0
        );
        // Look direction along X (parallel to plane surface)
        Vector3d eye = new Vector3d(0, 5, 0);
        Vector3d lookDir = new Vector3d(1, 0, 0);

        RaycastResult result = DisplayRaycaster.cast(eye, lookDir, plane);
        assertInstanceOf(RaycastResult.Miss.class, result);
    }

    @Test
    @DisplayName("Test 2.2 — Direct center hit")
    void testDirectCenterHit() {
        // 4x3-block plane with top-left at (0, 3, 0), facing south (+Z)
        DisplayPlane plane = createPlane(new Vector3d(0, 3, 0), 4.0, 3.0, 16.0);
        // Eye at (2.0, 1.5, 5.0) looking directly at center (2.0, 1.5, 0.0) from front
        Vector3d eye = new Vector3d(2.0, 1.5, 5.0);
        Vector3d lookDir = new Vector3d(0, 0, -1);

        RaycastResult result = DisplayRaycaster.cast(eye, lookDir, plane);
        assertInstanceOf(RaycastResult.Hit.class, result);

        RaycastResult.Hit hit = (RaycastResult.Hit) result;
        assertEquals(plane.id(), hit.displayId());
        assertEquals(256, hit.pixelX(), "pixelX should be center of 512 (4 * 128)");
        assertEquals(192, hit.pixelY(), "pixelY should be center of 384 (3 * 128)");
        assertEquals(5.0, hit.t(), 1e-4);
    }

    @Test
    @DisplayName("Test 2.3 — Hit at exact u=1.0, v=1.0 corner (clamp verification)")
    void testHitAtExactU1V1Corner() {
        // 1x1 plane at (0, 1, 0), right = (1, 0, 0), down = (0, -1, 0)
        DisplayPlane plane = createPlane(new Vector3d(0, 1, 0), 1.0, 1.0, 16.0);
        // Corner u=1.0, v=1.0 is at (1.0, 0.0, 0.0)
        Vector3d eye = new Vector3d(1.0, 0.0, 5.0);
        Vector3d lookDir = new Vector3d(0, 0, -1);

        RaycastResult result = DisplayRaycaster.cast(eye, lookDir, plane);
        assertInstanceOf(RaycastResult.Hit.class, result);

        RaycastResult.Hit hit = (RaycastResult.Hit) result;
        // Must clamp to 127, NOT overflow to 128
        assertEquals(127, hit.pixelX(), "pixelX must be clamped to 127");
        assertEquals(127, hit.pixelY(), "pixelY must be clamped to 127");
    }

    @Test
    @DisplayName("Test 2.4 — Hit at u=0.0, v=0.0 corner")
    void testHitAtExactU0V0Corner() {
        DisplayPlane plane = createPlane(new Vector3d(0, 1, 0), 1.0, 1.0, 16.0);
        // Top-left corner u=0.0, v=0.0 is at (0.0, 1.0, 0.0)
        Vector3d eye = new Vector3d(0.0, 1.0, 5.0);
        Vector3d lookDir = new Vector3d(0, 0, -1);

        RaycastResult result = DisplayRaycaster.cast(eye, lookDir, plane);
        assertInstanceOf(RaycastResult.Hit.class, result);

        RaycastResult.Hit hit = (RaycastResult.Hit) result;
        assertEquals(0, hit.pixelX());
        assertEquals(0, hit.pixelY());
    }

    @Test
    @DisplayName("Test 2.5 — Out-of-bounds UV (u = 1.1)")
    void testOutOfBoundsUV() {
        DisplayPlane plane = createPlane(new Vector3d(0, 1, 0), 1.0, 1.0, 16.0);
        // Point with u = 1.1: x = 1.1
        Vector3d eye = new Vector3d(1.1, 0.5, 5.0);
        Vector3d lookDir = new Vector3d(0, 0, -1);

        RaycastResult result = DisplayRaycaster.cast(eye, lookDir, plane);
        assertInstanceOf(RaycastResult.Miss.class, result);
    }

    @Test
    @DisplayName("Test 2.6 — Ray behind plane (back-face culled)")
    void testRayBehindPlane() {
        DisplayPlane plane = createPlane(new Vector3d(0, 3, 0), 4.0, 3.0, 16.0);
        // Eye behind the plane (z = -5.0) looking towards the board (+Z)
        Vector3d eye = new Vector3d(2.0, 1.5, -5.0);
        Vector3d lookDir = new Vector3d(0, 0, 1);

        RaycastResult result = DisplayRaycaster.cast(eye, lookDir, plane);
        assertInstanceOf(RaycastResult.Miss.class, result);
    }

    @Test
    @DisplayName("Test 2.7 — Beyond interactionRadius (t > radius)")
    void testBeyondInteractionRadius() {
        DisplayPlane plane = createPlane(new Vector3d(0, 3, 0), 4.0, 3.0, 16.0);
        // Eye 50 blocks away, interactionRadius = 16
        Vector3d eye = new Vector3d(2.0, 1.5, 50.0);
        Vector3d lookDir = new Vector3d(0, 0, -1);

        RaycastResult result = DisplayRaycaster.cast(eye, lookDir, plane);
        assertInstanceOf(RaycastResult.Miss.class, result);
    }

    @Test
    @DisplayName("Test 2.8 — Grazing angle (denom = -5e-7)")
    void testGrazingAngle() {
        DisplayPlane plane = createPlane(new Vector3d(0, 3, 0), 4.0, 3.0, 16.0);
        Vector3d eye = new Vector3d(2.0, 1.5, 5.0);
        Vector3d lookDir = new Vector3d(1.0, 0.0, -5e-7);

        RaycastResult result = DisplayRaycaster.cast(eye, lookDir, plane);
        assertInstanceOf(RaycastResult.Miss.class, result);
    }

    @Test
    @DisplayName("Test 2.9 — Spatial index gate: player far from all boards")
    void testSpatialIndexGateFarFromAllBoards() {
        ChunkBucketSpatialIndex index = new ChunkBucketSpatialIndex();
        for (int i = 0; i < 5; i++) {
            DisplayPlane plane = createPlane(new Vector3d(i * 2.0, 64, 0), 2.0, 2.0, 16.0);
            index.register(plane);
        }

        // Player 200 blocks away from all boards
        Vector3d playerPos = new Vector3d(200.0, 64.0, 200.0);
        List<DisplayPlane> nearby = index.nearbyBoards(playerPos, 50.0, "world");
        assertTrue(nearby.isEmpty(), "Player 200 blocks away must return empty list from spatial index");
    }

    @Test
    @DisplayName("Test 2.10 — Spatial index returns correct nearby boards")
    void testSpatialIndexReturnsCorrectNearbyBoards() {
        ChunkBucketSpatialIndex index = new ChunkBucketSpatialIndex();
        DisplayPlane planeA = createPlane(new Vector3d(0, 0, 0), 2.0, 2.0, 16.0);
        DisplayPlane planeB = createPlane(new Vector3d(1000, 0, 0), 2.0, 2.0, 16.0);

        index.register(planeA);
        index.register(planeB);

        // Player at (5, 0, 0), radius = 32
        Vector3d playerPos = new Vector3d(5, 0, 0);
        List<DisplayPlane> nearby = index.nearbyBoards(playerPos, 32.0, "world");

        assertEquals(1, nearby.size());
        assertEquals(planeA.id(), nearby.get(0).id());
    }

    @Test
    @DisplayName("Test 2.11 — Simulated 15-block click resolves correctly")
    void testSimulated15BlockClickResolves() {
        // Eye at (0, 0, 15), look direction (0, 0, -1)
        // Plane at (0, 0, 0) facing south, interactionRadius = 20
        DisplayPlane plane = createPlane(new Vector3d(-1, 1, 0), 2.0, 2.0, 20.0);
        Vector3d eye = new Vector3d(0, 0, 15.0);
        Vector3d lookDir = new Vector3d(0, 0, -1);

        RaycastResult result = DisplayRaycaster.cast(eye, lookDir, plane);
        assertInstanceOf(RaycastResult.Hit.class, result);

        RaycastResult.Hit hit = (RaycastResult.Hit) result;
        assertEquals(15.0, hit.t(), 1e-4, "Click beyond vanilla reach (~4.5 blocks) must resolve at 15 blocks");
        assertEquals(128, hit.pixelX(), "Center X of 2x2 plane");
        assertEquals(128, hit.pixelY(), "Center Y of 2x2 plane");
    }

    /**
     * Test 2.12 — Empty-hand right-click at range (documented no-op constraint).
     *
     * In vanilla Minecraft, the client does NOT send a USE_ITEM packet when the player's
     * main-hand and off-hand are both empty while right-clicking air beyond vanilla reach (~4.5 blocks).
     * Because no packet is transmitted over the network, the server cannot detect the action.
     * This is an immutable vanilla client limitation, documented as expected degradation in PROJECT.md §4.1a.
     * Right-click interactions at range require holding any item.
     */
    @Test
    @DisplayName("Test 2.12 — Empty-hand right-click at range (documented no-op)")
    void testEmptyHandRightClickDocumentedDegradation() throws IOException {
        Path path = Paths.get("src", "main", "java", "me", "goosbanny", "goosboards", "protocol", "packet", "ClickPacketListener.java");
        assertTrue(Files.exists(path), "ClickPacketListener.java must exist");

        String content = Files.readString(path);
        assertTrue(content.contains("§4.1a"), "ClickPacketListener must cite §4.1a");
        assertTrue(content.contains("EMPTY-HAND RIGHT-CLICK CAVEAT"), "ClickPacketListener must document the empty-hand caveat");
    }

    @Test
    @DisplayName("Test 2.13 — Spatial index corner gate: player near far corner of 10x5 board is not culled")
    void testSpatialIndexCornerGateNotCulled() {
        ChunkBucketSpatialIndex index = new ChunkBucketSpatialIndex();
        // 10x5 board with topLeft at (0, 5, 0), right = +X, down = -Y
        DisplayPlane largePlane = createPlane(new Vector3d(0, 5, 0), 10.0, 5.0, 16.0);
        index.register(largePlane);

        // Player stands 2 blocks directly in front of the bottom-right corner (10, 0, 0)
        Vector3d playerPos = new Vector3d(10, 0, 2);
        // maxRadius = 5.0 blocks (distance to topLeft is ~11.36 blocks, so old topLeft check would fail)
        List<DisplayPlane> nearby = index.nearbyBoards(playerPos, 5.0, "world");

        assertEquals(1, nearby.size(), "Large board must be included in nearby boards when player is near far corner");
        assertEquals(largePlane.id(), nearby.get(0).id());

        // Also verify raycast from this corner player position hits the board near the bottom-right
        Vector3d lookDir = new Vector3d(0, 0, -1); // looking directly at board
        RaycastResult result = DisplayRaycaster.cast(playerPos, lookDir, largePlane);
        assertInstanceOf(RaycastResult.Hit.class, result);
        RaycastResult.Hit hit = (RaycastResult.Hit) result;
        assertEquals(2.0, hit.t(), 1e-4);
        assertTrue(hit.pixelX() > 1200, "Hit pixelX should be near the right edge of the 1280px wide board");
    }

    @Test
    @DisplayName("Test 2.14 — Targeted unregister and empty bucket cleanup")
    void testTargetedUnregisterAndBucketCleanup() {
        ChunkBucketSpatialIndex index = new ChunkBucketSpatialIndex();
        DisplayPlane plane = createPlane(new Vector3d(0, 0, 0), 4.0, 4.0, 16.0);
        index.register(plane);

        assertFalse(index.nearbyBoards(new Vector3d(1, 1, 1), 10.0, "world").isEmpty());

        index.unregister(plane.id());
        assertTrue(index.nearbyBoards(new Vector3d(1, 1, 1), 10.0, "world").isEmpty());
        assertNull(index.getPlane(plane.id()));
    }
}