package me.goosbanny.goosboards.scene.component.container;

import me.goosbanny.goosboards.scene.Rect;
import me.goosbanny.goosboards.scene.RenderContext;
import me.goosbanny.goosboards.scene.component.UIComponent;

import me.goosbanny.goosboards.core.logging.DebugLogger;
import me.goosbanny.goosboards.render.buffer.CanvasBuffer;
import me.goosbanny.goosboards.render.palette.PaletteQuantizer;

import java.util.Locale;

/**
 * A container and rectangular background component supporting colors, rounded corners, and borders.
 */
public class BackgroundComponent extends UIComponent {
    private int color = 0xFF202020;
    private int cornerRadius = 0;
    private int radius = 0;
    private String shape = "rectangle";
    private int outlineColor = 0x00000000;
    private double outlineWidth = 0.0;
    private boolean glow = false;
    private int glowColor = 0x00000000;
    private int glowRadius = 3;

    private int onHoverColor = 0x00000000;
    private int onHoverOutlineColor = 0x00000000;
    private double onHoverOutlineWidth = 0.0;
    private boolean hoverGlow = false;
    private int hoverGlowColor = 0x00000000;
    private boolean hovered = false;

    public BackgroundComponent(String id, boolean contextDependent) {
        super(id, contextDependent);
    }

    public boolean isCircular() {
        return "circle".equalsIgnoreCase(shape);
    }

    public boolean isRounded() {
        return "rounded".equalsIgnoreCase(shape) || "rounded_rectangle".equalsIgnoreCase(shape) || (!isCircular() && (cornerRadius > 0 || radius > 0));
    }

    public String getShape() {
        return shape;
    }

    public void setShape(String shape) {
        this.shape = shape != null ? shape.trim().toLowerCase(Locale.ROOT) : "rectangle";
        invalidateRaster();
    }

    public int getRadius() {
        return radius > 0 ? radius : cornerRadius;
    }

    public void setRadius(int radius) {
        this.radius = radius;
        this.cornerRadius = radius;
        invalidateRaster();
    }

    @Override
    public boolean isContextDependent() {
        return super.isContextDependent() || onHoverColor != 0 || onHoverOutlineColor != 0 || onHoverOutlineWidth > 0.0 || hoverGlow;
    }

    private volatile byte[] normalRaster;
    private volatile byte[] hoverRaster;
    private volatile int cachedW = -1;
    private volatile int cachedH = -1;
    private volatile int cachedRadius = 0;

    public void invalidateRaster() {
        normalRaster = null;
        hoverRaster = null;
        cachedW = -1;
        cachedH = -1;
        cachedRadius = 0;
    }

    @Override
    public void render(CanvasBuffer canvas, RenderContext ctx) {
        renderBackground(canvas, ctx);

        // Render children
        for (UIComponent child : getChildren()) {
            child.render(canvas, ctx);
        }
    }

    public void renderBackground(CanvasBuffer canvas, RenderContext ctx) {
        Rect b = getBounds();
        if (b.width() <= 0 || b.height() <= 0 || canvas == null) {
            return;
        }

        int bw = b.width();
        int bh = b.height();
        int maxGlow = Math.max(glow ? glowRadius : 0, hoverGlow ? glowRadius : 0);

        if (bw != cachedW || bh != cachedH || maxGlow != cachedRadius) {
            invalidateRaster();
            cachedW = bw;
            cachedH = bh;
            cachedRadius = maxGlow;
        }

        boolean isHovered = (ctx != null) && ctx.isHovered(getId());
        byte[] raster;
        if (isHovered) {
            if (hoverRaster == null) {
                hoverRaster = bakeRaster(bw, bh, maxGlow, true);
            }
            raster = hoverRaster;
        } else {
            if (normalRaster == null) {
                normalRaster = bakeRaster(bw, bh, maxGlow, false);
            }
            raster = normalRaster;
        }

        if (raster == null) return;

        int rasterW = bw + 2 * maxGlow;
        int rasterH = bh + 2 * maxGlow;
        int originX = b.x() - maxGlow;
        int originY = b.y() - maxGlow;

        canvas.blitRaster(originX, originY, rasterW, rasterH, raster);
    }

