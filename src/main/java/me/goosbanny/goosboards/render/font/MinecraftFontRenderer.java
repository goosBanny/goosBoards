package me.goosbanny.goosboards.render.font;

import me.goosbanny.goosboards.render.palette.PaletteQuantizer;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Renders pixel-crisp legacy-formatted text (&0-&f, hex colors) for Minecraft map displays.
 */
public final class MinecraftFontRenderer {

    private static final int[] MC_COLORS = new int[] {
            0xFF000000, // 0 Black
            0xFF0000AA, // 1 Dark Blue
            0xFF00AA00, // 2 Dark Green
            0xFF00AAAA, // 3 Dark Aqua
            0xFFAA0000, // 4 Dark Red
            0xFFAA00AA, // 5 Dark Purple
            0xFFFFAA00, // 6 Gold
            0xFFAAAAAA, // 7 Gray
            0xFF555555, // 8 Dark Gray
            0xFF5555FF, // 9 Blue
            0xFF55FF55, // a Green
            0xFF55FFFF, // b Aqua
            0xFFFF5555, // c Red
            0xFFFF55FF, // d Light Purple
            0xFFFFFF55, // e Yellow
            0xFFFFFFFF  // f White
    };

    private static final Pattern COLOR_PATTERN = Pattern.compile("(?i)(?:[&§]([0-9a-fk-or])|[&§]#([0-9a-f]{6})|<#([0-9a-f]{6})>)");

    public record TextSpan(String text, int color, boolean bold, boolean italic) {}

    private MinecraftFontRenderer() {}

    public static List<TextSpan> parseSpans(String rawText, int defaultColor) {
        List<TextSpan> spans = new ArrayList<>();
        if (rawText == null || rawText.isEmpty()) {
            return spans;
        }

        int currentColor = defaultColor;
        boolean bold = false;
        boolean italic = false;

        Matcher matcher = COLOR_PATTERN.matcher(rawText);
        int lastEnd = 0;

        while (matcher.find()) {
            if (matcher.start() > lastEnd) {
                String sub = rawText.substring(lastEnd, matcher.start());
                spans.add(new TextSpan(sub, currentColor, bold, italic));
            }

            if (matcher.group(1) != null) {
                char code = Character.toLowerCase(matcher.group(1).charAt(0));
                if (code >= '0' && code <= '9') {
                    currentColor = MC_COLORS[code - '0'];
                    bold = false;
                    italic = false;
                } else if (code >= 'a' && code <= 'f') {
                    currentColor = MC_COLORS[10 + (code - 'a')];
                    bold = false;
                    italic = false;
                } else if (code == 'l') {
                    bold = true;
                } else if (code == 'o') {
                    italic = true;
                } else if (code == 'r') {
                    currentColor = defaultColor;
                    bold = false;
                    italic = false;
                }
            } else if (matcher.group(2) != null) {
                try {
                    currentColor = 0xFF000000 | Integer.parseInt(matcher.group(2), 16);
                } catch (NumberFormatException ignored) {}
            } else if (matcher.group(3) != null) {
                try {
                    currentColor = 0xFF000000 | Integer.parseInt(matcher.group(3), 16);
                } catch (NumberFormatException ignored) {}
            }

            lastEnd = matcher.end();
        }

        if (lastEnd < rawText.length()) {
            spans.add(new TextSpan(rawText.substring(lastEnd), currentColor, bold, italic));
        }

        return spans;
    }

    private static volatile String BEST_FONT_NAME = null;

    private static String getBestFontName() {
        if (BEST_FONT_NAME != null) {
            return BEST_FONT_NAME;
        }
        String[] candidates = {"Dialog", Font.SANS_SERIF, Font.MONOSPACED, "SansSerif"};
        for (String candidate : candidates) {
            try {
                Font f = new Font(candidate, Font.PLAIN, 12);
                BufferedImage probe = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
                Graphics2D g = probe.createGraphics();
                try {
                    if (g.getFontMetrics(f).stringWidth("A") > 0) {
                        BEST_FONT_NAME = candidate;
                        return candidate;
                    }
                } finally {
                    g.dispose();
                }
            } catch (Throwable ignored) {}
        }
        BEST_FONT_NAME = Font.SANS_SERIF;
        return BEST_FONT_NAME;
    }

    public static byte[] renderToPixels(
            String rawText,
            int defaultColor,
            int scale,
            boolean shadow,
            int width,
            int height,
            String align,
            String vAlign
    ) {
        return renderToPixels(rawText, defaultColor, scale, 0, shadow, width, height, align, vAlign);
    }

