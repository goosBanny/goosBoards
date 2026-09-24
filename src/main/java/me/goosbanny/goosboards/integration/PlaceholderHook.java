package me.goosbanny.goosboards.integration;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * High-performance zero-cost PlaceholderAPI integration guard.
 * Caches plugin presence and method handles on enable to eliminate per-tick reflection
 * or plugin manager lookup overhead. Fast-paths strings without placeholder tokens.
 */
public final class PlaceholderHook {

    private static final Logger LOGGER = Logger.getLogger(PlaceholderHook.class.getName());

    private static volatile boolean papiPresent = false;
    private static MethodHandle setPlaceholdersHandle = null;

    private PlaceholderHook() {}

    public static void init() {
        if (Bukkit.getServer() == null || Bukkit.getPluginManager() == null) {
            papiPresent = false;
            return;
        }

        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            try {
                Class<?> papiClass = Class.forName("me.clip.placeholderapi.PlaceholderAPI");
                setPlaceholdersHandle = MethodHandles.publicLookup().findStatic(
                        papiClass,
                        "setPlaceholders",
                        MethodType.methodType(String.class, Player.class, String.class)
                );
                papiPresent = true;
                LOGGER.info("PlaceholderAPI hook initialized successfully.");
            } catch (Throwable e) {
                papiPresent = false;
                LOGGER.log(Level.WARNING, "Failed to bind PlaceholderAPI method handle: " + e.getMessage(), e);
            }
        } else {
            papiPresent = false;
            setPlaceholdersHandle = null;
        }
    }

    public static boolean isPresent() {
        return papiPresent;
    }

    public static void setPapiPresentForTesting(boolean present) {
        papiPresent = present;
    }

    /**
     * Resolves placeholders in text for the given player.
     * Guaranteed zero-overhead fast-path for plain strings or when PlaceholderAPI is absent.
     */
    public static String setPlaceholders(Player player, String text) {
        if (!papiPresent || player == null || text == null || !text.contains("%")) {
            return text;
        }

        if (setPlaceholdersHandle != null) {
            try {
                return (String) setPlaceholdersHandle.invokeExact(player, text);
            } catch (Throwable e) {
                return text;
            }
        }
        return text;
    }
}
