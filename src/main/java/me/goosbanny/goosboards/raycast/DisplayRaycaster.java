package me.goosbanny.goosboards.raycast;

import me.goosbanny.goosboards.display.DisplayPlane;
import me.goosbanny.goosboards.render.buffer.BoardMaskingUtil;

import java.util.UUID;

/**
 * Ultra-performant raycasting engine supporting zero-allocation primitive scalar intersection
 * and backwards-compatible Vector3d queries.
 */
public final class DisplayRaycaster {
    private static final double PARALLEL_THRESHOLD = 1e-6;

    private static final ThreadLocal<MutableRaycastHit> THREAD_LOCAL_HIT =
            ThreadLocal.withInitial(MutableRaycastHit::new);

    private DisplayRaycaster() {}

    /**
     * Zero-allocation primitive scalar ray-plane intersection hot loop.
     * Operates purely on primitive doubles writing directly into a reusable MutableRaycastHit.
     *
     * @param plane     the target display plane
     * @param eyeX      player eye world X
     * @param eyeY      player eye world Y
     * @param eyeZ      player eye world Z
     * @param dirX      normalized look vector X
     * @param dirY      normalized look vector Y
     * @param dirZ      normalized look vector Z
     * @param maxDist   maximum raycast distance
     * @param outResult output mutable hit result
     */
    public static void intersect(
            DisplayPlane plane,
            double eyeX, double eyeY, double eyeZ,
            double dirX, double dirY, double dirZ,
            double maxDist,
            MutableRaycastHit outResult
    ) {
        if (outResult == null) {
            return;
        }
        outResult.reset();

        if (plane == null) {
            return;
        }

        Vector3d normal = plane.normal();
        if (normal == null) {
            return;
        }

        double normX = normal.x();
        double normY = normal.y();
        double normZ = normal.z();

        double denom = dirX * normX + dirY * normY + dirZ * normZ;
        if (denom >= -PARALLEL_THRESHOLD) {
            return;
        }

        Vector3d topLeft = plane.topLeft();
        double origToPlaneX = topLeft.x() - eyeX;
        double origToPlaneY = topLeft.y() - eyeY;
        double origToPlaneZ = topLeft.z() - eyeZ;

        double t = (origToPlaneX * normX + origToPlaneY * normY + origToPlaneZ * normZ) / denom;
        if (t < 0.0 || t > maxDist) {
            return;
        }

        double pHitX = eyeX + dirX * t;
        double pHitY = eyeY + dirY * t;
        double pHitZ = eyeZ + dirZ * t;

        double rX = pHitX - topLeft.x();
        double rY = pHitY - topLeft.y();
        double rZ = pHitZ - topLeft.z();

        double widthBlocks = plane.widthBlocks();
        double heightBlocks = plane.heightBlocks();
        if (widthBlocks <= 0.0 || heightBlocks <= 0.0) {
            return;
        }

        Vector3d right = plane.rightUnit();
        double u = (rX * right.x() + rY * right.y() + rZ * right.z()) / widthBlocks;
        if (u < 0.0 || u > 1.0) {
            return;
        }

        Vector3d down = plane.downUnit();
        double v = (rX * down.x() + rY * down.y() + rZ * down.z()) / heightBlocks;
        if (v < 0.0 || v > 1.0) {
            return;
        }

        int maxPixelX = (int) Math.round(widthBlocks * 128.0) - 1;
        int maxPixelY = (int) Math.round(heightBlocks * 128.0) - 1;

        int rawPixelX = (int) Math.floor(u * widthBlocks * 128.0);
        int rawPixelY = (int) Math.floor(v * heightBlocks * 128.0);

        int pixelX = Math.clamp(rawPixelX, 0, Math.max(0, maxPixelX));
        int pixelY = Math.clamp(rawPixelY, 0, Math.max(0, maxPixelY));

        int totalW = maxPixelX + 1;
        int totalH = maxPixelY + 1;

        if (plane.isCircular()) {
            double cx = totalW / 2.0;
            double cy = totalH / 2.0;
            double r = Math.min(totalW, totalH) / 2.0;
            double dx = (pixelX + 0.5) - cx;
            double dy = (pixelY + 0.5) - cy;
            if ((dx * dx + dy * dy) > (r * r)) {
                return;
            }
        } else if (plane.isRounded()) {
            int rad = plane.cornerRadius() > 0 ? plane.cornerRadius() : 16;
            int r = Math.min(rad, Math.min(totalW, totalH) / 2);
            int r2 = r * r;
            if (!BoardMaskingUtil.isInsideRounded(pixelX, pixelY, totalW, totalH, r, r2)) {
                return;
            }
        }

        outResult.set(plane.id(), pixelX, pixelY, t);
    }

    /**
     * Ray-plane intersection hot loop defaulting to the plane's configured interactionRadius.
     */
    public static void intersect(
            DisplayPlane plane,
            double eyeX, double eyeY, double eyeZ,
            double dirX, double dirY, double dirZ,
            MutableRaycastHit outResult
    ) {
        intersect(plane, eyeX, eyeY, eyeZ, dirX, dirY, dirZ, plane != null ? plane.interactionRadius() : 16.0, outResult);
    }

    /**
     * Vector3d raycast overload for convenience and backwards compatibility.
     * <p>
     * <b>PERFORMANCE WARNING:</b> This method allocates a {@link RaycastResult.Hit} record
     * on every hit. For hot paths (such as packet listeners or tick loops), call
     * {@link #intersect(DisplayPlane, double, double, double, double, double, double, MutableRaycastHit)}
     * directly with a reusable {@link MutableRaycastHit} to maintain zero-allocation steady state.
     */
    public static RaycastResult cast(Vector3d eyeOrigin, Vector3d lookDir, DisplayPlane plane) {
        if (eyeOrigin == null || lookDir == null || plane == null) {
            return RaycastResult.miss();
        }

        MutableRaycastHit hit = THREAD_LOCAL_HIT.get();
        intersect(plane, eyeOrigin.x(), eyeOrigin.y(), eyeOrigin.z(), lookDir.x(), lookDir.y(), lookDir.z(), hit);

        if (hit.hit) {
            return new RaycastResult.Hit(hit.displayId, (int) Math.round(hit.pixelX), (int) Math.round(hit.pixelY), hit.distance);
        }
        return RaycastResult.miss();
    }

    /**
     * Clears the thread-local raycast hit buffer for the calling thread to prevent classloader leaks.
     */
    public static void clearThreadLocal() {
        THREAD_LOCAL_HIT.remove();
    }
}
