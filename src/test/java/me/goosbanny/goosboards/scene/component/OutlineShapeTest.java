package me.goosbanny.goosboards.scene.component;

import me.goosbanny.goosboards.scene.BoardConfig;
import me.goosbanny.goosboards.scene.Rect;
import me.goosbanny.goosboards.scene.RenderContext;
import me.goosbanny.goosboards.scene.component.container.BackgroundComponent;
import me.goosbanny.goosboards.scene.component.interactive.ButtonComponent;
import me.goosbanny.goosboards.scene.parser.BoardYamlParser;

import me.goosbanny.goosboards.render.buffer.CanvasBuffer;
import me.goosbanny.goosboards.render.buffer.CanvasBufferImpl;
import me.goosbanny.goosboards.render.palette.impl.PaletteQuantizerImpl;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OutlineShapeTest {

    @Test
    @DisplayName("YAML parser correctly reads shape and radius on background and button components")
    void testShapeYamlParsing() throws Exception {
        String yaml = """
                scenes:
                  default:
                    circ-bg:
                      type: background
                      position: "0 0"
                      size: "100 100"
                      shape: "circle"
                      radius: 50
                      color: "#FF0000"
                      outline-color: "#00FF00"
                      outline-width: 2
                    round-btn:
                      type: button
                      position: "120 0"
                      size: "80 40"
                      shape: "rounded"
                      radius: 12
                      color: "#0000FF"
                """;

        YamlConfiguration config = new YamlConfiguration();
        config.load(new StringReader(yaml));

        BoardYamlParser parser = new BoardYamlParser();
        var scenes = parser.parseScenes(config.getConfigurationSection("scenes"), 256, 256);
        assertNotNull(scenes);
        BoardConfig.SceneDefinition def = scenes.get("default");
        assertNotNull(def);
        assertEquals(2, def.components().size());

        BackgroundComponent bg = (BackgroundComponent) def.components().get(0);
        assertEquals("circle", bg.getShape());
        assertTrue(bg.isCircular());
        assertEquals(50, bg.getRadius());

        ButtonComponent btn = (ButtonComponent) def.components().get(1);
        assertEquals("rounded", btn.getShape());
        assertTrue(btn.isRounded());
        assertEquals(12, btn.getRadius());
    }

    @Test
    @DisplayName("Circular BackgroundComponent accurately tests containsPixel within circle radius")
    void testCircularContainsPixel() {
        BackgroundComponent bg = new BackgroundComponent("circ", false);
        bg.setShape("circle");
        bg.setBounds(new Rect(0, 0, 100, 100)); // center at (50, 50), radius 50

        assertTrue(bg.containsPixel(50, 50), "Center must be inside");
        assertTrue(bg.containsPixel(50, 10), "Top point inside radius");
        assertTrue(bg.containsPixel(50, 90), "Bottom point inside radius");
        assertTrue(bg.containsPixel(10, 50), "Left point inside radius");
        assertTrue(bg.containsPixel(90, 50), "Right point inside radius");

        assertFalse(bg.containsPixel(0, 0), "Corner (0,0) is outside circular radius");
        assertFalse(bg.containsPixel(99, 0), "Corner (99,0) is outside circular radius");
        assertFalse(bg.containsPixel(0, 99), "Corner (0,99) is outside circular radius");
        assertFalse(bg.containsPixel(99, 99), "Corner (99,99) is outside circular radius");
    }

    @Test
    @DisplayName("Rounded BackgroundComponent accurately tests containsPixel with corner radius")
    void testRoundedContainsPixel() {
        BackgroundComponent bg = new BackgroundComponent("round", false);
        bg.setShape("rounded");
        bg.setRadius(20);
        bg.setBounds(new Rect(0, 0, 100, 100));

        assertTrue(bg.containsPixel(50, 50), "Center is inside");
        assertTrue(bg.containsPixel(25, 25), "Inner corner is inside");
        assertFalse(bg.containsPixel(2, 2), "Outer corner (2,2) is outside corner radius of 20");
    }

    @Test
    @DisplayName("Top outline strictly overrides underlying board outline without color mixing or terracotta tint")
    void testOutlineOverridesCleanly() {
        CanvasBuffer canvas = new CanvasBufferImpl(1, 1); // 1x1 tile = 128x128 pixels

        // Pre-paint a blue background outline (0xFF0000FF)
        byte blueIdx = PaletteQuantizerImpl.match(0xFF0000FF);
        for (int x = 0; x < 128; x++) {
            canvas.setPixel(x, 0, blueIdx);
            canvas.setPixel(x, 1, blueIdx);
        }

        // Render a button with a solid pure red outline (0xFFFF0000) overlapping the top edge
        ButtonComponent btn = new ButtonComponent("btn", true);
        btn.setBounds(new Rect(0, 0, 64, 32));
        btn.setOutlineColor(0xFFFF0000);
        btn.setOutlineWidth(2.0);
        btn.render(canvas, RenderContext.empty());

        byte expectedRedIdx = PaletteQuantizerImpl.match(0xFFFF0000);
        // Pixels on the top edge covered by the button should be pure red, NOT blue or terracotta orange
        for (int x = 0; x < 64; x++) {
            byte pixel = canvas.getPixel(x, 0);
            assertEquals(expectedRedIdx, pixel, "Overlapping top outline must strictly paint pure red");
        }

        // Pixels beyond x=64 should remain blue
        for (int x = 65; x < 128; x++) {
            byte pixel = canvas.getPixel(x, 0);
            assertEquals(blueIdx, pixel, "Uncovered area must retain original blue outline");
        }
    }

    @Test
    @DisplayName("Button and Background components dynamically transition outline color and width on hover")
    void testDynamicHoverOutlineTransitions() {
        CanvasBuffer canvas = new CanvasBufferImpl(1, 1);
        ButtonComponent btn = new ButtonComponent("btn-hover", true);
        btn.setBounds(new Rect(10, 10, 50, 30));
        btn.setOutlineColor(0xFFFF0000); // Red
        btn.setOutlineWidth(1.0);
        btn.setOnHoverOutlineColor(0xFF00FF00); // Green
        btn.setOnHoverOutlineWidth(3.0);

        // 1. Idle render
        btn.render(canvas, RenderContext.empty());
        byte redIdx = PaletteQuantizerImpl.match(0xFFFF0000);
        assertEquals(redIdx, canvas.getPixel(10, 10), "Idle outline must be red at (10, 10)");
        // With width 1.0, row 10 is outline, but row 11 should be fill
        byte greenIdx = PaletteQuantizerImpl.match(0xFF00FF00);
        assertFalse(canvas.getPixel(10, 12) == greenIdx, "Row 12 should not be green");

        // 2. Hover render
        RenderContext hoverCtx = RenderContext.of(null, Set.of("btn-hover"), true, 0, 0);
        btn.render(canvas, hoverCtx);
        assertEquals(greenIdx, canvas.getPixel(10, 10), "Hovered outline must transition to green at (10, 10)");
        assertEquals(greenIdx, canvas.getPixel(10, 11), "Hovered outline with width 3.0 must cover row 11");
        assertEquals(greenIdx, canvas.getPixel(10, 12), "Hovered outline with width 3.0 must cover row 12");
    }

    @Test
    @DisplayName("ImageComponent and TextComponent render bounds outline and hover transitions")
    void testImageAndTextComponentOutlines() {
        CanvasBuffer canvas = new CanvasBufferImpl(1, 1);

        // ImageComponent outline
        me.goosbanny.goosboards.scene.component.visual.ImageComponent img =
                new me.goosbanny.goosboards.scene.component.visual.ImageComponent("img1", false);
        img.setBounds(new Rect(0, 0, 40, 40));
        img.setOutlineColor(0xFF00FFFF); // Cyan
        img.setOutlineWidth(2.0);
        img.setOnHoverOutlineColor(0xFFFF00FF); // Magenta
        img.setOnHoverOutlineWidth(4.0);

        assertTrue(img.hasOutline());

        // Idle outline
        img.renderBoundsOutline(canvas, RenderContext.empty());
        byte cyanIdx = PaletteQuantizerImpl.match(0xFF00FFFF);
        assertEquals(cyanIdx, canvas.getPixel(0, 0));
        assertEquals(cyanIdx, canvas.getPixel(1, 1));

        // Hover outline
        RenderContext hoverCtx = RenderContext.of(null, Set.of("img1"), true, 0, 0);
        img.renderBoundsOutline(canvas, hoverCtx);
        byte magentaIdx = PaletteQuantizerImpl.match(0xFFFF00FF);
        assertEquals(magentaIdx, canvas.getPixel(0, 0));
        assertEquals(magentaIdx, canvas.getPixel(3, 3), "Width 4.0 covers pixel (3,3)");

        // TextComponent outline
        me.goosbanny.goosboards.scene.component.text.TextComponent txt =
                new me.goosbanny.goosboards.scene.component.text.TextComponent("txt1", false);
        txt.setBounds(new Rect(50, 50, 40, 20));
        txt.setOutlineColor(0xFFFFFF00); // Yellow
        txt.setOutlineWidth(1.0);
        txt.setOnHoverOutlineColor(0xFFFFFFFF); // White
        txt.setOnHoverOutlineWidth(2.0);

        assertTrue(txt.hasOutline());
        txt.renderBoundsOutline(canvas, RenderContext.empty());
        byte yellowIdx = PaletteQuantizerImpl.match(0xFFFFFF00);
        assertEquals(yellowIdx, canvas.getPixel(50, 50));

        RenderContext txtHoverCtx = RenderContext.of(null, Set.of("txt1"), true, 0, 0);
        txt.renderBoundsOutline(canvas, txtHoverCtx);
        byte whiteIdx = PaletteQuantizerImpl.match(0xFFFFFFFF);
        assertEquals(whiteIdx, canvas.getPixel(50, 50));
        assertEquals(whiteIdx, canvas.getPixel(51, 51));
    }
}