    private byte[] bakeRaster(int bw, int bh, int pad, boolean isHovered) {
        int rasterW = bw + 2 * pad;
        int rasterH = bh + 2 * pad;
        byte[] buf = new byte[rasterW * rasterH];

        int bx = pad;
        int by = pad;

        boolean circular = isCircular();
        boolean rounded = isRounded();
        int effRadius = radius > 0 ? radius : cornerRadius;

        // 1. Glow bloom outline
        boolean activeGlow = glow || (isHovered && hoverGlow);
        int activeGlowColor = (isHovered && hoverGlowColor != 0) ? hoverGlowColor : glowColor;
        if (activeGlow && activeGlowColor != 0 && glowRadius > 0) {
            bakeGlow(buf, rasterW, rasterH, bx, by, bw, bh, glowRadius, activeGlowColor, circular, rounded, effRadius);
        }

        // 2. Background fill
        int activeBg = (isHovered && onHoverColor != 0) ? onHoverColor : color;
        byte colorIdx = PaletteQuantizer.match(activeBg);
        if (colorIdx != 0) {
            if (circular) {
                int r = effRadius > 0 ? effRadius : Math.min(bw, bh) / 2;
                double cx = bw / 2.0;
                double cy = bh / 2.0;
                double r2 = r * r;
                for (int y = 0; y < bh; y++) {
                    int row = (by + y) * rasterW;
                    double dy = (y + 0.5) - cy;
                    for (int x = 0; x < bw; x++) {
                        double dx = (x + 0.5) - cx;
                        if (dx * dx + dy * dy <= r2) {
                            buf[row + (bx + x)] = colorIdx;
                        }
                    }
                }
            } else if (rounded && effRadius > 0) {
                int r = Math.min(effRadius, Math.min(bw, bh) / 2);
                int r2 = r * r;
                for (int y = 0; y < bh; y++) {
                    int row = (by + y) * rasterW;
                    for (int x = 0; x < bw; x++) {
                        if (isInsideRoundedCorner(x, y, bw, bh, r, r2)) {
                            buf[row + (bx + x)] = colorIdx;
                        }
                    }
                }
            } else {
                for (int y = by; y < by + bh; y++) {
                    int row = y * rasterW;
                    for (int x = bx; x < bx + bw; x++) {
                        buf[row + x] = colorIdx;
                    }
                }
            }
        }

        // 3. Crisp outline (100% solid, overrides glow without color mixing)
        int activeOutline = (isHovered && onHoverOutlineColor != 0) ? onHoverOutlineColor : outlineColor;
        double activeWidth;
        if (isHovered) {
            if (onHoverOutlineWidth > 0.0) {
                activeWidth = onHoverOutlineWidth;
            } else if (onHoverOutlineColor != 0 && outlineWidth <= 0.0) {
                activeWidth = 1.0;
            } else {
                activeWidth = outlineWidth;
            }
        } else {
            activeWidth = outlineWidth;
        }

        if (activeOutline != 0 && activeWidth > 0.0) {
            byte outlineIdx = PaletteQuantizer.match(activeOutline);
            int ow = Math.max(1, (int) Math.ceil(activeWidth));

            if (circular) {
                int r = effRadius > 0 ? effRadius : Math.min(bw, bh) / 2;
                double cx = bw / 2.0;
                double cy = bh / 2.0;
                double r2 = r * r;
                double innerR = Math.max(0.0, r - ow);
                double innerR2 = innerR * innerR;
                for (int y = 0; y < bh; y++) {
                    int row = (by + y) * rasterW;
                    double dy = (y + 0.5) - cy;
                    for (int x = 0; x < bw; x++) {
                        double dx = (x + 0.5) - cx;
                        double d2 = dx * dx + dy * dy;
                        if (d2 <= r2 && d2 >= innerR2) {
                            buf[row + (bx + x)] = outlineIdx;
                        }
                    }
                }
            } else if (rounded && effRadius > 0) {
                int r = Math.min(effRadius, Math.min(bw, bh) / 2);
                int r2 = r * r;
                int innerR = Math.max(0, r - ow);
                int innerR2 = innerR * innerR;
                int innerW = Math.max(0, bw - 2 * ow);
                int innerH = Math.max(0, bh - 2 * ow);
                for (int y = 0; y < bh; y++) {
                    int row = (by + y) * rasterW;
                    for (int x = 0; x < bw; x++) {
                        if (isInsideRoundedCorner(x, y, bw, bh, r, r2)) {
                            boolean inInner = (x >= ow && x < bw - ow && y >= ow && y < bh - ow)
                                    && isInsideRoundedCorner(x - ow, y - ow, innerW, innerH, innerR, innerR2);
                            if (!inInner) {
                                buf[row + (bx + x)] = outlineIdx;
                            }
                        }
                    }
                }
            } else {
                for (int o = 0; o < ow; o++) {
                    int topRow = (by + o) * rasterW;
                    int bottomRow = (by + bh - 1 - o) * rasterW;
                    for (int x = bx + o; x < bx + bw - o; x++) {
                        buf[topRow + x] = outlineIdx;
                        buf[bottomRow + x] = outlineIdx;
                    }
                    for (int y = by + o; y < by + bh - o; y++) {
                        buf[y * rasterW + (bx + o)] = outlineIdx;
                        buf[y * rasterW + (bx + bw - 1 - o)] = outlineIdx;
                    }
                }
            }
        }

        return buf;
    }

