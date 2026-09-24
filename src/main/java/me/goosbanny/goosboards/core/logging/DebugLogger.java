package me.goosbanny.goosboards.core.logging;

import me.goosbanny.goosboards.GoosBoards;

import java.util.logging.Logger;

/**
 * Centralized diagnostic and verbose debug logging service.
 * <p>
 * Zero allocation when disabled. When enabled, provides exhaustive insights into:
 * <ul>
 *     <li>Board YAML configuration loading and component tree construction</li>
 *     <li>Component layout dimensions, calculated bounding boxes, and scissor clips</li>
 *     <li>Image downloading, disk loading, scaling, and palette quantization</li>
 *     <li>Text font loading, MiniMessage/AWT rendering, and raster byte counts</li>
 *     <li>Spatial index proximity sweeps, viewer enter/exit triggers, and LOD decays</li>
 *     <li>Virtual entity item frame allocation, packet construction, and metadata indices</li>
 *     <li>Render tick timing, tile dirty diffs, hash changes, and Netty broadcasts</li>
 *     <li>Raycast intersection math, target component resolution, and click routing</li>
 * </ul>
 */
public final class DebugLogger {

    private static volatile boolean enabled = false;
    private static Logger logger = Logger.getLogger("GoosBoards");

    private DebugLogger() {}

    /**
     * Initializes the debug logger with the plugin's logger and initial state.
     */
    public static void init(Logger pluginLogger, boolean initiallyEnabled) {
        if (pluginLogger != null) {
            logger = pluginLogger;
        }
        enabled = initiallyEnabled;
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static void setEnabled(boolean value) {
        enabled = value;
    }

    /**
     * Logs a debug message with a subsystem tag.
     */
    public static void log(String tag, String message) {
        if (!enabled) return;
        logger.info("[DEBUG][" + tag + "] " + message);
    }

    /**
     * Logs a formatted debug message with a subsystem tag.
     */
    public static void log(String tag, String format, Object... args) {
        if (!enabled) return;
        try {
            logger.info("[DEBUG][" + tag + "] " + String.format(format, args));
        } catch (Exception e) {
            logger.info("[DEBUG][" + tag + "] " + format);
        }
    }
}
