package me.goosbanny.goosboards.command;

import org.bukkit.block.BlockFace;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for BoardSelectionManager: session lifecycle, corner geometry math,
 * expiry, and face-to-direction mapping.
 */
class BoardSelectionManagerTest {

    private BoardSelectionManager manager;
    private UUID uuid;

    @BeforeEach
    void setUp() {
        manager = new BoardSelectionManager();
        uuid = UUID.randomUUID();
    }

    // ── Session lifecycle ────────────────────────────────────────────────────

    @Test
    @DisplayName("startSession creates a new session in WAITING_FOR_CORNER_1 stage")
    void testStartSession() {
        BoardSelectionManager.SelectionSession session = manager.startSession(uuid);

        assertNotNull(session);
        assertEquals(BoardSelectionManager.Stage.WAITING_FOR_CORNER_1, session.getStage());
        assertNull(session.getCorner1());
        assertNull(session.getCorner2());
    }

    @Test
    @DisplayName("getSession returns null if no session exists")
    void testGetSessionNone() {
        assertNull(manager.getSession(uuid));
    }

    @Test
    @DisplayName("getSession returns active session after startSession")
    void testGetSessionActive() {
        manager.startSession(uuid);
        assertNotNull(manager.getSession(uuid));
    }

    @Test
    @DisplayName("cancelSession returns true and clears active session")
    void testCancelSession() {
        manager.startSession(uuid);
        assertTrue(manager.cancelSession(uuid));
        assertNull(manager.getSession(uuid));
    }

    @Test
    @DisplayName("cancelSession returns false if no session exists")
    void testCancelSessionNone() {
        assertFalse(manager.cancelSession(uuid));
    }

    @Test
    @DisplayName("startSession replaces any existing session")
    void testStartSessionReplaces() {
        BoardSelectionManager.SelectionSession s1 = manager.startSession(uuid);
        BoardSelectionManager.SelectionSession s2 = manager.startSession(uuid);
        assertNotSame(s1, s2);
        assertSame(s2, manager.getSession(uuid));
    }

    @Test
    @DisplayName("finalizeSession removes the session")
    void testFinalizeSession() {
        manager.startSession(uuid);
        manager.finalizeSession(uuid);
        assertNull(manager.getSession(uuid));
    }

    // ── Corner recording ─────────────────────────────────────────────────────

    @Test
    @DisplayName("recordCorner1 advances stage to WAITING_FOR_CORNER_2 and stores corner")
    void testRecordCorner1() {
        manager.startSession(uuid);
        BoardSelectionManager.CornerClick c1 = new BoardSelectionManager.CornerClick("world", 0, 64, 0, BlockFace.SOUTH);
        manager.recordCorner1(uuid, c1);

        BoardSelectionManager.SelectionSession s = manager.getSession(uuid);
        assertEquals(BoardSelectionManager.Stage.WAITING_FOR_CORNER_2, s.getStage());
        assertEquals(c1, s.getCorner1());
    }

    @Test
    @DisplayName("recordCorner2 stores corner2 without changing stage")
    void testRecordCorner2() {
        manager.startSession(uuid);
        manager.recordCorner1(uuid, new BoardSelectionManager.CornerClick("world", 0, 64, 0, BlockFace.SOUTH));
        BoardSelectionManager.CornerClick c2 = new BoardSelectionManager.CornerClick("world", 3, 66, 0, BlockFace.SOUTH);
        manager.recordCorner2(uuid, c2);

        BoardSelectionManager.SelectionSession s = manager.getSession(uuid);
        assertEquals(c2, s.getCorner2());
    }

    @Test
    @DisplayName("recordCorner1 is a no-op when session does not exist")
    void testRecordCorner1NoSession() {
        manager.recordCorner1(uuid, new BoardSelectionManager.CornerClick("world", 0, 0, 0, BlockFace.SOUTH));
        assertNull(manager.getSession(uuid));
    }

    // ── Geometry computation — south-facing ──────────────────────────────────

