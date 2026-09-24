package me.goosbanny.goosboards.scene.component.text;

import me.goosbanny.goosboards.scene.Rect;
import me.goosbanny.goosboards.scene.RenderContext;
import me.goosbanny.goosboards.scene.component.UIComponent;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import me.goosbanny.goosboards.core.logging.DebugLogger;
import me.goosbanny.goosboards.core.logging.MessageService;
import me.goosbanny.goosboards.render.buffer.CanvasBuffer;
import me.goosbanny.goosboards.render.palette.PaletteQuantizer;
import me.goosbanny.goosboards.render.font.TextRenderer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.format.TextDecoration.State;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Text component rendering MiniMessage-formatted rich text with custom typography.
 * Implements two-tier caching: pre-converting legacy codes to MiniMessage at load time,
 * permanently caching static Components, and LRU caching dynamic placeholder outputs.
 */
public class TextComponent extends UIComponent {
    private String text = "";
    private String font = "SansSerif";
    private int fontSize = 16;
    private int outlineColor = 0x00000000;
    private double outlineStroke = 0.0;
    private String fallbackText = "";

    // Two-tier component cache
    private Component cachedStaticComponent = null;
    private final Cache<String, Component> dynamicComponentCache = Caffeine.newBuilder()
            .maximumSize(100)
            .expireAfterAccess(Duration.ofMinutes(5))
            .build();
    private final Cache<String, byte[]> dynamicRasterCache = Caffeine.newBuilder()
            .maximumSize(100)
            .expireAfterAccess(Duration.ofSeconds(30))
            .build();

    public TextComponent(String id, boolean contextDependent) {
        super(id, contextDependent);
    }

    private byte[] cachedStaticPixels = null;
    private int cachedWidth = 0;
    private int cachedHeight = 0;

    @Override
    public void render(CanvasBuffer canvas, RenderContext ctx) {
        if (canvas == null) return;
        Rect b = getBounds();
        if (b.width() <= 0 || b.height() <= 0) return;

        byte[] pixelsToBlit = null;
        if (cachedStaticComponent != null && cachedStaticPixels != null
                && cachedWidth == b.width() && cachedHeight == b.height()) {
            pixelsToBlit = cachedStaticPixels;
        } else {
            Component component;
            if (cachedStaticComponent != null) {
                component = cachedStaticComponent;
            } else {
                String resolved = ctx != null ? ctx.resolve(text) : text;
                if (resolved == null || resolved.isBlank() || (resolved.equals(text) && text != null && text.startsWith("%") && text.endsWith("%"))) {
                    if (fallbackText != null && !fallbackText.isBlank()) {
                        resolved = fallbackText;
                    } else if (resolved == null || resolved.isBlank()) {
                        return;
                    }
                }
                component = dynamicComponentCache.get(resolved, MiniMessage.miniMessage()::deserialize);
            }

            if (component == null) return;
            String plainText = PlainTextComponentSerializer.plainText().serialize(component);
            if (plainText.isBlank()) return;

            if (cachedStaticComponent != null) {
                pixelsToBlit = rasterizeText(plainText, component, b.width(), b.height());
                cachedStaticPixels = pixelsToBlit;
                cachedWidth = b.width();
                cachedHeight = b.height();
            } else {
                int colorVal = component.color() != null ? component.color().value() : 0;
                String cacheKey = plainText + "@" + b.width() + "x" + b.height() + "@" + colorVal + "@" + fontSize + "@" + outlineColor + "@" + font + "@" + getAlignment() + "@" + getVerticalAlignment();
                pixelsToBlit = dynamicRasterCache.get(cacheKey, k -> rasterizeText(plainText, component, b.width(), b.height()));
            }
        }

        if (pixelsToBlit == null) return;

        int bw = b.width();
        int bh = b.height();
        for (int y = 0; y < bh; y++) {
            int cy = b.y() + y;
            if (cy < 0 || cy >= canvas.getHeight()) continue;
            for (int x = 0; x < bw; x++) {
                int cx = b.x() + x;
                if (cx < 0 || cx >= canvas.getWidth()) continue;
                byte colorIdx = pixelsToBlit[y * bw + x];
                if (colorIdx != 0) {
                    canvas.setPixel(cx, cy, colorIdx);
                }
            }
        }

        if (hasOutline()) {
            renderBoundsOutline(canvas, ctx);
        }
    }

