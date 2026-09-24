package me.goosbanny.goosboards.scene.parser;

import me.goosbanny.goosboards.scene.BoardConfig;
import me.goosbanny.goosboards.scene.Rect;
import me.goosbanny.goosboards.scene.component.UIComponent;
import me.goosbanny.goosboards.scene.component.container.BackgroundComponent;
import me.goosbanny.goosboards.scene.exception.BoardParseException;
import me.goosbanny.goosboards.scene.exception.LayoutDepthException;
import me.goosbanny.goosboards.scene.layout.LayoutEngine;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SceneLayoutParserTest {

    @Test
    @DisplayName("Test 4.1 — Bounding box: absolute position + fixed size")
    void testBoundingBoxAbsolutePositionAndFixedSize() {
        BackgroundComponent comp = new BackgroundComponent("panel", false);
        comp.setPositionStr("10 20");
        comp.setSizeStr("100 50");

        LayoutEngine.layout(comp, 512, 384);

        assertEquals(new Rect(10, 20, 100, 50), comp.getBounds());
    }

    @Test
    @DisplayName("Test 4.2 — Bounding box: 'max max' size")
    void testBoundingBoxMaxMaxSize() {
        BackgroundComponent comp = new BackgroundComponent("bg", false);
        comp.setPositionStr("0 0");
        comp.setSizeStr("max max");

        LayoutEngine.layout(comp, 512, 384);

        assertEquals(new Rect(0, 0, 512, 384), comp.getBounds());
    }

    @Test
    @DisplayName("Test 4.3 — Bounding box: percentage size")
    void testBoundingBoxPercentageSize() {
        BackgroundComponent parent = new BackgroundComponent("parent", false);
        parent.setPositionStr("0 0");
        parent.setSizeStr("512 384");

        BackgroundComponent child = new BackgroundComponent("child", false);
        child.setPositionStr("0 0");
        child.setSizeStr("50% 25%");
        parent.addChild(child);

        LayoutEngine.layout(parent, 512, 384);

        assertEquals(256, child.getBounds().width(), "50% of 512 is 256");
        assertEquals(96, child.getBounds().height(), "25% of 384 is 96");
    }

    @Test
    @DisplayName("Test 4.4 — Nested percentage layout")
    void testNestedPercentageLayout() {
        BackgroundComponent parent = new BackgroundComponent("parent", false);
        parent.setPositionStr("0 0");
        parent.setSizeStr("max max");

        BackgroundComponent child = new BackgroundComponent("child", false);
        child.setPositionStr("10 10");
        child.setSizeStr("50% 50%");
        parent.addChild(child);

        LayoutEngine.layout(parent, 512, 384);

        assertEquals(new Rect(0, 0, 512, 384), parent.getBounds());
        assertEquals(new Rect(10, 10, 256, 192), child.getBounds(),
                "Child bounds at (10,10) with 50% 50% must equal Rect(10, 10, 256, 192)");
    }

    @Test
    @DisplayName("Test 4.5 — Recursion depth limit (17 levels throws LayoutDepthException)")
    void testRecursionDepthLimit() {
        StringBuilder yaml = new StringBuilder("scenes:\n  deep-scene:\n");
        String indent = "    ";
        yaml.append(indent).append("root:\n");
        yaml.append(indent).append("  type: background\n");

        for (int i = 1; i <= 17; i++) {
            indent += "  ";
            yaml.append(indent).append("children:\n");
            indent += "  ";
            yaml.append(indent).append("node_").append(i).append(":\n");
            yaml.append(indent).append("  type: background\n");
        }

        BoardYamlParser parser = new BoardYamlParser();
        assertThrows(LayoutDepthException.class, () -> parser.parse(yaml.toString()),
                "Nesting beyond 16 levels must throw LayoutDepthException");
    }

    @Test
    @DisplayName("Test 4.6 — Malformed YAML (parse error wraps in BoardParseException)")
    void testMalformedYamlThrowsBoardParseException() {
        String invalidYaml = """
                scenes:
                  broken:
                    invalid: [unclosed bracket
                    tab: \t\t
                """;

        BoardYamlParser parser = new BoardYamlParser();
        assertThrows(BoardParseException.class, () -> parser.parse(invalidYaml),
                "Malformed YAML must cleanly throw BoardParseException");
    }

    @Test
    @DisplayName("Test 4.7 — Unknown component type (logged and skipped, no exception)")
    void testUnknownComponentTypeSkipped() {
        String yaml = """
                scenes:
                  main:
                    widget:
                      type: unknown_widget
                      position: 10 10
                      size: 50 50
                """;

        BoardYamlParser parser = new BoardYamlParser();
        BoardConfig config = parser.parse(yaml);

        assertNotNull(config);
        assertNotNull(config.scenes().get("main"));
        assertTrue(config.scenes().get("main").components().isEmpty(),
                "Unknown widget type must be skipped without crashing");
    }

    @Test
    @DisplayName("Test 4.8 — Context-independent classification (static text)")
    void testContextIndependentClassification() {
        String yaml = """
                scenes:
                  main:
                    header:
                      type: pixel_text
                      text: "HELLO WORLD"
                """;

        BoardYamlParser parser = new BoardYamlParser();
        BoardConfig config = parser.parse(yaml);

        UIComponent comp = config.scenes().get("main").components().get(0);
        assertFalse(comp.isContextDependent(), "Static text without placeholders must be context-independent");
    }

    @Test
    @DisplayName("Test 4.9 — Context-dependent classification (placeholder)")
    void testContextDependentClassification() {
        String yaml = """
                scenes:
                  main:
                    player_name:
                      type: pixel_text
                      text: "%player_name%"
                """;

        BoardYamlParser parser = new BoardYamlParser();
        BoardConfig config = parser.parse(yaml);

        UIComponent comp = config.scenes().get("main").components().get(0);
        assertTrue(comp.isContextDependent(), "Component with %placeholder% must be context-dependent");
    }

    @Test
    @DisplayName("Test 4.10 — Context-dependent propagation (child has placeholder)")
    void testContextDependentPropagationFromChild() {
        String yaml = """
                scenes:
                  main:
                    card:
                      type: background
                      children:
                        tps:
                          type: pixel_text
                          text: "%server_tps%"
                """;

        BoardYamlParser parser = new BoardYamlParser();
        BoardConfig config = parser.parse(yaml);

        UIComponent parent = config.scenes().get("main").components().get(0);
        assertTrue(parent.isContextDependent(), "Parent must inherit context-dependency from child");
        assertTrue(parent.getChildren().get(0).isContextDependent(), "Child must be context-dependent");
    }

    @Test
    @DisplayName("Test 4.11 — Alignment: center horizontal")
    void testAlignmentCenterHorizontal() {
        BackgroundComponent parent = new BackgroundComponent("parent", false);
        parent.setPositionStr("0 0");
        parent.setSizeStr("200 100");

        BackgroundComponent child = new BackgroundComponent("child", false);
        child.setAlignment("center");
        child.setSizeStr("100 50");
        parent.addChild(child);

        LayoutEngine.layout(parent, 512, 384);

        // Parent x = 0, width = 200. Child width = 100. Centered x = 0 + (200 - 100) / 2 = 50.
        assertEquals(50, child.getBounds().x(), "Component centered in 200px parent must have x = 50");
    }

    @Test
    @DisplayName("Test 4.12 — full board_example.yml parses without exception")
    void testFullBoardExampleParsesSuccessfully() {
        File boardFile = new File("src/test/resources/board_example.yml");
        if (!boardFile.exists()) {
            boardFile = new File("specs/board_example.yml");
        }
        assertTrue(boardFile.exists(), "board_example.yml must exist on disk");

        BoardYamlParser parser = new BoardYamlParser();
        BoardConfig config = parser.parse(boardFile);

        assertNotNull(config, "Parsed BoardConfig must not be null");
        assertEquals(4, config.scenes().size(), "board_example.yml must contain exactly 4 scenes");

        assertTrue(config.scenes().containsKey("main-hub"), "Must contain main-hub scene");
        assertTrue(config.scenes().containsKey("shop-menu"), "Must contain shop-menu scene");
        assertTrue(config.scenes().containsKey("media-showcase"), "Must contain media-showcase scene");
        assertTrue(config.scenes().containsKey("staff-dashboard"), "Must contain staff-dashboard scene");

        // Verify no component in any scene tree is null
        for (BoardConfig.SceneDefinition scene : config.scenes().values()) {
            assertNotNull(scene.id(), "Scene id must not be null");
            assertNotNull(scene.components(), "Scene components must not be null");
            for (UIComponent rootComp : scene.components()) {
                assertNotNull(rootComp, "Root component must not be null");
                assertNotNull(rootComp.getBounds(), "Root component bounds must not be null");
                assertComponentTreeNotNull(rootComp);
            }
        }
    }

    private void assertComponentTreeNotNull(UIComponent component) {
        assertNotNull(component, "Component in tree must not be null");
        assertNotNull(component.getBounds(), "Component bounds must not be null");
        for (UIComponent child : component.getChildren()) {
            assertNotNull(child, "Child component must not be null");
            assertEquals(component, child.getParent(), "Child's parent reference must point to parent");
            assertComponentTreeNotNull(child);
        }
    }
}
