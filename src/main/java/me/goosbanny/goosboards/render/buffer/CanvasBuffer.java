package me.goosbanny.goosboards.render.buffer;

public interface CanvasBuffer {
    int getWidth();
    int getHeight();
    void setPixel(int x, int y, byte colorIndex);
    byte getPixel(int x, int y);
    byte[] getSubTile(int tileX, int tileY);
    boolean isTileDirty(int tileX, int tileY, long contentHash);
    void markClean(int tileX, int tileY, long newContentHash);

    default CanvasTile getCanvasTile(int tileX, int tileY) {
        return null;
    }

    default byte[] getFrontTile(int tileX, int tileY) {
        CanvasTile tile = getCanvasTile(tileX, tileY);
        return tile != null ? tile.getFrontBuffer() : getSubTile(tileX, tileY);
    }

    /**
     * Blits a 2D byte raster into the canvas buffer.
     * Pixels with value 0 (transparent) are skipped.
     */
    default void blitRaster(int originX, int originY, int rasterW, int rasterH, byte[] raster) {
        if (raster == null || rasterW <= 0 || rasterH <= 0) return;
        int canvasW = getWidth();
        int canvasH = getHeight();
        for (int y = 0; y < rasterH; y++) {
            int screenY = originY + y;
            if (screenY < 0 || screenY >= canvasH) continue;
            int rowOffset = y * rasterW;
            for (int x = 0; x < rasterW; x++) {
                byte p = raster[rowOffset + x];
                if (p != 0) {
                    int screenX = originX + x;
                    if (screenX >= 0 && screenX < canvasW) {
                        setPixel(screenX, screenY, p);
                    }
                }
            }
        }
    }

    /**
     * Applies a circular alpha mask centered on the canvas.
     * Pixels outside the circle of radius Math.min(width, height) / 2 are set to 0 (transparent).
     */
    default void applyCircularMask() {
        int w = getWidth();
        int h = getHeight();
        int radius = Math.min(w, h) / 2;
        applyCircularMask(w / 2, h / 2, radius);
    }

    /**
     * Applies a circular alpha mask centered at (centerX, centerY) with specified radius.
     * Pixels outside the circle are set to 0 (transparent).
     */
    default void applyCircularMask(int centerX, int centerY, int radius) {
        if (radius <= 0) return;
        int w = getWidth();
        int h = getHeight();
        double r2 = (double) radius * radius;
        for (int y = 0; y < h; y++) {
            double dy = (y + 0.5) - centerY;
            for (int x = 0; x < w; x++) {
                double dx = (x + 0.5) - centerX;
                if (dx * dx + dy * dy > r2) {
                    setPixel(x, y, (byte) 0);
                }
            }
        }
    }
}
