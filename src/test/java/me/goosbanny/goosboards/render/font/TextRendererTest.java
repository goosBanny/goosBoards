package me.goosbanny.goosboards.render.font;

import me.goosbanny.goosboards.GoosBoards;

import me.goosbanny.goosboards.render.buffer.CanvasBuffer;
import me.goosbanny.goosboards.render.buffer.CanvasBufferImpl;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TextRendererTest {

    @Test
    @DisplayName("stripFormatting removes MiniMessage and legacy color codes cleanly")
    void testStripFormatting() {
        assertEquals("", TextRenderer.stripFormatting(null));
        assertEquals("", TextRenderer.stripFormatting(""));
        assertEquals("Hello World", TextRenderer.stripFormatting("<red><bold>Hello World</bold></red>"));
        assertEquals("GoosBoards", TextRenderer.stripFormatting("§a§lGoosBoards§r"));
        assertEquals("Mixed Styles", TextRenderer.stripFormatting("<gradient:#38BDF8:#818CF8>&lMixed Styles</gradient>"));
    }

    @Test
    @DisplayName("findFittingFontSize binary searches optimal font size")
    void testFindFittingFontSize() {
        int defaultSize = TextRenderer.findFittingFontSize("", "SansSerif", 100, 50);
        assertEquals(16, defaultSize);

        int smallBoxSize = TextRenderer.findFittingFontSize("TEST STRING", "SansSerif", 50, 20);
        int largeBoxSize = TextRenderer.findFittingFontSize("TEST STRING", "SansSerif", 500, 200);

        assertTrue(smallBoxSize >= 1, "Small box size must be at least 1");
        assertTrue(largeBoxSize > smallBoxSize, "Large box must fit a larger font size than small box");
        assertTrue(largeBoxSize <= 512, "Font size must not exceed 512pt");
    }

    @Test
    @DisplayName("collectFragments extracts styled text fragments from Adventure Component")
    void testCollectFragments() {
        Component root = Component.text("Hello ", NamedTextColor.RED)
                .append(Component.text("World", NamedTextColor.BLUE, TextDecoration.BOLD));

        List<TextRenderer.TextFragment> fragments = new ArrayList<>();
        TextRenderer.collectFragments(root, Color.WHITE, false, false, fragments);

        assertEquals(2, fragments.size());
        assertEquals("Hello ", fragments.get(0).text());
        assertFalse(fragments.get(0).bold());

        assertEquals("World", fragments.get(1).text());
        assertTrue(fragments.get(1).bold());
    }

    @Test
    @DisplayName("renderText renders without clip crashes and blits to canvas")
    void testRenderTextAndBlit() {
        BufferedImage img = TextRenderer.renderText(
                "Welcome to GoosBoards!",
                null,
                "SansSerif",
                24,
                Color.YELLOW,
                256,
                64,
                "center",
                "center",
                1.0,
                0xFF000000
        );

        assertNotNull(img, "Rendered image must not be null");
        assertEquals(256, img.getWidth());
        assertEquals(64, img.getHeight());

        CanvasBuffer canvas = new CanvasBufferImpl(2, 2); // 256x256 px
        assertDoesNotThrow(() -> TextRenderer.blitToCanvas(img, canvas, 0, 0));
    }

    @Test
    @DisplayName("renderText with explicit newlines preserves multiple lines and respects container bounds")
    void testRenderTextWithNewlines() {
        String input = "INTERACTIVE RANKS\n/ SHOP";
        Component comp = MiniMessage.miniMessage()
                .deserialize("<gradient:#38BDF8:#10B981><b>INTERACTIVE RANKS\n/ SHOP</b></gradient>");

        BufferedImage img = TextRenderer.renderText(
                input,
                comp,
                "SansSerif",
                22,
                Color.WHITE,
                397,
                54,
                "left",
                "center",
                1.5,
                0xFF000000
        );

        assertNotNull(img);
        assertEquals(397, img.getWidth());
        assertEquals(54, img.getHeight());

        // Verify pixels were drawn on both top half (Line 1) and bottom half (Line 2)
        boolean hasTopPixels = false;
        boolean hasBottomPixels = false;
        for (int y = 0; y < 27; y++) {
            for (int x = 0; x < 397; x++) {
                if ((img.getRGB(x, y) >>> 24) > 64) {
                    hasTopPixels = true;
                    break;
                }
            }
            if (hasTopPixels) break;
        }
        for (int y = 27; y < 54; y++) {
            for (int x = 0; x < 397; x++) {
                if ((img.getRGB(x, y) >>> 24) > 64) {
                    hasBottomPixels = true;
                    break;
                }
            }
            if (hasBottomPixels) break;
        }

        assertTrue(hasTopPixels, "Line 1 (INTERACTIVE RANKS) must render pixels in upper half");
        assertTrue(hasBottomPixels, "Line 2 (/ SHOP) must render pixels in lower half without being dropped");
    }
}