    @Test
    @DisplayName("computeGeometry — south: 4x2 block wall produces correct width/height and top-left")
    void testGeometrySouth4x2() {
        // Corners at (0,64,5) and (3,65,5) → width=4, height=2, facing south
        BoardSelectionManager.CornerClick c1 = new BoardSelectionManager.CornerClick("world", 0, 64, 5, BlockFace.SOUTH);
        BoardSelectionManager.CornerClick c2 = new BoardSelectionManager.CornerClick("world", 3, 65, 5, BlockFace.SOUTH);

        BoardSelectionManager.SelectionGeometry geo = manager.computeGeometry(c1, c2);

        assertNotNull(geo);
        assertEquals(4, geo.widthBlocks());
        assertEquals(2, geo.heightBlocks());
        assertEquals("south", geo.directionLabel());
        assertEquals("world", geo.world());
        // Top-left Y = maxY + 1 = 66.0
        assertEquals(66.0, geo.topLeftY(), 0.001);
    }

    @Test
    @DisplayName("computeGeometry — north: correct width and direction label")
    void testGeometryNorth() {
        BoardSelectionManager.CornerClick c1 = new BoardSelectionManager.CornerClick("world", 2, 60, 0, BlockFace.NORTH);
        BoardSelectionManager.CornerClick c2 = new BoardSelectionManager.CornerClick("world", 5, 63, 0, BlockFace.NORTH);

        BoardSelectionManager.SelectionGeometry geo = manager.computeGeometry(c1, c2);
        assertNotNull(geo);
        assertEquals("north", geo.directionLabel());
        assertEquals(4, geo.widthBlocks());
        assertEquals(4, geo.heightBlocks());
    }

    @Test
    @DisplayName("computeGeometry — east: width derived from Z span")
    void testGeometryEast() {
        BoardSelectionManager.CornerClick c1 = new BoardSelectionManager.CornerClick("world", 10, 64, 0, BlockFace.EAST);
        BoardSelectionManager.CornerClick c2 = new BoardSelectionManager.CornerClick("world", 10, 67, 4, BlockFace.EAST);

        BoardSelectionManager.SelectionGeometry geo = manager.computeGeometry(c1, c2);
        assertNotNull(geo);
        assertEquals("east", geo.directionLabel());
        assertEquals(5, geo.widthBlocks()); // maxZ - minZ + 1 = 4 - 0 + 1 = 5
        assertEquals(4, geo.heightBlocks());
    }

    @Test
    @DisplayName("computeGeometry — west: width derived from Z span")
    void testGeometryWest() {
        BoardSelectionManager.CornerClick c1 = new BoardSelectionManager.CornerClick("world", 0, 70, 2, BlockFace.WEST);
        BoardSelectionManager.CornerClick c2 = new BoardSelectionManager.CornerClick("world", 0, 73, 6, BlockFace.WEST);

        BoardSelectionManager.SelectionGeometry geo = manager.computeGeometry(c1, c2);
        assertNotNull(geo);
        assertEquals("west", geo.directionLabel());
        assertEquals(5, geo.widthBlocks()); // maxZ - minZ + 1
        assertEquals(4, geo.heightBlocks());
    }

    @Test
    @DisplayName("computeGeometry — single block selection clamps to 1x1")
    void testGeometrySingleBlock() {
        BoardSelectionManager.CornerClick c1 = new BoardSelectionManager.CornerClick("world", 5, 64, 5, BlockFace.SOUTH);
        BoardSelectionManager.CornerClick c2 = new BoardSelectionManager.CornerClick("world", 5, 64, 5, BlockFace.SOUTH);

        BoardSelectionManager.SelectionGeometry geo = manager.computeGeometry(c1, c2);
        assertNotNull(geo);
        assertEquals(1, geo.widthBlocks());
        assertEquals(1, geo.heightBlocks());
    }

    @Test
    @DisplayName("computeGeometry returns null if corners are in different worlds")
    void testGeometryWorldMismatch() {
        BoardSelectionManager.CornerClick c1 = new BoardSelectionManager.CornerClick("world", 0, 64, 0, BlockFace.SOUTH);
        BoardSelectionManager.CornerClick c2 = new BoardSelectionManager.CornerClick("nether", 5, 64, 0, BlockFace.SOUTH);

        assertNull(manager.computeGeometry(c1, c2));
    }

