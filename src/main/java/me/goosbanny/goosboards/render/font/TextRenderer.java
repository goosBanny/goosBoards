package me.goosbanny.goosboards.render.font;

import me.goosbanny.goosboards.render.buffer.CanvasBuffer;
import me.goosbanny.goosboards.render.palette.PaletteQuantizer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.font.FontRenderContext;
import java.awt.font.TextLayout;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Modern baseline text rendering engine.
 * Renders rich MiniMessage and plain text elements into isolated ARGB {@link BufferedImage}s
 * using {@link TextLayout} with exact baseline ascent offsets, binary search font auto-sizing,
 * and zero parent-canvas clipping.
 */
public final class TextRenderer {

    private static final Pattern MINIMESSAGE_TAG_PATTERN = Pattern.compile("<[^>]+>");
    private static final Pattern LEGACY_COLOR_PATTERN = Pattern.compile("(?i)[§&][0-9a-fk-or]");
    private static final FontRenderContext DEFAULT_FRC = new FontRenderContext(null, true, true);

    private TextRenderer() {}

    /**
     * Strips all MiniMessage and legacy formatting tags from a string before dimension calculations.
     */
    public static String stripFormatting(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        String stripped = MINIMESSAGE_TAG_PATTERN.matcher(text).replaceAll("");
        return LEGACY_COLOR_PATTERN.matcher(stripped).replaceAll("");
    }

    /**
     * Binary searches for the largest font size within [1, 512] that fits the given dimensions.
     * Uses a 64pt baseline heuristic for efficient convergence.
     */
    public static int findFittingFontSize(String plainText, String fontName, int maxWidth, int maxHeight) {
        if (plainText == null || plainText.isBlank() || maxWidth <= 0 || maxHeight <= 0) {
            return 16;
        }

        String measurementText = plainText + " ";
        int low = 1;
        int high = Math.min(512, Math.max(8, maxHeight * 2));
        int best = low;

        while (low <= high) {
            int mid = (low + high) >>> 1;
            Font font = new Font(fontName, Font.PLAIN, mid);
            try {
                TextLayout layout = new TextLayout(measurementText, font, DEFAULT_FRC);
                float advance = layout.getAdvance();
                float totalHeight = layout.getAscent() + layout.getDescent() + layout.getLeading();

                if (advance <= maxWidth && totalHeight <= maxHeight) {
                    best = mid;
                    low = mid + 1; // Try larger
                } else {
                    high = mid - 1; // Too big
                }
            } catch (Throwable t) {
                high = mid - 1;
            }
        }

        return Math.max(1, best);
    }

    public record TextFragment(String text, Color color, boolean bold, boolean italic) {}

    /**
     * Recursively extracts styled text fragments from an Adventure Component.
     */
    public static void collectFragments(Component comp, Color parentColor, boolean parentBold, boolean parentItalic, List<TextFragment> out) {
        if (comp == null) return;
        Color color = parentColor;
        if (comp.color() != null) {
            color = new Color(comp.color().value(), false);
        }
        boolean bold = parentBold;
        if (comp.hasDecoration(TextDecoration.BOLD)) {
            bold = comp.decoration(TextDecoration.BOLD) == TextDecoration.State.TRUE;
        }
        boolean italic = parentItalic;
        if (comp.hasDecoration(TextDecoration.ITALIC)) {
            italic = comp.decoration(TextDecoration.ITALIC) == TextDecoration.State.TRUE;
        }

        if (comp instanceof TextComponent tc) {
            String content = tc.content();
            if (content != null && !content.isEmpty()) {
                out.add(new TextFragment(content, color != null ? color : Color.WHITE, bold, italic));
            }
        }
        for (Component child : comp.children()) {
            collectFragments(child, color, bold, italic, out);
        }
    }

