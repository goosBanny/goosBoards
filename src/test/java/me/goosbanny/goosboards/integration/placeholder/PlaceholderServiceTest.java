package me.goosbanny.goosboards.integration.placeholder;

import me.goosbanny.goosboards.integration.PlaceholderHook;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Collections;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlaceholderServiceTest {

    private Player mockPlayer;
    private UUID playerUuid;

    @BeforeEach
    void setUp() {
        PlaceholderService.clearCache();
        PlaceholderService.configure(20, 150L);
        PlaceholderHook.setPapiPresentForTesting(false);

        playerUuid = UUID.randomUUID();
        mockPlayer = Mockito.mock(Player.class);
        Mockito.when(mockPlayer.getUniqueId()).thenReturn(playerUuid);
        Mockito.when(mockPlayer.getName()).thenReturn("GooseTester");
        Mockito.when(mockPlayer.getDisplayName()).thenReturn("GooseTester");
        Mockito.when(mockPlayer.getPing()).thenReturn(42);
    }

    @Test
    @DisplayName("Plain text without placeholder tokens fast-paths immediately")
    void testFastPathNoPlaceholders() {
        String plain = "Hello World! No Placeholders Here.";
        String result = PlaceholderService.resolve(mockPlayer, plain, 20, 100L, Collections.emptyMap());
        assertEquals(plain, result);
    }

    @Test
    @DisplayName("Resolves built-in player placeholders")
    void testResolveBuiltins() {
        String template = "Player: %player_name%, Ping: %player_ping%ms";
        String result = PlaceholderService.resolve(mockPlayer, template, 20, 1L, Collections.emptyMap());
        assertEquals("Player: GooseTester, Ping: 42ms", result);
    }

    @Test
    @DisplayName("Caches resolved placeholder within refresh interval ticks")
    void testPlaceholderCachingWithinInterval() {
        String template = "Ping: %player_ping%ms";
        // First resolve at tick 100
        String first = PlaceholderService.resolve(mockPlayer, template, 20, 100L, Collections.emptyMap());
        assertEquals("Ping: 42ms", first);

        // Player ping changes in background
        Mockito.when(mockPlayer.getPing()).thenReturn(150);

        // Still within 20 ticks (at tick 110) -> must return cached value
        String cached = PlaceholderService.resolve(mockPlayer, template, 20, 110L, Collections.emptyMap());
        assertEquals("Ping: 42ms", cached, "Within refresh interval, cached value must be returned");

        // After 20 ticks (at tick 125, elapsed 25 >= 20) -> re-evaluates
        // Also wait enough ms so debounce does not block
        try {
            Thread.sleep(160);
        } catch (InterruptedException ignored) {}

        String refreshed = PlaceholderService.resolve(mockPlayer, template, 20, 125L, Collections.emptyMap());
        assertEquals("Ping: 150ms", refreshed, "After refresh interval, updated value must be evaluated");
    }

    @Test
    @DisplayName("Debounce suppresses rapid re-evaluation even if ticks advanced")
    void testDebounceProtection() {
        PlaceholderService.configure(1, 200L); // 1 tick refresh interval, but 200ms debounce
        String template = "Ping: %player_ping%ms";

        String first = PlaceholderService.resolve(mockPlayer, template, 1, 10L, Collections.emptyMap());
        assertEquals("Ping: 42ms", first);

        Mockito.when(mockPlayer.getPing()).thenReturn(999);

        // Ticks advanced to 15, but only 10ms elapsed (well under 200ms debounce)
        String debounced = PlaceholderService.resolve(mockPlayer, template, 1, 15L, Collections.emptyMap());
        assertEquals("Ping: 42ms", debounced, "Debounce must prevent re-evaluation within debounce window");
    }

    @Test
    @DisplayName("clearCache invalidates all cached player entries")
    void testClearCache() {
        String template = "Ping: %player_ping%ms";
        PlaceholderService.resolve(mockPlayer, template, 20, 10L, Collections.emptyMap());

        Mockito.when(mockPlayer.getPing()).thenReturn(88);
        PlaceholderService.clearCache();

        // Immediately after clearCache, must re-evaluate
        String result = PlaceholderService.resolve(mockPlayer, template, 20, 11L, Collections.emptyMap());
        assertEquals("Ping: 88ms", result);
    }

    @Test
    @DisplayName("clearViewer removes only the specified viewer's cache")
    void testClearViewer() {
        UUID otherId = UUID.randomUUID();
        Player otherPlayer = Mockito.mock(Player.class);
        Mockito.when(otherPlayer.getUniqueId()).thenReturn(otherId);
        Mockito.when(otherPlayer.getName()).thenReturn("OtherUser");
        Mockito.when(otherPlayer.getPing()).thenReturn(10);

        String template = "Ping: %player_ping%ms";
        PlaceholderService.resolve(mockPlayer, template, 20, 10L, Collections.emptyMap());
        PlaceholderService.resolve(otherPlayer, template, 20, 10L, Collections.emptyMap());

        Mockito.when(mockPlayer.getPing()).thenReturn(50);
        Mockito.when(otherPlayer.getPing()).thenReturn(50);

        PlaceholderService.clearViewer(playerUuid);

        // mockPlayer was cleared -> re-evaluates
        String mockRes = PlaceholderService.resolve(mockPlayer, template, 20, 12L, Collections.emptyMap());
        assertEquals("Ping: 50ms", mockRes);

        // otherPlayer was not cleared -> still cached
        String otherRes = PlaceholderService.resolve(otherPlayer, template, 20, 12L, Collections.emptyMap());
        assertEquals("Ping: 10ms", otherRes);
    }

    @Test
    @DisplayName("Explicit overrides apply to resolved strings")
    void testExplicitOverrides() {
        String template = "Score: {score}";
        Map<String, String> overrides = Map.of("{score}", "1000");
        String result = PlaceholderService.resolve(mockPlayer, template, 20, 10L, overrides);
        assertEquals("Score: 1000", result);
    }
}