    private static void bakeGlow(byte[] buf, int rasterW, int rasterH, int bx, int by, int bw, int bh, int radius, int glowColor, boolean circular, boolean rounded, int cornerRadius) {
        if (radius <= 0 || glowColor == 0) return;
        int r = (glowColor >> 16) & 0xFF;
        int g = (glowColor >> 8) & 0xFF;
        int b = glowColor & 0xFF;

        for (int d = radius; d >= 1; d--) {
            float t = 1.0f - ((float) (d - 1) / (float) radius);
            float alpha = t * t * 0.75f;
            int cr = Math.min(255, (int) (r * alpha));
            int cg = Math.min(255, (int) (g * alpha));
            int cb = Math.min(255, (int) (b * alpha));

            // Prevent red glow from quantizing into orange/brown terracotta
            if (r > 160 && g < 50 && b < 50) {
                cr = Math.max(160, cr);
                cg = 0;
                cb = 0;
            }

            int blended = (0xFF << 24) | (cr << 16) | (cg << 8) | cb;
            byte glowIdx = PaletteQuantizer.match(blended);
            if (glowIdx == 0) continue;

            if (circular) {
                int baseR = cornerRadius > 0 ? cornerRadius : Math.min(bw, bh) / 2;
                int ringR = baseR + d;
                double cx = bx + bw / 2.0;
                double cy = by + bh / 2.0;
                int minX = Math.max(0, (int) Math.floor(cx - ringR));
                int maxX = Math.min(rasterW - 1, (int) Math.ceil(cx + ringR));
                int minY = Math.max(0, (int) Math.floor(cy - ringR));
                int maxY = Math.min(rasterH - 1, (int) Math.ceil(cy + ringR));
                double rOuter2 = ringR * ringR;
                double rInner2 = (ringR - 1) * (ringR - 1);
                for (int y = minY; y <= maxY; y++) {
                    int row = y * rasterW;
                    double dy = (y + 0.5) - cy;
                    for (int x = minX; x <= maxX; x++) {
                        double dx = (x + 0.5) - cx;
                        double d2 = dx * dx + dy * dy;
                        if (d2 <= rOuter2 && d2 >= rInner2) {
                            buf[row + x] = glowIdx;
                        }
                    }
                }
            } else if (rounded && cornerRadius > 0) {
                int minX = Math.max(0, bx - d);
                int maxX = Math.min(rasterW - 1, bx + bw - 1 + d);
                int minY = Math.max(0, by - d);
                int maxY = Math.min(rasterH - 1, by + bh - 1 + d);
                int crad = cornerRadius + d;
                int crad2 = crad * crad;
                int totalW = bw + 2 * d;
                int totalH = bh + 2 * d;
                int topRow = minY * rasterW;
                int bottomRow = maxY * rasterW;
                for (int x = minX; x <= maxX; x++) {
                    int localX = x - (bx - d);
                    if (isInsideRoundedCorner(localX, 0, totalW, totalH, crad, crad2)) {
                        buf[topRow + x] = glowIdx;
                    }
                    if (isInsideRoundedCorner(localX, totalH - 1, totalW, totalH, crad, crad2)) {
                        buf[bottomRow + x] = glowIdx;
                    }
                }
                for (int y = minY; y <= maxY; y++) {
                    int localY = y - (by - d);
                    if (isInsideRoundedCorner(0, localY, totalW, totalH, crad, crad2)) {
                        buf[y * rasterW + minX] = glowIdx;
                    }
                    if (isInsideRoundedCorner(totalW - 1, localY, totalW, totalH, crad, crad2)) {
                        buf[y * rasterW + maxX] = glowIdx;
                    }
                }
            } else {
                int minX = Math.max(0, bx - d);
                int maxX = Math.min(rasterW - 1, bx + bw - 1 + d);
                int minY = Math.max(0, by - d);
                int maxY = Math.min(rasterH - 1, by + bh - 1 + d);
                int topRow = minY * rasterW;
                int bottomRow = maxY * rasterW;
                for (int x = minX; x <= maxX; x++) {
                    buf[topRow + x] = glowIdx;
                    buf[bottomRow + x] = glowIdx;
                }
                for (int y = minY; y <= maxY; y++) {
                    buf[y * rasterW + minX] = glowIdx;
                    buf[y * rasterW + maxX] = glowIdx;
                }
            }
        }
    }