    /**
     * Renders text to a dedicated ARGB {@link BufferedImage} using {@link TextLayout} with exact baseline ascent.
     * Never calls {@code setClip()} on any canvas.
     */
    public static BufferedImage renderText(
            String plainText,
            Component adventureComponent,
            String fontName,
            int fontSize,
            Color defaultColor,
            int width,
            int height,
            String alignment,
            String verticalAlignment,
            double outlineStroke,
            int outlineColor
    ) {
        if (width <= 0 || height <= 0) {
            return null;
        }

        String stripped = (plainText != null && !plainText.isBlank()) ? plainText : "";
        if (stripped.isBlank() && adventureComponent != null) {
            stripped = PlainTextComponentSerializer.plainText().serialize(adventureComponent);
        }
        if (stripped.isBlank()) {
            return new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        }

        // Auto-sizing binary search if fontSize <= 0
        int resolvedFontSize = fontSize;
        if (resolvedFontSize <= 0) {
            resolvedFontSize = findFittingFontSize(stripped, fontName, width, height);
        }
        resolvedFontSize = Math.max(1, resolvedFontSize);

        List<TextFragment> fragments = new ArrayList<>();
        if (adventureComponent != null) {
            collectFragments(adventureComponent, defaultColor != null ? defaultColor : Color.WHITE, false, false, fragments);
        }
        if (fragments.isEmpty()) {
            fragments.add(new TextFragment(stripped, defaultColor != null ? defaultColor : Color.WHITE, false, false));
        }

        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = img.createGraphics();

        try {
            g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2d.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);

            Font baseFont = new Font(fontName, Font.PLAIN, resolvedFontSize);
            FontRenderContext frc = g2d.getFontRenderContext();

            // Append trailing space to prevent TextLayout crashes on empty/trailing whitespace
            String layoutString = stripped + " ";
            TextLayout fullLayout;
            try {
                fullLayout = new TextLayout(layoutString, baseFont, frc);
            } catch (Throwable t) {
                fullLayout = null;
            }

            float layoutWidth = fullLayout != null ? fullLayout.getAdvance() : g2d.getFontMetrics(baseFont).stringWidth(stripped);
            float ascent = fullLayout != null ? fullLayout.getAscent() : g2d.getFontMetrics(baseFont).getAscent();
            float descent = fullLayout != null ? fullLayout.getDescent() : g2d.getFontMetrics(baseFont).getDescent();
            float totalHeight = fullLayout != null ? (ascent + descent + fullLayout.getLeading()) : g2d.getFontMetrics(baseFont).getHeight();

            boolean isMultiLine = stripped.contains("\n") || ((height >= totalHeight * 1.25) && layoutWidth > width);

            if (isMultiLine) {
                record StyledRun(String text, Color color, boolean bold, boolean italic) {}
                class Word {
                    final List<StyledRun> runs = new ArrayList<>();
                    float width = 0;
                    void addRun(StyledRun run, float runWidth) {
                        runs.add(run);
                        width += runWidth;
                    }
                }
                interface LayoutToken {}
                record WordToken(Word word) implements LayoutToken {}
                record NewlineToken() implements LayoutToken {}

                List<LayoutToken> tokens = new ArrayList<>();
                Word currentWord = new Word();

                for (TextFragment frag : fragments) {
                    String fragText = frag.text();
                    if (fragText == null || fragText.isEmpty()) continue;

                    int fragStyle = (frag.bold() ? Font.BOLD : 0) | (frag.italic() ? Font.ITALIC : 0);
                    Font fragFont = baseFont.deriveFont(fragStyle);
                    FontMetrics fm = g2d.getFontMetrics(fragFont);

                    int len = fragText.length();
                    int runStart = -1;

                    for (int i = 0; i < len; i++) {
                        char c = fragText.charAt(i);
                        if (c == '\n') {
                            if (runStart != -1) {
                                String sub = fragText.substring(runStart, i);
                                float w = fm.stringWidth(sub);
                                currentWord.addRun(new StyledRun(sub, frag.color(), frag.bold(), frag.italic()), w);
                                runStart = -1;
                            }
                            if (!currentWord.runs.isEmpty()) {
                                tokens.add(new WordToken(currentWord));
                                currentWord = new Word();
                            }
                            tokens.add(new NewlineToken());
                        } else if (c == ' ' || c == '\t') {
                            if (runStart != -1) {
                                String sub = fragText.substring(runStart, i);
                                float w = fm.stringWidth(sub);
                                currentWord.addRun(new StyledRun(sub, frag.color(), frag.bold(), frag.italic()), w);
                                runStart = -1;
                            }
                            if (!currentWord.runs.isEmpty()) {
                                tokens.add(new WordToken(currentWord));
                                currentWord = new Word();
                            }
                        } else {
                            if (runStart == -1) {
                                runStart = i;
                            }
                        }
                    }
                    if (runStart != -1) {
                        String sub = fragText.substring(runStart, len);
                        float w = fm.stringWidth(sub);
                        currentWord.addRun(new StyledRun(sub, frag.color(), frag.bold(), frag.italic()), w);
                    }
                }
                if (!currentWord.runs.isEmpty()) {
                    tokens.add(new WordToken(currentWord));
                }

                float spaceWidth = g2d.getFontMetrics(baseFont).stringWidth(" ");
                List<List<Word>> lines = new ArrayList<>();
                List<Word> currentLine = new ArrayList<>();
                float currentLineWidth = 0;

                for (LayoutToken token : tokens) {
                    if (token instanceof NewlineToken) {
                        lines.add(currentLine);
                        currentLine = new ArrayList<>();
                        currentLineWidth = 0;
                    } else if (token instanceof WordToken wt) {
                        Word w = wt.word();
                        float needed = currentLine.isEmpty() ? w.width : (spaceWidth + w.width);
                        if (currentLineWidth + needed <= width || currentLine.isEmpty()) {
                            currentLine.add(w);
                            currentLineWidth += needed;
                        } else {
                            lines.add(currentLine);
                            currentLine = new ArrayList<>();
                            currentLine.add(w);
                            currentLineWidth = w.width;
                        }
                    }
                }
                if (!currentLine.isEmpty()) {
                    lines.add(currentLine);
                }

                float lineHeight = totalHeight * 1.15f;
                float blockHeight = lines.size() * lineHeight;
                float startY;
                if ("top".equalsIgnoreCase(verticalAlignment)) {
                    startY = (float) Math.ceil(ascent);
                } else if ("bottom".equalsIgnoreCase(verticalAlignment)) {
                    startY = height - blockHeight + (float) Math.ceil(ascent);
                } else {
                    startY = Math.max((float) Math.ceil(ascent), (height - blockHeight) / 2.0f + (float) Math.ceil(ascent));
                }

                int stroke = (outlineStroke > 0.0 && outlineColor != 0) ? (int) Math.ceil(outlineStroke) : 0;
                Color outCol = stroke > 0 ? new Color(outlineColor, true) : null;

                for (int l = 0; l < lines.size(); l++) {
                    float lineY = startY + l * lineHeight;
                    if (l > 0 && lineY - ascent >= height) break;
                    List<Word> line = lines.get(l);

                    float lineWidth = 0;
                    for (int wi = 0; wi < line.size(); wi++) {
                        lineWidth += line.get(wi).width;
                        if (wi < line.size() - 1) lineWidth += spaceWidth;
                    }

                    float lineX;
                    if ("center".equalsIgnoreCase(alignment)) {
                        lineX = Math.max(0, (width - lineWidth) / 2.0f);
                    } else if ("right".equalsIgnoreCase(alignment)) {
                        lineX = Math.max(0, width - lineWidth);
                    } else {
                        lineX = 0;
                    }

                    // Stroke outline pass
                    if (stroke > 0) {
                        g2d.setColor(outCol);
                        for (int dx = -stroke; dx <= stroke; dx++) {
                            for (int dy = -stroke; dy <= stroke; dy++) {
                                if (dx != 0 || dy != 0) {
                                    float currX = lineX + dx;
                                    for (int wi = 0; wi < line.size(); wi++) {
                                        Word w = line.get(wi);
                                        for (StyledRun run : w.runs) {
                                            int style = (run.bold() ? Font.BOLD : 0) | (run.italic() ? Font.ITALIC : 0);
                                            Font wf = baseFont.deriveFont(style);
                                            g2d.setFont(wf);
                                            g2d.drawString(run.text(), currX, lineY + dy);
                                            currX += g2d.getFontMetrics(wf).stringWidth(run.text());
                                        }
                                        if (wi < line.size() - 1) {
                                            currX += spaceWidth;
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Foreground pass
                    float currX = lineX;
                    for (int wi = 0; wi < line.size(); wi++) {
                        Word w = line.get(wi);
                        for (StyledRun run : w.runs) {
                            int style = (run.bold() ? Font.BOLD : 0) | (run.italic() ? Font.ITALIC : 0);
                            Font wf = baseFont.deriveFont(style);
                            g2d.setFont(wf);
                            g2d.setColor(run.color());
                            g2d.drawString(run.text(), currX, lineY);
                            currX += g2d.getFontMetrics(wf).stringWidth(run.text());
                        }
                        if (wi < line.size() - 1) {
                            currX += spaceWidth;
                        }
                    }
                }
            } else {
                // Auto-scale down if single line overflows container width
                if (layoutWidth > width && width > 0) {
                    double scale = (double) width / layoutWidth;
                    int fitted = Math.max(8, (int) Math.floor(resolvedFontSize * scale));
                    if (fitted < resolvedFontSize) {
                        resolvedFontSize = fitted;
                        baseFont = new Font(fontName, Font.PLAIN, resolvedFontSize);
                        try {
                            fullLayout = new TextLayout(layoutString, baseFont, frc);
                        } catch (Throwable t) {
                            fullLayout = null;
                        }
                        layoutWidth = fullLayout != null ? fullLayout.getAdvance() : g2d.getFontMetrics(baseFont).stringWidth(stripped);
                        ascent = fullLayout != null ? fullLayout.getAscent() : g2d.getFontMetrics(baseFont).getAscent();
                        descent = fullLayout != null ? fullLayout.getDescent() : g2d.getFontMetrics(baseFont).getDescent();
                        totalHeight = fullLayout != null ? (ascent + descent + fullLayout.getLeading()) : g2d.getFontMetrics(baseFont).getHeight();
                    }
                }

                // Horizontal alignment
                float drawX;
                if ("center".equalsIgnoreCase(alignment)) {
                    drawX = Math.max(0, (width - layoutWidth) / 2.0f);
                } else if ("right".equalsIgnoreCase(alignment)) {
                    drawX = Math.max(0, width - layoutWidth);
                } else {
                    drawX = 0;
                }

                // Vertical baseline alignment: drawY MUST be the baseline (Math.ceil(ascent))
                float drawY;
                if ("top".equalsIgnoreCase(verticalAlignment)) {
                    drawY = (float) Math.ceil(ascent);
                } else if ("bottom".equalsIgnoreCase(verticalAlignment)) {
                    drawY = height - descent;
                } else {
                    drawY = (float) Math.ceil((height - totalHeight) / 2.0f + ascent);
                }

                // Stroke outline/shadow pass
                if (outlineStroke > 0.0 && outlineColor != 0) {
                    Color outCol = new Color(outlineColor, true);
                    g2d.setColor(outCol);
                    int stroke = (int) Math.ceil(outlineStroke);

                    for (int dx = -stroke; dx <= stroke; dx++) {
                        for (int dy = -stroke; dy <= stroke; dy++) {
                            if (dx != 0 || dy != 0) {
                                float currX = drawX + dx;
                                for (TextFragment frag : fragments) {
                                    if (frag.text() == null || frag.text().isEmpty()) continue;
                                    int style = (frag.bold() ? Font.BOLD : 0) | (frag.italic() ? Font.ITALIC : 0);
                                    Font fragFont = baseFont.deriveFont(style);
                                    g2d.setFont(fragFont);
                                    g2d.drawString(frag.text(), currX, drawY + dy);
                                    currX += g2d.getFontMetrics(fragFont).stringWidth(frag.text());
                                }
                            }
                        }
                    }
                }

                // Foreground text pass (exact fragment metrics without trailing spaces)
                float currX = drawX;
                for (TextFragment frag : fragments) {
                    if (frag.text() == null || frag.text().isEmpty()) continue;
                    int style = (frag.bold() ? Font.BOLD : 0) | (frag.italic() ? Font.ITALIC : 0);
                    Font fragFont = baseFont.deriveFont(style);
                    g2d.setFont(fragFont);
                    g2d.setColor(frag.color());
                    g2d.drawString(frag.text(), currX, drawY);
                    currX += g2d.getFontMetrics(fragFont).stringWidth(frag.text());
                }
            }

        } finally {
            g2d.dispose();
        }

        return img;
    }

    /**
     * Composites an isolated ARGB {@link BufferedImage} onto the target {@link CanvasBuffer}
     * matching colors to the 256-color map palette without clipping.
     */
    public static void blitToCanvas(BufferedImage img, CanvasBuffer canvas, int destX, int destY) {
        if (img == null || canvas == null) return;
        int w = img.getWidth();
        int h = img.getHeight();

        for (int y = 0; y < h; y++) {
            int cy = destY + y;
            if (cy < 0 || cy >= canvas.getHeight()) continue;
            for (int x = 0; x < w; x++) {
                int cx = destX + x;
                if (cx < 0 || cx >= canvas.getWidth()) continue;
                int argb = img.getRGB(x, y);
                int alpha = (argb >>> 24);
                if (alpha > 64) {
                    byte palIdx = PaletteQuantizer.match(argb);
                    if (palIdx != 0) {
                        canvas.setPixel(cx, cy, palIdx);
                    }
                }
            }
        }
    }
}