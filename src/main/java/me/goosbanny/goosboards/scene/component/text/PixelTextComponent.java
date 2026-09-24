package me.goosbanny.goosboards.scene.component.text;

import me.goosbanny.goosboards.scene.Rect;
import me.goosbanny.goosboards.scene.RenderContext;
import me.goosbanny.goosboards.scene.component.UIComponent;

import me.goosbanny.goosboards.render.buffer.CanvasBuffer;
import me.goosbanny.goosboards.render.font.MinecraftFontRenderer;

import java.util.Objects;

/**
 * Fast pixel-aligned text component rendering legacy formatted text.
 */
public class PixelTextComponent extends UIComponent {
    private String text = "";
    private int color = 0xFFFFFFFF;
    private int fontScale = 1;
    private int fontSize = 0;
    private boolean shadow = false;
    private String fallbackText = "";

    public PixelTextComponent(String id, boolean contextDependent) {
        super(id, contextDependent);
    }

    private String cachedResolvedText = null;
    private int cachedColor = 0;
    private int cachedScale = 1;
    private int cachedFontSize = 0;
    private boolean cachedShadow = false;
    private int cachedW = 0;
    private int cachedH = 0;
    private String cachedAlign = "";
    private String cachedVAlign = "";
    private byte[] cachedPixels = null;

    @Override
    public void render(CanvasBuffer canvas, RenderContext ctx) {
        if (canvas == null) return;
        Rect b = getBounds();
        if (b.width() <= 0 || b.height() <= 0) return;

        String resolved = ctx != null ? ctx.resolve(text) : text;
        if (resolved == null || resolved.isBlank() || (resolved.equals(text) && text != null && text.startsWith("%") && text.endsWith("%"))) {
            if (fallbackText != null && !fallbackText.isBlank()) {
                resolved = fallbackText;
            } else if (resolved == null || resolved.isBlank()) {
                return;
            }
        }

        byte[] pixels;
        String align = getAlignment();
        String vAlign = getVerticalAlignment();
        if (cachedPixels != null
                && resolved.equals(cachedResolvedText)
                && color == cachedColor
                && fontScale == cachedScale
                && fontSize == cachedFontSize
                && shadow == cachedShadow
                && b.width() == cachedW
                && b.height() == cachedH
                && Objects.equals(align, cachedAlign)
                && Objects.equals(vAlign, cachedVAlign)) {
            pixels = cachedPixels;
        } else {
            pixels = MinecraftFontRenderer.renderToPixels(
                    resolved,
                    color,
                    fontScale,
                    fontSize,
                    shadow,
                    b.width(),
                    b.height(),
                    align,
                    vAlign
            );
            cachedResolvedText = resolved;
            cachedColor = color;
            cachedScale = fontScale;
            cachedFontSize = fontSize;
            cachedShadow = shadow;
            cachedW = b.width();
            cachedH = b.height();
            cachedAlign = align;
            cachedVAlign = vAlign;
            cachedPixels = pixels;
        }

        canvas.blitRaster(b.x(), b.y(), b.width(), b.height(), pixels);
    }

    @Override
    public boolean containsPixel(int localX, int localY) {
        Rect b = getBounds();
        return localX >= 0 && localX < b.width() && localY >= 0 && localY < b.height();
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text != null ? text : "";
    }

    public int getColor() {
        return color;
    }

    public void setColor(int color) {
        this.color = color;
    }

    public int getFontScale() {
        return fontScale;
    }

    public void setFontScale(int fontScale) {
        this.fontScale = Math.max(1, fontScale);
    }

    public int getFontSize() {
        return fontSize;
    }

    public void setFontSize(int fontSize) {
        this.fontSize = Math.max(0, fontSize);
    }

    public boolean isShadow() {
        return shadow;
    }

    public void setShadow(boolean shadow) {
        this.shadow = shadow;
    }

    public String getFallbackText() {
        return fallbackText;
    }

    public void setFallbackText(String fallbackText) {
        this.fallbackText = fallbackText != null ? fallbackText : "";
    }
}