    @Override
    public boolean containsPixel(int localX, int localY) {
        Rect b = getBounds();
        int bw = b.width();
        int bh = b.height();
        if (localX < 0 || localX >= bw || localY < 0 || localY >= bh) {
            return false;
        }
        if (isCircular()) {
            int effRadius = radius > 0 ? radius : Math.min(bw, bh) / 2;
            double cx = bw / 2.0;
            double cy = bh / 2.0;
            double dx = (localX + 0.5) - cx;
            double dy = (localY + 0.5) - cy;
            return (dx * dx + dy * dy) <= (effRadius * effRadius);
        }
        int rad = radius > 0 ? radius : cornerRadius;
        if (isRounded() && rad > 0) {
            int r = Math.min(rad, Math.min(bw, bh) / 2);
            return isInsideRoundedCorner(localX, localY, bw, bh, r, r * r);
        }
        return true;
    }

    private static boolean isInsideRoundedCorner(int localX, int localY, int w, int h, int r, int r2) {
        int distX = (localX < r) ? localX : (localX >= w - r) ? (w - 1 - localX) : -1;
        int distY = (localY < r) ? localY : (localY >= h - r) ? (h - 1 - localY) : -1;

        if (distX >= 0 && distY >= 0) {
            int dx = r - distX;
            int dy = r - distY;
            return (dx * dx + dy * dy) <= r2;
        }
        return true;
    }

    public int getColor() {
        return color;
    }

    public void setColor(int color) {
        this.color = color;
        invalidateRaster();
    }

    public int getCornerRadius() {
        return cornerRadius;
    }

    public void setCornerRadius(int cornerRadius) {
        this.cornerRadius = Math.max(0, cornerRadius);
        invalidateRaster();
    }

    public int getOutlineColor() {
        return outlineColor;
    }

    public void setOutlineColor(int outlineColor) {
        this.outlineColor = outlineColor;
        super.setOutlineColor(outlineColor);
        invalidateRaster();
    }

    public double getOutlineWidth() {
        return outlineWidth;
    }

    public void setOutlineWidth(double outlineWidth) {
        this.outlineWidth = Math.max(0.0, outlineWidth);
        super.setOutlineWidth(this.outlineWidth);
        invalidateRaster();
    }

    public boolean isGlow() {
        return glow;
    }

    public void setGlow(boolean glow) {
        this.glow = glow;
        invalidateRaster();
    }

    public int getGlowColor() {
        return glowColor;
    }

    public void setGlowColor(int glowColor) {
        this.glowColor = glowColor;
        invalidateRaster();
    }

    public int getGlowRadius() {
        return glowRadius;
    }

    public void setGlowRadius(int glowRadius) {
        this.glowRadius = Math.max(0, glowRadius);
        invalidateRaster();
    }

