package me.goosbanny.goosboards.scene.component.visual;

import me.goosbanny.goosboards.scene.Rect;
import me.goosbanny.goosboards.scene.RenderContext;
import me.goosbanny.goosboards.scene.component.UIComponent;

import me.goosbanny.goosboards.media.SkinManager;
import me.goosbanny.goosboards.render.buffer.CanvasBuffer;
import me.goosbanny.goosboards.media.security.SafeMediaLoader;
import org.bukkit.entity.Player;

import java.util.function.Consumer;

/**
 * 2D player head skin avatar component with async fetching and Steve placeholder fallback.
 */
public class Head2DComponent extends UIComponent {

    private String skin = "%player_name%";
    private String fallbackSkin = "Steve";
    private static SafeMediaLoader mediaLoader = null;

    // Default Steve face colors (8x8) as palette color indices / RGB fallback
    private static final byte SKIN_BASE = 24;    // Flesh tone
    private static final byte HAIR_COLOR = 105;  // Dark brown
    private static final byte EYE_WHITE = 34;   // White
    private static final byte EYE_PUPIL = 98;   // Blue

    public static void setSafeMediaLoader(SafeMediaLoader loader) {
        mediaLoader = loader;
    }

    private static Consumer<String> assetLoadedCallback = null;

    public static void setAssetLoadedCallback(Consumer<String> callback) {
        assetLoadedCallback = callback;
    }

    private static void notifyAssetLoaded(String skinKey) {
        if (assetLoadedCallback != null && skinKey != null) {
            try {
                assetLoadedCallback.accept(skinKey);
            } catch (Throwable ignored) {}
        }
    }

    public static void clearCache() {
        SkinManager.clearCache();
    }

    public Head2DComponent(String id, boolean contextDependent) {
        super(id, contextDependent);
    }

    public String getSkin() {
        return skin;
    }

    public void setSkin(String skin) {
        this.skin = skin != null ? skin : "%player_name%";
    }

    public String getFallbackSkin() {
        return fallbackSkin;
    }

    public void setFallbackSkin(String fallbackSkin) {
        this.fallbackSkin = (fallbackSkin != null && !fallbackSkin.isBlank()) ? fallbackSkin : "Steve";
    }

    @Override
    public void render(CanvasBuffer canvas, RenderContext ctx) {
        Rect b = getBounds();
        if (b.width() <= 0 || b.height() <= 0 || canvas == null) {
            return;
        }

        String resolvedSkin = (ctx != null) ? ctx.resolve(this.skin) : this.skin;
        if (resolvedSkin == null || resolvedSkin.isBlank() || (resolvedSkin.startsWith("%") && resolvedSkin.endsWith("%"))) {
            resolvedSkin = this.fallbackSkin;
        }

        Player viewer = ctx != null ? ctx.viewer() : null;
        byte[] customPixels = SkinManager.getOrLoadSkin(
                resolvedSkin,
                viewer,
                mediaLoader,
                Head2DComponent::notifyAssetLoaded
        );

        if (customPixels == null && !resolvedSkin.equalsIgnoreCase(this.fallbackSkin)) {
            // While downloading custom skin, resolve fallback skin (e.g. Steve) so head is never blank
            customPixels = SkinManager.getOrLoadSkin(
                    this.fallbackSkin,
                    viewer,
                    mediaLoader,
                    null
            );
        }

        // Render an 8x8 face scaled to bounds
        int x0 = b.x();
        int y0 = b.y();
        int w = b.width();
        int h = b.height();
        if (w <= 0 || h <= 0) return;

        // Precompute horizontal grid mapping to avoid per-pixel divisions in hot loop
        int[] gridXLookup = new int[w];
        for (int px = 0; px < w; px++) {
            gridXLookup[px] = Math.clamp((px * 8) / w, 0, 7);
        }

        for (int py = 0; py < h; py++) {
            int gridY = Math.clamp((py * 8) / h, 0, 7);
            int rowOffset = gridY * 8;
            int canvasY = y0 + py;
            for (int px = 0; px < w; px++) {
                int gridX = gridXLookup[px];
                byte color = (customPixels != null)
                        ? customPixels[rowOffset + gridX]
                        : getFacePixel(gridX, gridY);
                if (color != 0) {
                    canvas.setPixel(x0 + px, canvasY, color);
                }
            }
        }
    }

    private byte getFacePixel(int gx, int gy) {
        // Hair top two rows
        if (gy < 2) {
            return HAIR_COLOR;
        }
        // Hair sides
        if (gx == 0 || gx == 7) {
            return HAIR_COLOR;
        }
        // Hair bangs
        if (gy == 2 && (gx == 1 || gx == 6)) {
            return HAIR_COLOR;
        }
        // Eyes (row 4)
        if (gy == 4) {
            if (gx == 2 || gx == 5) return EYE_PUPIL;
            if (gx == 1 || gx == 6) return EYE_WHITE;
        }
        // Base skin for remaining pixels
        return SKIN_BASE;
    }

    @Override
    public boolean containsPixel(int localX, int localY) {
        Rect b = getBounds();
        return localX >= 0 && localX < b.width() && localY >= 0 && localY < b.height();
    }
}