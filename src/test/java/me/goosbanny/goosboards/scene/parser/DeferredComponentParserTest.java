package me.goosbanny.goosboards.scene.parser;

import me.goosbanny.goosboards.scene.BoardConfig;
import me.goosbanny.goosboards.scene.component.UIComponent;
import me.goosbanny.goosboards.scene.component.interactive.ActionListenerComponent;
import me.goosbanny.goosboards.scene.component.misc.DelayedTimerComponent;
import me.goosbanny.goosboards.scene.component.text.TextComponent;
import me.goosbanny.goosboards.scene.component.visual.GifComponent;
import me.goosbanny.goosboards.scene.component.visual.Head2DComponent;
import me.goosbanny.goosboards.scene.component.visual.ImageComponent;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies that all formerly-deferred component types are now parsed and
 * registered correctly by BoardYamlParser instead of being skipped.
 */
class DeferredComponentParserTest {

    private static final BoardYamlParser PARSER = new BoardYamlParser();

    // ── head_2d ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("head_2d with explicit skin parses to Head2DComponent with correct skin")
    void testHead2DWithSkin() {
        String yaml = """
                displays:
                  main:
                    world: world
                    width: 2
                    height: 2
                scenes:
                  default:
                    avatar:
                      type: head_2d
                      skin: Steve
                      position: "0 0"
                      size: "64 64"
                """;

        BoardConfig config = PARSER.parse(yaml);
        List<UIComponent> comps = config.scenes().get("default").components();
        assertEquals(1, comps.size(), "One component expected");
        assertInstanceOf(Head2DComponent.class, comps.get(0));
        assertEquals("Steve", ((Head2DComponent) comps.get(0)).getSkin());
    }

    @Test
    @DisplayName("head_2d with player key falls back correctly")
    void testHead2DWithPlayerKey() {
        String yaml = """
                scenes:
                  default:
                    head:
                      type: head_2d
                      player: "%player_name%"
                      position: "0 0"
                      size: "32 32"
                """;

        BoardConfig config = PARSER.parse(yaml);
        UIComponent comp = config.scenes().get("default").components().get(0);
        assertInstanceOf(Head2DComponent.class, comp);
        assertEquals("%player_name%", ((Head2DComponent) comp).getSkin());
    }

    @Test
    @DisplayName("head_2d with no skin key uses default placeholder")
    void testHead2DDefaultSkin() {
        String yaml = """
                scenes:
                  default:
                    head:
                      type: head_2d
                      position: "0 0"
                      size: "32 32"
                """;

        BoardConfig config = PARSER.parse(yaml);
        UIComponent comp = config.scenes().get("default").components().get(0);
        assertInstanceOf(Head2DComponent.class, comp);
        assertEquals("%player_name%", ((Head2DComponent) comp).getSkin());
    }

    // ── auto_font_size_text ───────────────────────────────────────────────────

    @Test
    @DisplayName("auto_font_size_text parses to TextComponent with font-size=0 (auto-fit signal)")
    void testAutoFontSizeText() {
        String yaml = """
                scenes:
                  default:
                    welcome:
                      type: auto_font_size_text
                      text: "Hello World!"
                      font: "Arial"
                      position: "0 0"
                      size: "200 50"
                """;

        BoardConfig config = PARSER.parse(yaml);
        UIComponent comp = config.scenes().get("default").components().get(0);
        assertInstanceOf(TextComponent.class, comp);
        TextComponent txt = (TextComponent) comp;
        assertEquals("Hello World!", txt.getText());
        assertEquals("Arial", txt.getFont());
        assertEquals(0, txt.getFontSize(), "Font size 0 signals auto-fit");
    }

    // ── gif ───────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("gif type parses to ImageComponent with image name")
    void testGifComponent() {
        String yaml = """
                scenes:
                  default:
                    anim:
                      type: gif
                      image: "banner.gif"
                      position: "0 0"
                      size: "128 64"
                """;

        BoardConfig config = PARSER.parse(yaml);
        UIComponent comp = config.scenes().get("default").components().get(0);
        assertInstanceOf(GifComponent.class, comp);
        assertEquals("banner.gif", ((GifComponent) comp).getImageName());
    }

    @Test
    @DisplayName("gif type supports 'gif' key as fallback image path")
    void testGifComponentGifKey() {
        String yaml = """
                scenes:
                  default:
                    anim:
                      type: gif
                      gif: "loading.gif"
                      position: "0 0"
                      size: "128 64"
                """;

        BoardConfig config = PARSER.parse(yaml);
        UIComponent comp = config.scenes().get("default").components().get(0);
        assertInstanceOf(GifComponent.class, comp);
        assertEquals("loading.gif", ((GifComponent) comp).getImageName());
    }

    // ── delayed_function_timer ────────────────────────────────────────────────

