package me.goosbanny.goosboards.scene;

import me.goosbanny.goosboards.scene.parser.BoardYamlParser;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.StringReader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class BoardSettingsPlaceholderTest {

    @Test
    @DisplayName("Parses placeholder-refresh-ticks from settings")
    void testParsePlaceholderRefreshTicks() throws Exception {
        String yaml = """
                settings:
                  dithering: true
                  persistent: false
                  placeholder-refresh-ticks: 15
                scenes:
                  default:
                    text:
                      type: text
                      position: "0 0"
                      size: "100 20"
                      text: "Test"
                """;

        YamlConfiguration config = new YamlConfiguration();
        config.load(new StringReader(yaml));

        BoardYamlParser parser = new BoardYamlParser();
        BoardConfig board = parser.parse(config);

        assertNotNull(board);
        assertNotNull(board.settings());
        assertEquals(15, board.settings().placeholderRefreshTicks());
    }

    @Test
    @DisplayName("Parses placeholder-refresh-interval as alias")
    void testParsePlaceholderRefreshIntervalAlias() throws Exception {
        String yaml = """
                settings:
                  dithering: false
                  placeholder-refresh-interval: 35
                scenes:
                  default:
                    text:
                      type: text
                      position: "0 0"
                      size: "100 20"
                      text: "Test"
                """;

        YamlConfiguration config = new YamlConfiguration();
        config.load(new StringReader(yaml));

        BoardYamlParser parser = new BoardYamlParser();
        BoardConfig board = parser.parse(config);

        assertNotNull(board);
        assertNotNull(board.settings());
        assertEquals(35, board.settings().placeholderRefreshTicks());
    }

    @Test
    @DisplayName("Defaults to -1 when placeholder refresh is not specified")
    void testDefaultPlaceholderRefreshTicks() throws Exception {
        String yaml = """
                settings:
                  dithering: true
                scenes:
                  default:
                    text:
                      type: text
                      position: "0 0"
                      size: "100 20"
                      text: "Test"
                """;

        YamlConfiguration config = new YamlConfiguration();
        config.load(new StringReader(yaml));

        BoardYamlParser parser = new BoardYamlParser();
        BoardConfig board = parser.parse(config);

        assertNotNull(board);
        assertNotNull(board.settings());
        assertEquals(-1, board.settings().placeholderRefreshTicks());
    }
}