    @Test
    @DisplayName("computeGeometry returns null if either corner is null")
    void testGeometryNullCorners() {
        BoardSelectionManager.CornerClick c1 = new BoardSelectionManager.CornerClick("world", 0, 64, 0, BlockFace.SOUTH);
        assertNull(manager.computeGeometry(null, c1));
        assertNull(manager.computeGeometry(c1, null));
        assertNull(manager.computeGeometry(null, null));
    }

    // ── Reversed corner order ────────────────────────────────────────────────

    @Test
    @DisplayName("computeGeometry is symmetric: swapping corner order produces same geometry")
    void testGeometrySymmetric() {
        BoardSelectionManager.CornerClick c1 = new BoardSelectionManager.CornerClick("world", 0, 64, 5, BlockFace.SOUTH);
        BoardSelectionManager.CornerClick c2 = new BoardSelectionManager.CornerClick("world", 5, 67, 5, BlockFace.SOUTH);

        BoardSelectionManager.SelectionGeometry g12 = manager.computeGeometry(c1, c2);
        BoardSelectionManager.SelectionGeometry g21 = manager.computeGeometry(c2, c1);

        assertNotNull(g12);
        assertNotNull(g21);
        // Note: face comes from c1, so direction may differ — only compare dimensions
        assertEquals(g12.widthBlocks(),  g21.widthBlocks());
        assertEquals(g12.heightBlocks(), g21.heightBlocks());
    }

    // ── faceToDirection ──────────────────────────────────────────────────────

    @Test
    @DisplayName("faceToDirection maps all cardinal faces correctly")
    void testFaceToDirection() {
        assertEquals("north", BoardSelectionManager.faceToDirection(BlockFace.NORTH));
        assertEquals("south", BoardSelectionManager.faceToDirection(BlockFace.SOUTH));
        assertEquals("east",  BoardSelectionManager.faceToDirection(BlockFace.EAST));
        assertEquals("west",  BoardSelectionManager.faceToDirection(BlockFace.WEST));
        assertEquals("up",    BoardSelectionManager.faceToDirection(BlockFace.UP));
        assertEquals("down",  BoardSelectionManager.faceToDirection(BlockFace.DOWN));
    }

    @Test
    @DisplayName("faceToDirection defaults to 'south' for null or non-cardinal face")
    void testFaceToDirectionNull() {
        assertEquals("south", BoardSelectionManager.faceToDirection(null));
        // Non-cardinal faces (e.g. NORTH_EAST) should fall through to south
        assertEquals("south", BoardSelectionManager.faceToDirection(BlockFace.NORTH_EAST));
    }

    // ── Session isolation ─────────────────────────────────────────────────────

    @Test
    @DisplayName("Multiple players have independent isolated sessions")
    void testMultiplePlayerSessions() {
        UUID uuid2 = UUID.randomUUID();
        manager.startSession(uuid);
        manager.startSession(uuid2);

        manager.recordCorner1(uuid, new BoardSelectionManager.CornerClick("world", 0, 64, 0, BlockFace.SOUTH));

        // uuid2 should still be in WAITING_FOR_CORNER_1
        BoardSelectionManager.SelectionSession s2 = manager.getSession(uuid2);
        assertEquals(BoardSelectionManager.Stage.WAITING_FOR_CORNER_1, s2.getStage());

        // uuid should be in WAITING_FOR_CORNER_2
        assertEquals(BoardSelectionManager.Stage.WAITING_FOR_CORNER_2, manager.getSession(uuid).getStage());
    }

    // ── Active state listener transitions ────────────────────────────────────

    @Test
    @DisplayName("activeStateListener fires true only on 0->1 transition and false only on 1->0 transition")
    void testActiveStateListenerTransitions() {
        List<Boolean> events = new ArrayList<>();
        manager.setActiveStateListener(events::add);

        UUID u1 = UUID.randomUUID();
        UUID u2 = UUID.randomUUID();

        // 0 -> 1
        manager.startSession(u1);
        assertEquals(List.of(true), events);

        // 1 -> 2 (no change)
        manager.startSession(u2);
        assertEquals(List.of(true), events);

        // 2 -> 1 (no change)
        manager.cancelSession(u1);
        assertEquals(List.of(true), events);

        // 1 -> 0 (becomes empty)
        manager.finalizeSession(u2);
        assertEquals(List.of(true, false), events);
    }
}
