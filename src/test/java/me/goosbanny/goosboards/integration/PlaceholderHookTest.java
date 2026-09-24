package me.goosbanny.goosboards.integration;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PlaceholderHookTest {

    @AfterEach
    void tearDown() {
        PlaceholderHook.setPapiPresentForTesting(false);
    }

    @Test
    @DisplayName("Safeguard 6: Text without percent symbol fast-paths without any overhead")
    void testFastPathNoPlaceholders() {
        Player mockPlayer = mock(Player.class);
        PlaceholderHook.setPapiPresentForTesting(true);

        String raw = "Plain Text Without Placeholders";
        String result = PlaceholderHook.setPlaceholders(mockPlayer, raw);

        assertSame(raw, result, "Strings without '%' must immediately return original reference");
        verifyNoInteractions(mockPlayer);
    }

    @Test
    @DisplayName("When PlaceholderAPI is not present, strings pass through unchanged")
    void testPassThroughWhenAbsent() {
        Player mockPlayer = mock(Player.class);
        PlaceholderHook.setPapiPresentForTesting(false);

        String raw = "Score: %player_name%";
        String result = PlaceholderHook.setPlaceholders(mockPlayer, raw);

        assertEquals("Score: %player_name%", result);
    }
}
