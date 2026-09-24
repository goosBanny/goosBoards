package me.goosbanny.goosboards.integration.placeholder;

import me.goosbanny.goosboards.integration.PlaceholderHook;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * High-performance, rate-limited placeholder resolution and caching service.
 * Caches evaluated placeholder strings per-viewer and per-template string with configurable
 * tick-based refresh intervals and debounce protection.
 */
public final class PlaceholderService {

    public static final int DEFAULT_REFRESH_TICKS = 20;
    public static final long DEFAULT_DEBOUNCE_MS = 150L;

    private static volatile int globalDefaultRefreshTicks = DEFAULT_REFRESH_TICKS;
    private static volatile long globalDebounceMs = DEFAULT_DEBOUNCE_MS;

    public record CachedResolution(String resolvedText, long resolvedTimeMs, long resolvedTick) {}

    // viewerId -> (rawTemplate -> CachedResolution)
    private static final Map<UUID, Map<String, CachedResolution>> VIEWER_CACHE = new ConcurrentHashMap<>();

    private PlaceholderService() {}

    public static void configure(int defaultRefreshTicks, long debounceMs) {
        globalDefaultRefreshTicks = defaultRefreshTicks > 0 ? defaultRefreshTicks : DEFAULT_REFRESH_TICKS;
        globalDebounceMs = debounceMs >= 0 ? debounceMs : DEFAULT_DEBOUNCE_MS;
    }

    public static int getDefaultRefreshTicks() {
        return globalDefaultRefreshTicks;
    }

    public static long getDebounceMs() {
        return globalDebounceMs;
    }

    public static void clearCache() {
        VIEWER_CACHE.clear();
    }

    public static void clearViewer(UUID viewerId) {
        if (viewerId != null) {
            VIEWER_CACHE.remove(viewerId);
        }
    }

    /**
     * Resolves all placeholders in text for the given viewer.
     * Fast-paths plain text with zero lookups.
     * Evaluates against per-viewer cache obeying refreshTicks and debounceMs.
     *
     * @param viewer               the viewing player (can be null)
     * @param text                 the raw template text
     * @param boardRefreshTicks    board-configured refresh rate in ticks (-1 to use global default)
     * @param currentTick          the current engine or server tick count
     * @param resolvedPlaceholders optional additional explicit key-value mappings
     * @return the fully resolved text
     */
    public static String resolve(
            Player viewer,
            String text,
            int boardRefreshTicks,
            long currentTick,
            Map<String, String> resolvedPlaceholders
    ) {
        if (text == null || text.isEmpty()) {
            return text;
        }

        // Fast path: If text has no %, no placeholders can match
        if (!text.contains("%")) {
            return applyExplicitOverrides(text, resolvedPlaceholders);
        }

        if (viewer == null) {
            // No player context: resolve static/server placeholders directly
            String result = resolveBuiltins(null, text);
            return applyExplicitOverrides(result, resolvedPlaceholders);
        }

        int effectiveRefreshTicks = boardRefreshTicks > 0 ? boardRefreshTicks : globalDefaultRefreshTicks;
        long effectiveDebounceMs = globalDebounceMs;
        long nowMs = System.currentTimeMillis();
        UUID viewerId = viewer.getUniqueId();

        Map<String, CachedResolution> playerMap = VIEWER_CACHE.computeIfAbsent(viewerId, k -> new ConcurrentHashMap<>());
        CachedResolution cached = playerMap.get(text);

        if (cached != null) {
            boolean debounced = (nowMs - cached.resolvedTimeMs() < effectiveDebounceMs);
            boolean withinInterval = (currentTick >= cached.resolvedTick() && (currentTick - cached.resolvedTick() < effectiveRefreshTicks));
            if (debounced || withinInterval) {
                return applyExplicitOverrides(cached.resolvedText(), resolvedPlaceholders);
            }
        }

        // Interval elapsed or not cached: Evaluate with PlaceholderAPI and built-ins
        String result = text;
        result = PlaceholderHook.setPlaceholders(viewer, result);
        result = resolveBuiltins(viewer, result);

        playerMap.put(text, new CachedResolution(result, nowMs, currentTick));
        return applyExplicitOverrides(result, resolvedPlaceholders);
    }

    private static String resolveBuiltins(Player viewer, String text) {
        if (text == null || !text.contains("%")) {
            return text;
        }
        String result = text;
        try {
            if (viewer != null) {
                if (viewer.getName() != null) {
                    result = result.replace("%player_name%", viewer.getName());
                }
                if (viewer.getDisplayName() != null) {
                    result = result.replace("%player_displayname%", viewer.getDisplayName());
                }
                result = result.replace("%player_ping%", String.valueOf(viewer.getPing()));
                if (viewer.getUniqueId() != null) {
                    result = result.replace("%player_uuid%", viewer.getUniqueId().toString());
                }
                if (viewer.getWorld() != null && viewer.getWorld().getName() != null) {
                    result = result.replace("%player_world%", viewer.getWorld().getName());
                }
            }
            if (Bukkit.getServer() != null) {
                result = result.replace("%server_online%", String.valueOf(Bukkit.getOnlinePlayers().size()));
            }
        } catch (Throwable ignored) {}
        return result;
    }

    private static String applyExplicitOverrides(String text, Map<String, String> resolvedPlaceholders) {
        if (text == null || resolvedPlaceholders == null || resolvedPlaceholders.isEmpty()) {
            return text;
        }
        String result = text;
        for (Map.Entry<String, String> entry : resolvedPlaceholders.entrySet()) {
            result = result.replace(entry.getKey(), entry.getValue());
        }
        return result;
    }
}