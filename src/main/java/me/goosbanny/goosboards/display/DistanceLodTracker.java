package me.goosbanny.goosboards.display;

import me.goosbanny.goosboards.display.DisplayPlane;
import me.goosbanny.goosboards.raycast.Vector3d;

/**
 * Distance-based Level-Of-Detail (LOD) and occlusion culler.
 * Decays packet transmission frequency with viewer distance and completely
 * culls viewers positioned behind the board plane.
 */
public final class DistanceLodTracker {

    public static final double DEFAULT_NEAR_DISTANCE = 6.0;   // <= 6 blocks: 20 FPS (every tick)
    public static final double DEFAULT_MEDIUM_DISTANCE = 16.0; // 6 < d <= 16 blocks: 5 FPS (every 4 ticks)
    public static final long DEFAULT_NEAR_DIVISOR = 1L;
    public static final long DEFAULT_MEDIUM_DIVISOR = 4L;
    public static final long DEFAULT_FAR_DIVISOR = 20L;

    public static final double NEAR_THRESHOLD = DEFAULT_NEAR_DISTANCE;
    public static final double MEDIUM_THRESHOLD = DEFAULT_MEDIUM_DISTANCE;

    private static volatile double nearDistance = DEFAULT_NEAR_DISTANCE;
    private static volatile double mediumDistance = DEFAULT_MEDIUM_DISTANCE;
    private static volatile long nearDivisor = DEFAULT_NEAR_DIVISOR;
    private static volatile long mediumDivisor = DEFAULT_MEDIUM_DIVISOR;
    private static volatile long farDivisor = DEFAULT_FAR_DIVISOR;

    private DistanceLodTracker() {
    }

    public static void configureLod(double nearDist, long nearDiv, double medDist, long medDiv, long farDiv) {
        nearDistance = nearDist > 0 ? nearDist : DEFAULT_NEAR_DISTANCE;
        nearDivisor = nearDiv > 0 ? nearDiv : DEFAULT_NEAR_DIVISOR;
        mediumDistance = medDist > 0 ? medDist : DEFAULT_MEDIUM_DISTANCE;
        mediumDivisor = medDiv > 0 ? medDiv : DEFAULT_MEDIUM_DIVISOR;
        farDivisor = farDiv > 0 ? farDiv : DEFAULT_FAR_DIVISOR;
    }

    /**
     * Determines whether a viewer should receive a render/map packet on the current tick.
     *
     * @param distance    distance in blocks between viewer eye and board
     * @param occluded    true if viewer is behind the display plane
     * @param currentTick the current server or animation tick
     * @return true if the packet should be rendered and sent; false to skip
     */
    public static boolean shouldRender(double distance, boolean occluded, long currentTick) {
        if (occluded) {
            return false; // 0 FPS: completely skip rendering and packets
        }
        if (distance <= nearDistance) {
            return (nearDivisor <= 1L) || ((currentTick % nearDivisor) == 0L);
        }
        if (distance <= mediumDistance) {
            return (currentTick % mediumDivisor) == 0L;
        }
        return (currentTick % farDivisor) == 0L;
    }

    /**
     * Computes whether a viewer eye position is behind the board plane (viewing back face).
     * Direction D is from player towards the board center. D · N > 0 means the player-to-board vector
     * aligns with outward normal (player is behind the board) — cull.
     */
    public static boolean isViewingBackFace(double eyeX, double eyeY, double eyeZ, DisplayPlane plane) {
        if (plane == null || plane.normal() == null) {
            return false;
        }
        Vector3d center = plane.center();
        Vector3d normal = plane.normal();
        if (center == null) {
            return false;
        }

        // Vector D pointing from player towards the board center
        double dx = center.x() - eyeX;
        double dy = center.y() - eyeY;
        double dz = center.z() - eyeZ;

        // D · N > 0 means player is on the back-face side (behind the board) — cull
        return (dx * normal.x() + dy * normal.y() + dz * normal.z()) > 0.0;
    }

    public static boolean isViewingBackFace(Vector3d eyePos, DisplayPlane plane) {
        if (eyePos == null) {
            return false;
        }
        return isViewingBackFace(eyePos.x(), eyePos.y(), eyePos.z(), plane);
    }

    /**
     * @deprecated Renamed to {@link #isViewingBackFace(Vector3d, DisplayPlane)} for clarity.
     */
    @Deprecated
    public static boolean isOccluded(Vector3d eyePos, DisplayPlane plane) {
        return isViewingBackFace(eyePos, plane);
    }

    public static double computeDistance(double eyeX, double eyeY, double eyeZ, DisplayPlane plane) {
        if (plane == null) {
            return Double.MAX_VALUE;
        }
        Vector3d center = plane.center();
        if (center == null) {
            return Double.MAX_VALUE;
        }
        double dx = eyeX - center.x();
        double dy = eyeY - center.y();
        double dz = eyeZ - center.z();
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /**
     * Computes the Euclidean distance between viewer eye position and board center.
     */
    public static double computeDistance(Vector3d eyePos, DisplayPlane plane) {
        if (eyePos == null) {
            return Double.MAX_VALUE;
        }
        return computeDistance(eyePos.x(), eyePos.y(), eyePos.z(), plane);
    }
}