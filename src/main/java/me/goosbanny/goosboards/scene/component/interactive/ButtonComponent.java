package me.goosbanny.goosboards.scene.component.interactive;

import me.goosbanny.goosboards.scene.Rect;
import me.goosbanny.goosboards.scene.RenderContext;
import me.goosbanny.goosboards.scene.component.UIComponent;
import me.goosbanny.goosboards.scene.component.container.BackgroundComponent;
import me.goosbanny.goosboards.scene.component.visual.ImageComponent;

import me.goosbanny.goosboards.render.buffer.CanvasBuffer;
import me.goosbanny.goosboards.render.palette.PaletteQuantizer;
import me.goosbanny.goosboards.render.font.TextRenderer;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * An interactive button component extending background rendering with hover states and action maps.
 */
public class ButtonComponent extends BackgroundComponent {
    private final Map<String, Object> onClickActions = new HashMap<>();
    private final Map<String, Object> onHoverActions = new HashMap<>();

    private String text = "";
    private String font = "SansSerif";
    private int fontSize = 16;
    private int textColor = 0xFFFFFFFF;

    private String imageName = "";
    private String hoverImageName = "";
    private volatile ImageComponent.CachedImage cachedImage = null;
    private volatile ImageComponent.CachedImage cachedHoverImage = null;

    public ButtonComponent(String id, boolean contextDependent) {
        super(id, true); // Buttons are interactive and context-dependent
    }

    @Override
    public boolean isContextDependent() {
        return true;
    }

    public String getImageName() {
        return imageName;
    }

    public void setImageName(String imageName) {
        this.imageName = imageName != null ? imageName : "";
        this.cachedImage = null;
    }

    public String getHoverImageName() {
        return hoverImageName;
    }

    public void setHoverImageName(String hoverImageName) {
        this.hoverImageName = hoverImageName != null ? hoverImageName : "";
        this.cachedHoverImage = null;
    }

    public Map<String, Object> getOnClickActions() {
        return Collections.unmodifiableMap(onClickActions);
    }

    public void addOnClickAction(String key, Object action) {
        if (key != null && action != null) {
            onClickActions.put(key, action);
        }
    }

    public Map<String, Object> getOnHoverActions() {
        return Collections.unmodifiableMap(onHoverActions);
    }

    public void addOnHoverAction(String key, Object action) {
        if (key != null && action != null) {
            onHoverActions.put(key, action);
        }
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text != null ? text : "";
    }

    public String getFont() {
        return font;
    }

    public void setFont(String font) {
        this.font = font != null ? font : "SansSerif";
    }

    public int getFontSize() {
        return fontSize;
    }

    public void setFontSize(int fontSize) {
        this.fontSize = Math.max(0, fontSize);
    }

    public int getTextColor() {
        return textColor;
    }

    public void setTextColor(int textColor) {
        this.textColor = textColor;
    }

    @Override
    public void render(CanvasBuffer canvas, RenderContext ctx) {
        Rect b = getBounds();
        if (b.width() <= 0 || b.height() <= 0 || canvas == null) {
            return;
        }

        // 1-3. Render cached background, glow, rounded corners, and outline
        renderBackground(canvas, ctx);

        boolean isHovered = (ctx != null) && ctx.isHovered(getId());
        int bx = b.x();
        int by = b.y();
        int bw = b.width();
        int bh = b.height();

        // 4. Render button image or icon if configured
        String activeImage = (isHovered && hoverImageName != null && !hoverImageName.isBlank())
                ? hoverImageName
                : imageName;
        if (activeImage != null && !activeImage.isBlank()) {
            String resolvedImage = (ctx != null && ctx.viewer() != null) ? ctx.resolve(activeImage) : activeImage;
            boolean hasTextOrChildren = (text != null && !text.isBlank()) || !getChildren().isEmpty();
            int iconW, iconH, iconX, iconY;
            if (hasTextOrChildren && bw > (int) (bh * 1.2)) {
                // Wide button with text/children: render as icon on the left
                iconH = Math.max(12, Math.min(bh - 8, 48));
                if (iconH > bh) iconH = bh;
                iconW = iconH;
                iconX = bx + 8;
                iconY = by + (bh - iconH) / 2;
            } else {
                // Icon-only or square button: fills bounds
                iconW = bw;
                iconH = bh;
                iconX = bx;
                iconY = by;
            }

            ImageComponent.CachedImage snap;
            if (isHovered) {
                snap = this.cachedHoverImage;
                if (snap == null || snap.width() != iconW || snap.height() != iconH) {
                    byte[] loaded = ImageComponent.loadAndQuantizeImage(getId(), resolvedImage, iconW, iconH, ci -> this.cachedHoverImage = ci);
                    if (loaded != null) {
                        snap = new ImageComponent.CachedImage(loaded, iconW, iconH);
                        this.cachedHoverImage = snap;
                    }
                }
            } else {
                snap = this.cachedImage;
                if (snap == null || snap.width() != iconW || snap.height() != iconH) {
                    byte[] loaded = ImageComponent.loadAndQuantizeImage(getId(), resolvedImage, iconW, iconH, ci -> this.cachedImage = ci);
                    if (loaded != null) {
                        snap = new ImageComponent.CachedImage(loaded, iconW, iconH);
                        this.cachedImage = snap;
                    }
                }
            }

            if (snap != null && snap.pixels() != null) {
                byte[] pixels = snap.pixels();
                for (int y = 0; y < iconH; y++) {
                    int cy = iconY + y;
                    if (cy < 0 || cy >= canvas.getHeight()) continue;
                    for (int x = 0; x < iconW; x++) {
                        int cx = iconX + x;
                        if (cx < 0 || cx >= canvas.getWidth()) continue;
                        byte pixelIdx = pixels[y * iconW + x];
                        if (pixelIdx != 0) {
                            canvas.setPixel(cx, cy, pixelIdx);
                        }
                    }
                }
            }
        }

        // Render button text if present directly
        if (text != null && !text.isBlank()) {
            String resolved = ctx != null ? ctx.resolve(text) : text;
            BufferedImage img = TextRenderer.renderText(
                    resolved,
                    null,
                    font,
                    fontSize,
                    new Color(textColor, true),
                    bw,
                    bh,
                    getAlignment(),
                    getVerticalAlignment(),
                    0.0,
                    0
            );
            if (img != null) {
                TextRenderer.blitToCanvas(img, canvas, bx, by);
            }
        }

        // Render child components without clipping
        for (UIComponent child : getChildren()) {
            child.render(canvas, ctx);
        }
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
}