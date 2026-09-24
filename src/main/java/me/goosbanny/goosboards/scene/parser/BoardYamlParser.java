package me.goosbanny.goosboards.scene.parser;

import me.goosbanny.goosboards.render.palette.ColorUtils;
import me.goosbanny.goosboards.scene.BoardConfig;
import me.goosbanny.goosboards.scene.component.UIComponent;
import me.goosbanny.goosboards.scene.component.container.BackgroundComponent;
import me.goosbanny.goosboards.scene.component.container.ScrollPaneComponent;
import me.goosbanny.goosboards.scene.component.interactive.ActionListenerComponent;
import me.goosbanny.goosboards.scene.component.interactive.ButtonComponent;
import me.goosbanny.goosboards.scene.component.misc.DelayedTimerComponent;
import me.goosbanny.goosboards.scene.component.text.PixelTextComponent;
import me.goosbanny.goosboards.scene.component.text.TextComponent;
import me.goosbanny.goosboards.scene.component.visual.GifComponent;
import me.goosbanny.goosboards.scene.component.visual.Head2DComponent;
import me.goosbanny.goosboards.scene.component.visual.ImageComponent;
import me.goosbanny.goosboards.scene.parser.component.ComponentParserRegistry;
import me.goosbanny.goosboards.scene.exception.BoardParseException;
import me.goosbanny.goosboards.scene.exception.LayoutDepthException;
import me.goosbanny.goosboards.scene.layout.LayoutEngine;

import me.goosbanny.goosboards.core.logging.DebugLogger;
import me.goosbanny.goosboards.raycast.Vector3d;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * Robust YAML parser for declarative board definitions, scene graphs, and
 * component trees.
 */
public class BoardYamlParser {
    private static final Logger LOGGER = Logger.getLogger(BoardYamlParser.class.getName());
    public static final int MAX_RECURSION_DEPTH = 16;
    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("%.+?%");

    private final ComponentParserRegistry componentParserRegistry;

    public BoardYamlParser() {
        this(new ComponentParserRegistry());
    }

    public BoardYamlParser(ComponentParserRegistry componentParserRegistry) {
        this.componentParserRegistry = Objects.requireNonNull(componentParserRegistry, "componentParserRegistry");
    }

    public BoardConfig parse(File file) {
        if (file == null || !file.exists()) {
            throw new BoardParseException("Board configuration file does not exist: " + file);
        }
        try {
            YamlConfiguration yaml = new YamlConfiguration();
            yaml.load(file);
            return parse(yaml);
        } catch (LayoutDepthException e) {
            throw e;
        } catch (Exception e) {
            throw new BoardParseException("Failed to parse board YAML file: " + file.getName(), e);
        }
    }

    public BoardConfig parse(String yamlContent) {
        if (yamlContent == null || yamlContent.isBlank()) {
            throw new BoardParseException("YAML content is empty");
        }
        try {
            YamlConfiguration yaml = new YamlConfiguration();
            yaml.load(new StringReader(yamlContent));
            return parse(yaml);
        } catch (LayoutDepthException e) {
            throw e;
        } catch (Exception e) {
            throw new BoardParseException("Failed to parse YAML content", e);
        }
    }

    public BoardConfig parse(YamlConfiguration yaml) {
        if (yaml == null) {
            throw new BoardParseException("YamlConfiguration cannot be null");
        }

        // 1. Parse settings
        BoardConfig.BoardSettings settings = parseSettings(yaml.getConfigurationSection("settings"));

        // 2. Parse displays
        Map<String, BoardConfig.DisplayDefinition> displays = parseDisplays(yaml.getConfigurationSection("displays"));

        // Default dimensions for layout if displays are specified
        int defaultCanvasWidth = 512;
        int defaultCanvasHeight = 384;
        if (!displays.isEmpty()) {
            BoardConfig.DisplayDefinition firstDisplay = displays.values().iterator().next();
            defaultCanvasWidth = firstDisplay.width() * 128;
            defaultCanvasHeight = firstDisplay.height() * 128;
        }

        // 3. Parse scenes
        Map<String, BoardConfig.SceneDefinition> scenes = parseScenes(
                yaml.getConfigurationSection("scenes"),
                defaultCanvasWidth,
                defaultCanvasHeight);

        // 4. Validate empty-hand right-click constraint
        double maxInteractionDist = displays.values().stream()
                .mapToDouble(BoardConfig.DisplayDefinition::interactionDistance)
                .max()
                .orElse(16.0);
        if (maxInteractionDist > 4.5) {
            for (BoardConfig.SceneDefinition scene : scenes.values()) {
                for (UIComponent comp : scene.components()) {
                    if (comp instanceof ButtonComponent btn) {
                        for (String actionKey : btn.getOnClickActions().keySet()) {
                            if (actionKey.toLowerCase(Locale.ROOT).contains("right")) {
                                LOGGER.warning(String.format(
                                        "Button '%s' in scene '%s': right-click action '%s' at interaction distance %.1f blocks (> 4.5m) will silently no-op for empty-handed players (vanilla client constraint).",
                                        btn.getId(), scene.id(), actionKey, maxInteractionDist));
                            }
                        }
                    }
                }
            }
        }

        return new BoardConfig(settings, displays, scenes);
    }