    public int getOnHoverColor() {
        return onHoverColor;
    }

    public void setOnHoverColor(int onHoverColor) {
        this.onHoverColor = onHoverColor;
        invalidateRaster();
    }

    public int getOnHoverOutlineColor() {
        return onHoverOutlineColor;
    }

    public void setOnHoverOutlineColor(int onHoverOutlineColor) {
        this.onHoverOutlineColor = onHoverOutlineColor;
        super.setOnHoverOutlineColor(onHoverOutlineColor);
        invalidateRaster();
    }

    public double getOnHoverOutlineWidth() {
        return onHoverOutlineWidth;
    }

    public void setOnHoverOutlineWidth(double onHoverOutlineWidth) {
        this.onHoverOutlineWidth = Math.max(0.0, onHoverOutlineWidth);
        super.setOnHoverOutlineWidth(this.onHoverOutlineWidth);
        invalidateRaster();
    }

    public boolean isHoverGlow() {
        return hoverGlow;
    }

    public void setHoverGlow(boolean hoverGlow) {
        this.hoverGlow = hoverGlow;
        invalidateRaster();
    }

    public int getHoverGlowColor() {
        return hoverGlowColor;
    }

    public void setHoverGlowColor(int hoverGlowColor) {
        this.hoverGlowColor = hoverGlowColor;
        invalidateRaster();
    }

    public boolean isHovered() {
        return hovered;
    }

    public void setHovered(boolean hovered) {
        this.hovered = hovered;
    }

    protected static void renderGlow(CanvasBuffer canvas, int bx, int by, int bw, int bh, int radius, int glowColor, int cornerRadius) {
        if (radius <= 0 || glowColor == 0 || canvas == null) return;
        int r = (glowColor >> 16) & 0xFF;
        int g = (glowColor >> 8) & 0xFF;
        int b = glowColor & 0xFF;

        for (int d = radius; d >= 1; d--) {
            float t = 1.0f - ((float) (d - 1) / (float) radius);
            float alpha = t * t * 0.75f;
            int cr = Math.min(255, (int) (r * alpha));
            int cg = Math.min(255, (int) (g * alpha));
            int cb = Math.min(255, (int) (b * alpha));
            int blended = (0xFF << 24) | (cr << 16) | (cg << 8) | cb;
            byte glowIdx = PaletteQuantizer.match(blended);
            if (glowIdx == 0) continue;

            int minX = Math.max(0, bx - d);
            int maxX = Math.min(canvas.getWidth() - 1, bx + bw - 1 + d);
            int minY = Math.max(0, by - d);
            int maxY = Math.min(canvas.getHeight() - 1, by + bh - 1 + d);

            if (cornerRadius <= 0) {
                for (int x = minX; x <= maxX; x++) {
                    canvas.setPixel(x, minY, glowIdx);
                    canvas.setPixel(x, maxY, glowIdx);
                }
                for (int y = minY; y <= maxY; y++) {
                    canvas.setPixel(minX, y, glowIdx);
                    canvas.setPixel(maxX, y, glowIdx);
                }
            } else {
                int crad = cornerRadius + d;
                int crad2 = crad * crad;
                int totalW = bw + 2 * d;
                int totalH = bh + 2 * d;
                for (int x = minX; x <= maxX; x++) {
                    int localX = x - (bx - d);
                    if (isInsideRoundedCorner(localX, 0, totalW, totalH, crad, crad2)) {
                        canvas.setPixel(x, minY, glowIdx);
                    }
                    if (isInsideRoundedCorner(localX, totalH - 1, totalW, totalH, crad, crad2)) {
                        canvas.setPixel(x, maxY, glowIdx);
                    }
                }
                for (int y = minY; y <= maxY; y++) {
                    int localY = y - (by - d);
                    if (isInsideRoundedCorner(0, localY, totalW, totalH, crad, crad2)) {
                        canvas.setPixel(minX, y, glowIdx);
                    }
                    if (isInsideRoundedCorner(totalW - 1, localY, totalW, totalH, crad, crad2)) {
                        canvas.setPixel(maxX, y, glowIdx);
                    }
                }
            }
        }
    }
}