    public static byte[] renderToPixels(
            String rawText,
            int defaultColor,
            int scale,
            int fontSize,
            boolean shadow,
            int width,
            int height,
            String align,
            String vAlign
    ) {
        if (width <= 0 || height <= 0 || rawText == null || rawText.isBlank()) {
            return new byte[Math.max(1, width * height)];
        }

        int effectiveScale = Math.max(1, scale);
        int baseFontSize;
        if (fontSize > 0) {
            baseFontSize = fontSize;
        } else if (scale > 1) {
            baseFontSize = Math.max(8, 14 * effectiveScale);
        } else {
            baseFontSize = 16; // Clear, readable 16px default! (Was 8px which was unviewable)
        }
        String fontName = getBestFontName();
        Font regularFont = new Font(fontName, Font.PLAIN, baseFontSize);
        Font boldFont = new Font(fontName, Font.BOLD, baseFontSize);

        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = img.createGraphics();
        try {
            g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);

            List<TextSpan> spans = parseSpans(rawText, defaultColor);

            // Compute total text width
            int totalWidth = 0;
            for (TextSpan span : spans) {
                g2d.setFont(span.bold() ? boldFont : regularFont);
                totalWidth += g2d.getFontMetrics().stringWidth(span.text());
            }

            FontMetrics fm = g2d.getFontMetrics(regularFont);
            int textHeight = fm.getHeight();

            // Auto-scale down if text overflows bounding box
            if ((totalWidth > width && width > 0) || (textHeight > height && height > 0)) {
                double scaleX = width > 0 ? (double) width / totalWidth : 1.0;
                double scaleY = height > 0 ? (double) height / textHeight : 1.0;
                double scaleFactor = Math.min(scaleX, scaleY);
                int fittedSize = Math.max(8, (int) Math.floor(baseFontSize * scaleFactor));
                if (fittedSize < baseFontSize) {
                    baseFontSize = fittedSize;
                    regularFont = new Font(fontName, Font.PLAIN, baseFontSize);
                    boldFont = new Font(fontName, Font.BOLD, baseFontSize);
                    totalWidth = 0;
                    for (TextSpan span : spans) {
                        g2d.setFont(span.bold() ? boldFont : regularFont);
                        totalWidth += g2d.getFontMetrics().stringWidth(span.text());
                    }
                    fm = g2d.getFontMetrics(regularFont);
                    textHeight = fm.getHeight();
                }
            }

            int startX;
            if ("center".equalsIgnoreCase(align)) {
                startX = Math.max(0, (width - totalWidth) / 2);
            } else if ("right".equalsIgnoreCase(align)) {
                startX = Math.max(0, width - totalWidth);
            } else {
                startX = 0;
            }

            int startY;
            if ("center".equalsIgnoreCase(vAlign)) {
                startY = (height - textHeight) / 2 + fm.getAscent();
            } else if ("bottom".equalsIgnoreCase(vAlign)) {
                startY = height - fm.getDescent();
            } else {
                startY = fm.getAscent();
            }

            // Draw shadow pass first
            if (shadow) {
                int shadowOffset = Math.max(1, baseFontSize / 12);
                int currX = startX + shadowOffset;
                int shadowY = startY + shadowOffset;
                for (TextSpan span : spans) {
                    g2d.setFont(span.bold() ? boldFont : regularFont);
                    int rgb = span.color();
                    int r = ((rgb >> 16) & 0xFF) / 4;
                    int g = ((rgb >> 8) & 0xFF) / 4;
                    int b = (rgb & 0xFF) / 4;
                    g2d.setColor(new Color(r, g, b));
                    g2d.drawString(span.text(), currX, shadowY);
                    currX += g2d.getFontMetrics().stringWidth(span.text());
                }
            }

            // Draw foreground pass
            int currX = startX;
            for (TextSpan span : spans) {
                g2d.setFont(span.bold() ? boldFont : regularFont);
                g2d.setColor(new Color(span.color(), false));
                g2d.drawString(span.text(), currX, startY);
                currX += g2d.getFontMetrics().stringWidth(span.text());
            }
        } finally {
            g2d.dispose();
        }

        byte[] output = new byte[width * height];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int argb = img.getRGB(x, y);
                int alpha = (argb >>> 24);
                if (alpha >= 64) {
                    output[y * width + x] = PaletteQuantizer.match(argb);
                }
            }
        }
        return output;
    }
}