    private BoardConfig.BoardSettings parseSettings(ConfigurationSection section) {
        if (section == null) {
            return new BoardConfig.BoardSettings(true, true, false, true, true, true, -1);
        }
        boolean dithering = section.getBoolean("dithering", true);
        boolean persistent = section.getBoolean("persistent", false);
        boolean resetOnRadiusExit = section.getBoolean("reset-on-radius-exit", true);
        boolean resetOnRejoin = section.getBoolean("reset-on-rejoin", !persistent);
        boolean resetOnReload = section.getBoolean("reset-on-reload", !persistent);
        boolean resetOnDisappear = section.getBoolean("reset-on-disappear", resetOnRadiusExit);
        int placeholderRefreshTicks = section.getInt("placeholder-refresh-ticks",
                section.getInt("placeholder-refresh-interval", -1));
        return new BoardConfig.BoardSettings(dithering, resetOnRadiusExit, persistent, resetOnRejoin, resetOnReload,
                resetOnDisappear, placeholderRefreshTicks);
    }

    private Map<String, BoardConfig.DisplayDefinition> parseDisplays(ConfigurationSection section) {
        Map<String, BoardConfig.DisplayDefinition> displays = new HashMap<>();
        if (section == null) {
            return displays;
        }

        for (String key : section.getKeys(false)) {
            ConfigurationSection dSec = section.getConfigurationSection(key);
            if (dSec == null) {
                continue;
            }
            String world = dSec.getString("world", "world");
            int width = dSec.getInt("width", 4);
            int height = dSec.getInt("height", 3);

            ConfigurationSection tlSec = dSec.getConfigurationSection("top-left");
            double x = tlSec != null ? tlSec.getDouble("x", 0.0) : 0.0;
            double y = tlSec != null ? tlSec.getDouble("y", 64.0) : 64.0;
            double z = tlSec != null ? tlSec.getDouble("z", 0.0) : 0.0;
            Vector3d topLeft = new Vector3d(x, y, z);

            String direction = dSec.getString("direction", "south");
            double distance = dSec.getDouble("distance",
                    dSec.getDouble("radius",
                            dSec.getDouble("range",
                                    dSec.getDouble("view-distance",
                                            dSec.getDouble("activation-radius", 32.0)))));
            double interactionDistance = dSec.getDouble("interaction-distance",
                    dSec.getDouble("interaction-radius",
                            dSec.getDouble("interact-distance",
                                    dSec.getDouble("interact-radius", Math.min(distance, 16.0)))));
            if (interactionDistance < distance * 0.5) {
                LOGGER.warning(String.format(
                        "Display '%s' interaction-distance (%.1f) is significantly lower than activation distance (%.1f); players beyond %.1f blocks will see the board but cannot interact with it.",
                        key, interactionDistance, distance, interactionDistance));
            }

            boolean glow = dSec.getBoolean("glow", false);
            boolean hoverGlow = dSec.getBoolean("hover-glow",
                    dSec.getBoolean("glow-on-hover",
                            dSec.getBoolean("hover-outline",
                                    dSec.getBoolean("outline-on-hover",
                                            dSec.getBoolean("outline-on-look", false)))));
            String glowColor = dSec.getString("glow-color",
                    dSec.getString("outline-color", "white"));
            String hoverGlowColor = dSec.getString("hover-glow-color",
                    dSec.getString("hover-outline-color",
                            dSec.getString("on-hover-outline-color", "white")));
            String glowMode = dSec.getString("glow-mode", "border");
            String shape = dSec.getString("shape", dSec.getString("mask", "rectangle"));
            int cornerRadius = dSec.getInt("corner-radius", dSec.getInt("radius", 0));

            displays.put(key, new BoardConfig.DisplayDefinition(
                    key, world, width, height, topLeft, direction, distance, interactionDistance,
                    glow, hoverGlow, glowColor, hoverGlowColor, glowMode, shape, cornerRadius));
            DebugLogger.log("Parser",
                    "Parsed display '%s': world=%s, %dx%d blocks (%dx%d px), top-left=(%.1f, %.1f, %.1f), dir=%s, radius=%.1f, glow=%s, hoverGlow=%s, glowColor=%s, hoverGlowColor=%s",
                    key, world, width, height, width * 128, height * 128, topLeft.x(), topLeft.y(), topLeft.z(),
                    direction, distance, glow, hoverGlow, glowColor, hoverGlowColor);
        }
        return displays;
    }

