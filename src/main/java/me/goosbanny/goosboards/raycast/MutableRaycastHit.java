package me.goosbanny.goosboards.raycast;

import java.util.UUID;

/**
 * Reusable mutable hit result container for high-frequency (20Hz) raycast loops.
 * Avoids creating new RaycastResult or Hit objects on the heap during look-vector checks.
 */
public final class MutableRaycastHit {
    public boolean hit;
    public UUID displayId;
    public double pixelX;
    public double pixelY;
    public double distance;

    public MutableRaycastHit() {
        reset();
    }

    public void reset() {
        this.hit = false;
        this.displayId = null;
        this.pixelX = 0.0;
        this.pixelY = 0.0;
        this.distance = 0.0;
    }

    public void set(UUID displayId, double pixelX, double pixelY, double distance) {
        this.hit = true;
        this.displayId = displayId;
        this.pixelX = pixelX;
        this.pixelY = pixelY;
        this.distance = distance;
    }

    public int getIntPixelX() {
        return (int) Math.round(pixelX);
    }

    public int getIntPixelY() {
        return (int) Math.round(pixelY);
    }
}
