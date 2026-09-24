package me.goosbanny.goosboards.display;

import me.goosbanny.goosboards.raycast.Vector3d;

import org.bukkit.block.BlockFace;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class DisplayPlaneGeometryTest {

    @Test
    @DisplayName("DisplayPlane computes outward-facing normals for all directional planes")
    void testComputeNormal() {
        // South-facing board: right = +X, down = -Y -> normal must be +Z
        DisplayPlane southPlane = new DisplayPlane(
                UUID.randomUUID(), "world", new Vector3d(0, 10, 0), 4, 4,
                new Vector3d(1, 0, 0), new Vector3d(0, -1, 0), 32.0, 16.0
        );
        assertEquals(0.0, southPlane.normal().x(), 1e-5);
        assertEquals(0.0, southPlane.normal().y(), 1e-5);
        assertEquals(1.0, southPlane.normal().z(), 1e-5);
        assertEquals(3, southPlane.itemFrameOrientation(), "Orientation for +Z normal must be 3 (South)");

        // North-facing board: right = -X, down = -Y -> normal must be -Z
        DisplayPlane northPlane = new DisplayPlane(
                UUID.randomUUID(), "world", new Vector3d(0, 10, 0), 4, 4,
                new Vector3d(-1, 0, 0), new Vector3d(0, -1, 0), 32.0, 16.0
        );
        assertEquals(0.0, northPlane.normal().x(), 1e-5);
        assertEquals(0.0, northPlane.normal().y(), 1e-5);
        assertEquals(-1.0, northPlane.normal().z(), 1e-5);
        assertEquals(2, northPlane.itemFrameOrientation(), "Orientation for -Z normal must be 2 (North)");

        // Floor (UP): down = +Z (South), right = +X (East) -> normal must be +Y (Up)
        DisplayPlane floorPlane = new DisplayPlane(
                UUID.randomUUID(), "world", new Vector3d(0, 10, 0), 4, 4,
                new Vector3d(1, 0, 0), new Vector3d(0, 0, 1), 32.0, 16.0
        );
        assertEquals(0.0, floorPlane.normal().x(), 1e-5);
        assertEquals(1.0, floorPlane.normal().y(), 1e-5);
        assertEquals(0.0, floorPlane.normal().z(), 1e-5);
        assertEquals(1, floorPlane.itemFrameOrientation(), "Orientation for +Y normal must be 1 (Up)");

        // Ceiling (DOWN): down = +Z (South), right = -X (West) -> normal must be -Y (Down)
        DisplayPlane ceilingPlane = new DisplayPlane(
                UUID.randomUUID(), "world", new Vector3d(0, 10, 0), 4, 4,
                new Vector3d(-1, 0, 0), new Vector3d(0, 0, 1), 32.0, 16.0
        );
        assertEquals(0.0, ceilingPlane.normal().x(), 1e-5);
        assertEquals(-1.0, ceilingPlane.normal().y(), 1e-5);
        assertEquals(0.0, ceilingPlane.normal().z(), 1e-5);
        assertEquals(0, ceilingPlane.itemFrameOrientation(), "Orientation for -Y normal must be 0 (Down)");
    }

    @Test
    @DisplayName("Floor and ceiling item frame rotations map correctly to 6-face matrix")
    void testFloorCeilingFrameRotations() {
        Vector3d south = new Vector3d(0, 0, 1);
        Vector3d west  = new Vector3d(-1, 0, 0);
        Vector3d north = new Vector3d(0, 0, -1);
        Vector3d east  = new Vector3d(1, 0, 0);

        // Floor (BlockFace.UP)
        assertEquals(0, DisplayPlane.getFrameRotation(BlockFace.UP, south));
        assertEquals(1, DisplayPlane.getFrameRotation(BlockFace.UP, west));
        assertEquals(2, DisplayPlane.getFrameRotation(BlockFace.UP, north));
        assertEquals(3, DisplayPlane.getFrameRotation(BlockFace.UP, east));

        // Ceiling (BlockFace.DOWN)
        assertEquals(2, DisplayPlane.getFrameRotation(BlockFace.DOWN, south));
        assertEquals(1, DisplayPlane.getFrameRotation(BlockFace.DOWN, west));
        assertEquals(0, DisplayPlane.getFrameRotation(BlockFace.DOWN, north));
        assertEquals(3, DisplayPlane.getFrameRotation(BlockFace.DOWN, east));
    }

    @Test
    @DisplayName("deriveRight computes orthogonal vector for face and down vector")
    void testDeriveRight() {
        Vector3d southDown = new Vector3d(0, 0, 1);
        Vector3d rightUp = DisplayPlane.deriveRight(BlockFace.UP, southDown);
        // (0, 0, 1) cross (0, 1, 0) = (-1, 0, 0)
        assertEquals(1.0, rightUp.length(), 1e-5);

        assertEquals(new Vector3d(1, 0, 0), DisplayPlane.deriveRight(BlockFace.SOUTH, new Vector3d(0, -1, 0)));
        assertEquals(new Vector3d(-1, 0, 0), DisplayPlane.deriveRight(BlockFace.NORTH, new Vector3d(0, -1, 0)));
    }

    @Test
    @DisplayName("Sub-voxel frame offsets are calibrated to modern voxel grid")
    void testSubVoxelFrameOffsets() {
        assertEquals(1.0 / 128.0, DisplayPlane.INVISIBLE_FRAME_OFFSET, 1e-9);
        assertEquals(9.0 / 128.0, DisplayPlane.VISIBLE_FRAME_OFFSET, 1e-9);
    }
}
