package me.goosbanny.goosboards.render.buffer;

import me.goosbanny.goosboards.render.palette.ColorUtils;
import me.goosbanny.goosboards.render.palette.PaletteQuantizer;

/**
 * High-performance geometry masking and outline rendering utility for CanvasBuffer displays.
 * Supports rectangular, rounded-rectangle, and circular clipping masks and outlines.
 */
public final class BoardMaskingUtil {

    private BoardMaskingUtil() {
    }

    public static void renderBoardOutline(CanvasBuffer canvas, String colorStr, int thickness) {
        renderBoardOutline(canvas, colorStr, thickness, "rectangle", 0);
    }

    public static void renderBoardOutline(CanvasBuffer canvas, String colorStr, int thickness, String shape, int cornerRadius) {
        if (canvas == null) return;
        int color = ColorUtils.parseColor(colorStr, 0xFFFFFFFF);
        byte colorIdx = color != 0 ? PaletteQuantizer.match(color) : 0;

        int w = canvas.getWidth();
        int h = canvas.getHeight();
        int thick = Math.max(1, thickness);

        if ("circle".equalsIgnoreCase(shape)) {
            double cx = w / 2.0;
            double cy = h / 2.0;
            double r = Math.min(w, h) / 2.0;
            double r2 = r * r;
            double innerR = Math.max(0.0, r - thick);
            double innerR2 = innerR * innerR;

            for (int y = 0; y < h; y++) {
                double dy = (y + 0.5) - cy;
                for (int x = 0; x < w; x++) {
                    double dx = (x + 0.5) - cx;
                    double d2 = dx * dx + dy * dy;
                    if (d2 > r2) {
                        canvas.setPixel(x, y, (byte) 0);
                    } else if (colorIdx != 0 && d2 >= innerR2) {
                        canvas.setPixel(x, y, colorIdx);
                    }
                }
            }
            return;
        }

        if ("rounded".equalsIgnoreCase(shape) || "rounded_rectangle".equalsIgnoreCase(shape)) {
            int rad = cornerRadius > 0 ? cornerRadius : 16;
            int r = Math.min(rad, Math.min(w, h) / 2);
            int r2 = r * r;
            int innerR = Math.max(0, r - thick);
            int innerR2 = innerR * innerR;
            int innerW = Math.max(0, w - 2 * thick);
            int innerH = Math.max(0, h - 2 * thick);

            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    boolean inOuter = isInsideRounded(x, y, w, h, r, r2);
                    if (!inOuter) {
                        canvas.setPixel(x, y, (byte) 0);
                    } else if (colorIdx != 0) {
                        boolean inInner = (x >= thick && x < w - thick && y >= thick && y < h - thick)
                                && isInsideRounded(x - thick, y - thick, innerW, innerH, innerR, innerR2);
                        if (!inInner) {
                            canvas.setPixel(x, y, colorIdx);
                        }
                    }
                }
            }
            return;
        }

        if (colorIdx != 0) {
            for (int t = 0; t < thick; t++) {
                int topY = t;
                int bottomY = h - 1 - t;
                for (int x = 0; x < w; x++) {
                    if (topY >= 0 && topY < h) canvas.setPixel(x, topY, colorIdx);
                    if (bottomY >= 0 && bottomY < h) canvas.setPixel(x, bottomY, colorIdx);
                }
            }
            for (int t = 0; t < thick; t++) {
                int leftX = t;
                int rightX = w - 1 - t;
                for (int y = 0; y < h; y++) {
                    if (leftX >= 0 && leftX < w) canvas.setPixel(leftX, y, colorIdx);
                    if (rightX >= 0 && rightX < w) canvas.setPixel(rightX, y, colorIdx);
                }
            }
        }
    }

    public static boolean isInsideRounded(int x, int y, int w, int h, int r, int r2) {
        int distX = (x < r) ? x : (x >= w - r) ? (w - 1 - x) : -1;
        int distY = (y < r) ? y : (y >= h - r) ? (h - 1 - y) : -1;
        if (distX >= 0 && distY >= 0) {
            int dx = r - distX;
            int dy = r - distY;
            return (dx * dx + dy * dy) <= r2;
        }
        return true;
    }

    public static void maskOutsideBoard(CanvasBuffer canvas, String shape, int cornerRadius) {
        if (canvas == null) return;
        int w = canvas.getWidth();
        int h = canvas.getHeight();
        if ("circle".equalsIgnoreCase(shape)) {
            double cx = w / 2.0;
            double cy = h / 2.0;
            double r = Math.min(w, h) / 2.0;
            double r2 = r * r;
            for (int y = 0; y < h; y++) {
                double dy = (y + 0.5) - cy;
                for (int x = 0; x < w; x++) {
                    double dx = (x + 0.5) - cx;
                    if (dx * dx + dy * dy > r2) {
                        canvas.setPixel(x, y, (byte) 0);
                    }
                }
            }
        } else if ("rounded".equalsIgnoreCase(shape) || "rounded_rectangle".equalsIgnoreCase(shape)) {
            int rad = cornerRadius > 0 ? cornerRadius : 16;
            int r = Math.min(rad, Math.min(w, h) / 2);
            int r2 = r * r;
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    if (!isInsideRounded(x, y, w, h, r, r2)) {
                        canvas.setPixel(x, y, (byte) 0);
                    }
                }
            }
        }
    }
}
