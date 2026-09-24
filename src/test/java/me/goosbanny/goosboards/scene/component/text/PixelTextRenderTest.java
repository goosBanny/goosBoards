package me.goosbanny.goosboards.scene.component.text;

import me.goosbanny.goosboards.render.buffer.CanvasBuffer;
import me.goosbanny.goosboards.render.buffer.CanvasBufferImpl;
import me.goosbanny.goosboards.render.font.MinecraftFontRenderer;
import me.goosbanny.goosboards.render.palette.PaletteQuantizer;
import me.goosbanny.goosboards.render.palette.impl.PaletteQuantizerImpl;

import me.goosbanny.goosboards.scene.layout.LayoutEngine;
import me.goosbanny.goosboards.scene.component.text.PixelTextComponent;
import me.goosbanny.goosboards.scene.Rect;
import me.goosbanny.goosboards.scene.RenderContext;
import me.goosbanny.goosboards.scene.component.container.ScrollPaneComponent;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PixelTextRenderTest {

    @Test
    @DisplayName("MinecraftFontRenderer parses color codes and hex codes correctly")
    void testParseSpans() {
        String input = "&aGreen &bAqua &lBold &#FF5500Orange";
        List<MinecraftFontRenderer.TextSpan> spans = MinecraftFontRenderer.parseSpans(input, 0xFFFFFFFF);
        assertFalse(spans.isEmpty());
        assertEquals("Green ", spans.get(0).text());
        assertEquals(0xFF55FF55, spans.get(0).color());
        assertEquals("Aqua ", spans.get(1).text());
        assertEquals(0xFF55FFFF, spans.get(1).color());
    }

    @Test
    @DisplayName("PixelTextComponent renders non-zero pixels onto CanvasBuffer")
    void testPixelTextRendersOnCanvas() {
        CanvasBufferImpl canvas = new CanvasBufferImpl(2, 2); // 256x256 px
        PixelTextComponent ptc = new PixelTextComponent("test-ptc", false);
        ptc.setBounds(new Rect(10, 10, 100, 20));
        ptc.setText("&a&lTEST BUTTON");
        ptc.setShadow(true);

        ptc.render(canvas, RenderContext.empty());

        // Count non-zero pixels rendered into the bounding box
        int nonZero = 0;
        for (int y = 10; y < 30; y++) {
            for (int x = 10; x < 110; x++) {
                if (canvas.getPixel(x, y) != 0) {
                    nonZero++;
                }
            }
        }
        assertTrue(nonZero > 0, "PixelTextComponent must render non-zero pixels onto canvas");
    }

    @Test
    @DisplayName("RenderContext resolves player name and ping with fallback")
    void testRenderContextFallback() {
        Player mockPlayer = mock(Player.class);
        when(mockPlayer.getName()).thenReturn("goosBanny");
        when(mockPlayer.getPing()).thenReturn(42);
        when(mockPlayer.getUniqueId()).thenReturn(UUID.randomUUID());

        RenderContext ctx = RenderContext.of(mockPlayer);
        String resolved = ctx.resolve("Hello %player_name%, your ping is %player_ping%ms!");
        assertEquals("Hello goosBanny, your ping is 42ms!", resolved);
    }

    @Test
    @DisplayName("ScrollPane children are laid out relative to (0, 0) inside inner buffer")
    void testScrollPaneChildLayout() {
        ScrollPaneComponent sp = new ScrollPaneComponent("scroll", false);
        sp.setPositionStr("50 50");
        sp.setSizeStr("200 150");
        sp.setScrollInnerWidth(200);
        sp.setScrollInnerHeight(400);

        PixelTextComponent item1 = new PixelTextComponent("item1", false);
        item1.setPositionStr("10 20");
        item1.setSizeStr("180 30");
        sp.addChild(item1);

        LayoutEngine.layout(sp, 512, 512);

        assertEquals(50, sp.getBounds().x());
        assertEquals(50, sp.getBounds().y());

        // Child bounds must be 0-based within scroll inner buffer: x=10, y=20 (NOT 50+10=60)
        assertEquals(10, item1.getBounds().x(), "Child X must be relative to scroll pane inner buffer");
        assertEquals(20, item1.getBounds().y(), "Child Y must be relative to scroll pane inner buffer");
    }

    @Test
    @DisplayName("PaletteQuantizer Redmean color distance distinguishes skin and subtle tones")
    void testRedmeanColorMatching() {
        // Pure black
        byte black = PaletteQuantizerImpl.match(0xFF000000);
        assertNotEquals(0, black);

        // Pure white
        byte white = PaletteQuantizerImpl.match(0xFFFFFFFF);
        assertNotEquals(0, white);
        assertNotEquals(black, white);

        // Steve skin flesh tone (~#C68A65)
        byte skin = PaletteQuantizerImpl.match(0xFFC68A65);
        assertNotEquals(0, skin);
        assertNotEquals(black, skin);
        assertNotEquals(white, skin);
    }
}