    private byte[] rasterizeText(String plainText, Component component, int width, int height) {
        Color textColor = Color.WHITE;
        if (component.color() != null) {
            textColor = new Color(component.color().value(), false);
        }

        BufferedImage img = TextRenderer.renderText(
                plainText,
                component,
                this.font,
                this.fontSize,
                textColor,
                width,
                height,
                getAlignment(),
                getVerticalAlignment(),
                this.outlineStroke,
                this.outlineColor
        );

        if (img == null) {
            return new byte[Math.max(1, width * height)];
        }

        byte[] output = new byte[width * height];
        int nonZero = 0;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int argb = img.getRGB(x, y);
                int alpha = (argb >>> 24);
                if (alpha > 64) {
                    output[y * width + x] = PaletteQuantizer.match(argb);
                    nonZero++;
                }
            }
        }
        return output;
    }

    @Override
    public boolean containsPixel(int localX, int localY) {
        Rect b = getBounds();
        return localX >= 0 && localX < b.width() && localY >= 0 && localY < b.height();
    }

    public String getText() {
        return text;
    }

    public void setText(String rawText) {
        // Tier 1: Convert legacy & or hex codes into native MiniMessage tags once at load time
        this.text = rawText != null ? MessageService.convertLegacyToMiniMessage(rawText) : "";

        // Tier 2: If static (no placeholders), pre-compile and cache Kyori Component permanently
        if (!this.text.contains("%") && !this.text.isBlank()) {
            try {
                this.cachedStaticComponent = MiniMessage.miniMessage().deserialize(this.text);
            } catch (Exception e) {
                this.cachedStaticComponent = null;
            }
        } else {
            this.cachedStaticComponent = null;
        }
        this.cachedStaticPixels = null;
        this.dynamicComponentCache.invalidateAll();
    }

    public Component getCachedStaticComponent() {
        return cachedStaticComponent;
    }

    public String getFont() {
        return font;
    }

    public void setFont(String font) {
        if (font != null && !font.isBlank()) {
            this.font = font.trim();
        }
    }

    public int getFontSize() {
        return fontSize;
    }

    /**
     * Sets the font size. A value of {@code 0} is a special sentinel meaning "auto-fit to bounds".
     * Negative values are clamped to 0. Positive values are used as-is.
     */
    public void setFontSize(int fontSize) {
        this.fontSize = Math.max(0, fontSize);
    }

    public int getOutlineColor() {
        return outlineColor;
    }

    @Override
    public void setOutlineColor(int outlineColor) {
        this.outlineColor = outlineColor;
        super.setOutlineColor(outlineColor);
    }

    public double getOutlineStroke() {
        return outlineStroke;
    }

    public void setOutlineStroke(double outlineStroke) {
        this.outlineStroke = Math.max(0.0, outlineStroke);
        super.setOutlineWidth(this.outlineStroke);
    }

    @Override
    public void setOutlineWidth(double outlineWidth) {
        super.setOutlineWidth(outlineWidth);
        this.outlineStroke = outlineWidth;
    }

    public String getFallbackText() {
        return fallbackText;
    }

    public void setFallbackText(String fallbackText) {
        this.fallbackText = fallbackText != null ? fallbackText : "";
    }

    private record TextFragment(String text, Color color, boolean bold, boolean italic) {}

    private void collectFragments(Component comp, Color parentColor, boolean parentBold, boolean parentItalic, List<TextFragment> out) {
        if (comp == null) return;
        Color color = parentColor;
        if (comp.color() != null) {
            color = new Color(comp.color().value(), false);
        }
        boolean bold = parentBold;
        if (comp.hasDecoration(TextDecoration.BOLD)) {
            bold = comp.decoration(TextDecoration.BOLD) == State.TRUE;
        }
        boolean italic = parentItalic;
        if (comp.hasDecoration(TextDecoration.ITALIC)) {
            italic = comp.decoration(TextDecoration.ITALIC) == State.TRUE;
        }

        if (comp instanceof net.kyori.adventure.text.TextComponent tc) {
            String content = tc.content();
            if (content != null && !content.isEmpty()) {
                out.add(new TextFragment(content, color != null ? color : Color.WHITE, bold, italic));
            }
        }
        for (Component child : comp.children()) {
            collectFragments(child, color, bold, italic, out);
        }
    }
}