    public Map<String, BoardConfig.SceneDefinition> parseScenes(
            ConfigurationSection section,
            int canvasWidth,
            int canvasHeight) {
        Map<String, BoardConfig.SceneDefinition> scenes = new HashMap<>();
        if (section == null) {
            return scenes;
        }

        // Check if `scenes` is actually a single flat scene where keys are component
        // IDs:
        // e.g.
        // scenes:
        // web-banner:
        // type: image
        boolean isFlatScene = false;
        for (String key : section.getKeys(false)) {
            ConfigurationSection sub = section.getConfigurationSection(key);
            if (sub != null && sub.contains("type")) {
                isFlatScene = true;
                break;
            }
        }

        if (isFlatScene) {
            List<UIComponent> components = new ArrayList<>();
            for (String compId : section.getKeys(false)) {
                ConfigurationSection compSec = section.getConfigurationSection(compId);
                if (compSec != null) {
                    UIComponent comp = parseComponent(compId, compSec, null, 1);
                    if (comp != null) {
                        components.add(comp);
                    }
                }
            }
            LayoutEngine.layout(components, canvasWidth, canvasHeight);
            scenes.put("default", new BoardConfig.SceneDefinition("default", components));
            DebugLogger.log("Parser", "Parsed flat scene 'default' with %d components (canvas: %dx%d px)",
                    components.size(), canvasWidth, canvasHeight);
            for (UIComponent c : components) {
                DebugLogger.log("Parser",
                        "  Component '%s' (%s): pos='%s', size='%s' -> bounds: x=%d, y=%d, w=%d, h=%d",
                        c.getId(), c.getClass().getSimpleName(), c.getPositionStr(), c.getSizeStr(),
                        c.getBounds().x(), c.getBounds().y(), c.getBounds().width(), c.getBounds().height());
            }
            return scenes;
        }

        for (String sceneId : section.getKeys(false)) {
            ConfigurationSection sceneSec = section.getConfigurationSection(sceneId);
            if (sceneSec == null) {
                continue;
            }

            List<UIComponent> components = new ArrayList<>();
            for (String compId : sceneSec.getKeys(false)) {
                ConfigurationSection compSec = sceneSec.getConfigurationSection(compId);
                if (compSec != null) {
                    UIComponent comp = parseComponent(compId, compSec, null, 1);
                    if (comp != null) {
                        components.add(comp);
                    }
                }
            }

            // Run layout engine on this scene's components
            LayoutEngine.layout(components, canvasWidth, canvasHeight);
            scenes.put(sceneId, new BoardConfig.SceneDefinition(sceneId, components));
            DebugLogger.log("Parser", "Parsed scene '%s' with %d components (canvas: %dx%d px)", sceneId,
                    components.size(), canvasWidth, canvasHeight);
            for (UIComponent c : components) {
                DebugLogger.log("Parser",
                        "  Component '%s' (%s): pos='%s', size='%s' -> bounds: x=%d, y=%d, w=%d, h=%d",
                        c.getId(), c.getClass().getSimpleName(), c.getPositionStr(), c.getSizeStr(),
                        c.getBounds().x(), c.getBounds().y(), c.getBounds().width(), c.getBounds().height());
            }
        }
        return scenes;
    }

    public UIComponent parseComponent(String id, ConfigurationSection sec, UIComponent parent, int depth) {
        if (depth > MAX_RECURSION_DEPTH) {
            throw new LayoutDepthException(
                    "Component recursion depth exceeded limit of " + MAX_RECURSION_DEPTH + " at id: " + id);
        }
        if (sec == null) {
            return null;
        }

        String type = sec.getString("type");
        if (type == null || type.isBlank()) {
            return null;
        }
        type = type.trim().toLowerCase(Locale.ROOT);

        boolean contextDependent = hasPlaceholdersOrConditions(sec);

        UIComponent component = componentParserRegistry.parse(type, id, sec, contextDependent);
        if (component == null) {
            LOGGER.warning("Unknown component type: " + type + " at id: " + id + ", skipping.");
            return null;
        }

        // Parse generic layout properties
        component.setPositionStr(sec.getString("position", "0 0"));
        component.setSizeStr(sec.getString("size", "auto auto"));
        component.setAlignment(sec.getString("alignment", "left"));
        component.setVerticalAlignment(sec.getString("vertical-alignment", "top"));

        // Parse children (support both "children" and "content" sections)
        ConfigurationSection childrenSec = sec.getConfigurationSection("children");
        if (childrenSec == null) {
            childrenSec = sec.getConfigurationSection("content");
        }

        if (childrenSec != null) {
            for (String childId : childrenSec.getKeys(false)) {
                ConfigurationSection childSec = childrenSec.getConfigurationSection(childId);
                if (childSec != null) {
                    UIComponent childComp = parseComponent(childId, childSec, component, depth + 1);
                    if (childComp != null) {
                        component.addChild(childComp);
                    }
                }
            }
        }

        return component;
    }

    private boolean hasPlaceholdersOrConditions(ConfigurationSection sec) {
        if (sec.contains("conditions")) {
            return true;
        }
        for (String key : sec.getKeys(true)) {
            if (sec.isString(key)) {
                String val = sec.getString(key);
                if (val != null && PLACEHOLDER_PATTERN.matcher(val).find()) {
                    return true;
                }
            }
        }
        return false;
    }
}