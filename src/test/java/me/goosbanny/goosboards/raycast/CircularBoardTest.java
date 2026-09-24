package me.goosbanny.goosboards.raycast;

import me.goosbanny.goosboards.display.DisplayPlane;
import me.goosbanny.goosboards.render.palette.ColorUtils;

import me.goosbanny.goosboards.render.BoardRenderEngine;
import me.goosbanny.goosboards.render.buffer.CanvasBuffer;
import me.goosbanny.goosboards.render.buffer.CanvasBufferImpl;
import me.goosbanny.goosboards.render.palette.impl.PaletteQuantizerImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CircularBoardTest {

    @Test
    @DisplayName("DisplayRaycaster accurately hits inside circular board and misses outside circle corners")
    void testCircularBoardRaycast() {
        // Create 2x2 blocks circular board facing SOUTH (normal (0,0,1))
        // Top-left at (-1, 1, 0), rightUnit = (1, 0, 0), downUnit = (0, -1, 0)
        // Board spans X: [-1, 1], Y: [1, -1], Z: 0
        // Center is at (0, 0, 0), radius is 1.0 block
        DisplayPlane plane = new DisplayPlane(
                UUID.randomUUID(),
                "world",
                new Vector3d(-1, 1, 0),
                2.0,
                2.0,
                new Vector3d(1, 0, 0),
                new Vector3d(0, -1, 0),
                32.0,
                16.0,
                false,
                false,
                "aqua",
                "border",
                "circle",
                0
        );
        assertTrue(plane.isCircular());

        MutableRaycastHit hit = new MutableRaycastHit();

        // 1. Ray aiming straight at the center (0, 0, 0) from (0, 0, 5) -> must hit
        DisplayRaycaster.intersect(plane, 0, 0, 5, 0, 0, -1, 10.0, hit);
        assertTrue(hit.hit, "Ray pointing at center of circular board must hit");
        assertEquals(128, hit.pixelX, 2); // Center of 256x256
        assertEquals(128, hit.pixelY, 2);

        // 2. Ray aiming near top-left corner (-0.8, 0.8, 0) -> outside circle (dist = sqrt(0.64 + 0.64) = 1.13 > 1.0)
        Vector3d cornerDir = new Vector3d(-0.8, 0.8, -5).multiply(1.0 / Math.sqrt(0.64 + 0.64 + 25));
        DisplayRaycaster.intersect(plane, 0, 0, 5, cornerDir.x(), cornerDir.y(), cornerDir.z(), 10.0, hit);
        assertFalse(hit.hit, "Ray pointing at corner outside circular radius must be rejected");

        // 3. Ray aiming inside circle at (0.5, 0, 0) -> dist = 0.5 <= 1.0 -> must hit
        Vector3d insideDir = new Vector3d(0.5, 0, -5).multiply(1.0 / Math.sqrt(0.25 + 25));
        DisplayRaycaster.intersect(plane, 0, 0, 5, insideDir.x(), insideDir.y(), insideDir.z(), 10.0, hit);
        assertTrue(hit.hit, "Ray pointing inside circular radius must hit");
    }

    @Test
    @DisplayName("BoardRenderEngine masks pixels outside circular board to 0 (transparent)")
    void testCircularBoardMasking() {
        CanvasBuffer canvas = new CanvasBufferImpl(2, 2); // 2x2 tiles = 256x256 pixels

        // Fill entire canvas with white pixels (0xFFFFFFFF)
        byte whiteIdx = PaletteQuantizerImpl.match(0xFFFFFFFF);
        for (int y = 0; y < 256; y++) {
            for (int x = 0; x < 256; x++) {
                canvas.setPixel(x, y, whiteIdx);
            }
        }

        // Render circular board outline with red glow
        BoardRenderEngine.renderBoardOutline(canvas, "#FF0000", 2, "circle", 0);

        // Corner (0,0) is outside the circle -> must be masked to 0 (transparent)
        assertEquals((byte) 0, canvas.getPixel(0, 0), "Corner pixel outside circle must be masked to 0 (transparent)");
        assertEquals((byte) 0, canvas.getPixel(255, 0), "Corner pixel outside circle must be masked to 0 (transparent)");
        assertEquals((byte) 0, canvas.getPixel(0, 255), "Corner pixel outside circle must be masked to 0 (transparent)");
        assertEquals((byte) 0, canvas.getPixel(255, 255), "Corner pixel outside circle must be masked to 0 (transparent)");

        // Center (128, 128) is inside the circle -> must remain white
        assertEquals(whiteIdx, canvas.getPixel(128, 128), "Center inside circle must retain its fill");

        // Border pixel at (128, 1) near top edge of circle -> must have the red outline
        byte redIdx = PaletteQuantizerImpl.match(0xFFFF0000);
        assertEquals(redIdx, canvas.getPixel(128, 0), "Perimeter of circular board must have red outline");
    }

    @Test
    @DisplayName("BoardRenderEngine masks pixels outside rounded outline to transparent (0) and hover outline overrides base outline")
    void testRoundedBoardMaskingAndHoverOverride() {
        CanvasBuffer canvas = new CanvasBufferImpl(2, 2); // 256x256 pixels

        // Fill canvas with content color (slate #0F172A)
        byte slateIdx = PaletteQuantizerImpl.match(0xFF0F172A);
        for (int y = 0; y < 256; y++) {
            for (int x = 0; x < 256; x++) {
                canvas.setPixel(x, y, slateIdx);
            }
        }

        // 1. Render base red outline with rounded ends (corner-radius: 16)
        BoardRenderEngine.maskOutsideBoard(canvas, "rounded", 16);
        BoardRenderEngine.renderBoardOutline(canvas, "red", 2, "rounded", 16);

        // Outside rounded corners must be transparent (0)
        assertEquals((byte) 0, canvas.getPixel(0, 0), "Corner pixel (0,0) outside rounded outline must be transparent (0)");
        assertEquals((byte) 0, canvas.getPixel(255, 0), "Corner pixel (255,0) outside rounded outline must be transparent (0)");
        assertEquals((byte) 0, canvas.getPixel(0, 255), "Corner pixel (0,255) outside rounded outline must be transparent (0)");
        assertEquals((byte) 0, canvas.getPixel(255, 255), "Corner pixel (255,255) outside rounded outline must be transparent (0)");

        // Inside content (e.g. at (128, 128)) must remain slate
        assertEquals(slateIdx, canvas.getPixel(128, 128), "Interior of rounded board must retain underlying content");

        // Edge at (128, 0) must have the red outline
        byte redIdx = PaletteQuantizerImpl.match(ColorUtils.parseColor("red", 0));
        assertEquals(redIdx, canvas.getPixel(128, 0), "Board edge must have red outline by default");

        // 2. Now simulate hover: render white outline over the board
        BoardRenderEngine.renderBoardOutline(canvas, "white", 2, "rounded", 16);

        // Edge at (128, 0) must now be white, cleanly overriding red
        byte whiteIdx = PaletteQuantizerImpl.match(0xFFFFFFFF);
        assertEquals(whiteIdx, canvas.getPixel(128, 0), "Hover outline must override base outline cleanly with white");
        // Interior remains slate
        assertEquals(slateIdx, canvas.getPixel(128, 128), "Interior must remain unchanged by hover outline");
        // Outside corners remain transparent (0)
        assertEquals((byte) 0, canvas.getPixel(0, 0), "Outside corners must remain transparent (0)");
    }

    @Test
    @DisplayName("CanvasBuffer.applyCircularMask sets pixels outside radius to 0 (transparent) and preserves interior")
    void testCanvasBufferApplyCircularMask() {
        CanvasBuffer canvas = new CanvasBufferImpl(2, 2); // 256x256
        byte fill = (byte) 34;
        for (int y = 0; y < 256; y++) {
            for (int x = 0; x < 256; x++) {
                canvas.setPixel(x, y, fill);
            }
        }

        canvas.applyCircularMask();

        // (0, 0) is outside the circle of radius 128 centered at (128, 128)
        assertEquals((byte) 0, canvas.getPixel(0, 0));
        assertEquals((byte) 0, canvas.getPixel(255, 0));
        assertEquals((byte) 0, canvas.getPixel(0, 255));
        assertEquals((byte) 0, canvas.getPixel(255, 255));

        // (128, 128) is the center -> retains fill
        assertEquals(fill, canvas.getPixel(128, 128));
        // (128, 64) is at dist=64 <= 128 -> retains fill
        assertEquals(fill, canvas.getPixel(128, 64));
    }

    @Test
    @DisplayName("BoardYamlParser parses mask: circle property as circular display shape")
    void testBoardYamlParserMaskCircle() {
        String yaml = """
                displays:
                  radar:
                    world: world
                    width: 2
                    height: 2
                    top-left:
                      x: 0.0
                      y: 64.0
                      z: 0.0
                    direction: north
                    mask: circle
                """;
        org.bukkit.configuration.file.YamlConfiguration config = new org.bukkit.configuration.file.YamlConfiguration();
        try {
            config.loadFromString(yaml);
            me.goosbanny.goosboards.scene.parser.BoardYamlParser parser = new me.goosbanny.goosboards.scene.parser.BoardYamlParser();
            java.util.Map<String, me.goosbanny.goosboards.scene.BoardConfig.DisplayDefinition> displays =
                    parser.parse(config).displays();
            assertTrue(displays.containsKey("radar"));
            assertEquals("circle", displays.get("radar").shape());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}