    @Test
    @DisplayName("delayed_function_timer parses delay ticks and converts to ms")
    void testDelayedTimerTicks() {
        String yaml = """
                scenes:
                  default:
                    timer:
                      type: delayed_function_timer
                      delay: 100
                      position: "0 0"
                      size: "0 0"
                """;

        BoardConfig config = PARSER.parse(yaml);
        UIComponent comp = config.scenes().get("default").components().get(0);
        assertInstanceOf(DelayedTimerComponent.class, comp);
        DelayedTimerComponent timer = (DelayedTimerComponent) comp;
        assertEquals(100 * 50L, timer.getDelayMs(), "100 ticks * 50ms = 5000ms");
    }

    @Test
    @DisplayName("delayed_function_timer prefers delay-ms over tick conversion")
    void testDelayedTimerMs() {
        String yaml = """
                scenes:
                  default:
                    timer:
                      type: delayed_function_timer
                      delay-ms: 7500
                      position: "0 0"
                      size: "0 0"
                """;

        BoardConfig config = PARSER.parse(yaml);
        DelayedTimerComponent timer = (DelayedTimerComponent)
                config.scenes().get("default").components().get(0);
        assertEquals(7500L, timer.getDelayMs());
    }

    @Test
    @DisplayName("delayed_function_timer parses on-trigger function section")
    void testDelayedTimerFunctions() {
        String yaml = """
                scenes:
                  default:
                    timer:
                      type: delayed_function_timer
                      delay: 40
                      on-trigger:
                        cmd1:
                          type: command
                          command: "say time's up"
                      position: "0 0"
                      size: "0 0"
                """;

        BoardConfig config = PARSER.parse(yaml);
        DelayedTimerComponent timer = (DelayedTimerComponent)
                config.scenes().get("default").components().get(0);
        assertFalse(timer.getFunctions().isEmpty(), "Should have parsed at least one function");
        assertTrue(timer.getFunctions().containsKey("cmd1"));
    }

    // ── action_listener ───────────────────────────────────────────────────────

    @Test
    @DisplayName("action_listener parses identifier and on-trigger actions")
    void testActionListenerOnTrigger() {
        String yaml = """
                scenes:
                  default:
                    reward-listener:
                      type: action_listener
                      identifier: give-reward
                      on-trigger:
                        give:
                          type: command
                          command: "give %player_name% diamond 1"
                      position: "0 0"
                      size: "0 0"
                """;

        BoardConfig config = PARSER.parse(yaml);
        UIComponent comp = config.scenes().get("default").components().get(0);
        assertInstanceOf(ActionListenerComponent.class, comp);
        ActionListenerComponent alc = (ActionListenerComponent) comp;

        assertEquals("give-reward", alc.getIdentifier());
        assertTrue(alc.getActions().containsKey("give"));
    }

    @Test
    @DisplayName("action_listener uses on-action key as fallback")
    void testActionListenerOnAction() {
        String yaml = """
                scenes:
                  default:
                    teleporter:
                      type: action_listener
                      identifier: tp-spawn
                      on-action:
                        tp:
                          type: command
                          command: "tp %player_name% 0 64 0"
                      position: "0 0"
                      size: "0 0"
                """;

        BoardConfig config = PARSER.parse(yaml);
        ActionListenerComponent alc = (ActionListenerComponent)
                config.scenes().get("default").components().get(0);

        assertEquals("tp-spawn", alc.getIdentifier());
        assertTrue(alc.getActions().containsKey("tp"));
    }

    @Test
    @DisplayName("action_listener falls back to component id when identifier is absent")
    void testActionListenerDefaultIdentifier() {
        String yaml = """
                scenes:
                  default:
                    my-listener:
                      type: action_listener
                      position: "0 0"
                      size: "0 0"
                """;

        BoardConfig config = PARSER.parse(yaml);
        ActionListenerComponent alc = (ActionListenerComponent)
                config.scenes().get("default").components().get(0);

        assertEquals("my-listener", alc.getIdentifier());
    }

    // ── Mixed scene with all deferred types ───────────────────────────────────

    @Test
    @DisplayName("Scene with all five formerly-deferred component types parses without warnings")
    void testMixedDeferredScene() {
        String yaml = """
                scenes:
                  showcase:
                    bg:
                      type: background
                      color: "#1a1a2e"
                      position: "0 0"
                      size: "max max"
                    avatar:
                      type: head_2d
                      skin: "%player_name%"
                      position: "10 10"
                      size: "64 64"
                    label:
                      type: auto_font_size_text
                      text: "Welcome!"
                      position: "80 10"
                      size: "200 64"
                    banner:
                      type: gif
                      gif: "rainbow.gif"
                      position: "0 100"
                      size: "256 128"
                    timer:
                      type: delayed_function_timer
                      delay: 20
                      position: "0 0"
                      size: "0 0"
                    listener:
                      type: action_listener
                      identifier: showcase-trigger
                      position: "0 0"
                      size: "0 0"
                """;

        BoardConfig config = PARSER.parse(yaml);
        List<UIComponent> comps = config.scenes().get("showcase").components();

        // 6 components: background, head_2d, auto_font_size_text, gif, delayed_function_timer, action_listener
        assertEquals(6, comps.size(), "All 6 components should be parsed, none skipped");

        long nullCount = comps.stream().filter(c -> c == null).count();
        assertEquals(0, nullCount, "No component should be null");
    }
}
