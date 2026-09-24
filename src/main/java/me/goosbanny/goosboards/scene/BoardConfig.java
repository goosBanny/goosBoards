package me.goosbanny.goosboards.scene;

import me.goosbanny.goosboards.scene.component.UIComponent;

import me.goosbanny.goosboards.raycast.Vector3d;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Encapsulates the fully parsed board configuration parsed from YAML.
 */
public record BoardConfig(
        BoardSettings settings,
        Map<String, DisplayDefinition> displays,
        Map<String, SceneDefinition> scenes
) {
    public record BoardSettings(
            boolean dithering,
            boolean resetOnRadiusExit,
            boolean persistent,
            boolean resetOnRejoin,
            boolean resetOnReload,
            boolean resetOnDisappear,
            int placeholderRefreshTicks
    ) {
        public BoardSettings(
                boolean dithering,
                boolean resetOnRadiusExit,
                boolean persistent,
                boolean resetOnRejoin,
                boolean resetOnReload,
                boolean resetOnDisappear
        ) {
            this(dithering, resetOnRadiusExit, persistent, resetOnRejoin, resetOnReload, resetOnDisappear, -1);
        }

        public BoardSettings(boolean dithering, boolean resetOnRadiusExit) {
            this(dithering, resetOnRadiusExit, false, true, true, resetOnRadiusExit, -1);
        }
    }

    public record DisplayDefinition(
            String id,
            String world,
            int width,
            int height,
            Vector3d topLeft,
            String direction,
            double distance,
            double interactionDistance,
            boolean glow,
            boolean hoverGlow,
            String glowColor,
            String hoverGlowColor,
            String glowMode,
            String shape,
            int cornerRadius
    ) {
        public DisplayDefinition(
                String id,
                String world,
                int width,
                int height,
                Vector3d topLeft,
                String direction,
                double distance,
                double interactionDistance,
                boolean glow,
                boolean hoverGlow,
                String glowColor,
                String glowMode,
                String shape,
                int cornerRadius
        ) {
            this(id, world, width, height, topLeft, direction, distance, interactionDistance, glow, hoverGlow, glowColor, "white", glowMode, shape, cornerRadius);
        }

        public DisplayDefinition(
                String id,
                String world,
                int width,
                int height,
                Vector3d topLeft,
                String direction,
                double distance,
                double interactionDistance,
                boolean glow,
                boolean hoverGlow,
                String glowColor,
                String glowMode
        ) {
            this(id, world, width, height, topLeft, direction, distance, interactionDistance, glow, hoverGlow, glowColor, "white", glowMode, "rectangle", 0);
        }

        public DisplayDefinition(
                String id,
                String world,
                int width,
                int height,
                Vector3d topLeft,
                String direction,
                double distance,
                double interactionDistance
        ) {
            this(id, world, width, height, topLeft, direction, distance, interactionDistance, false, false, "aqua", "white", "border", "rectangle", 0);
        }
    }

    public record SceneDefinition(
            String id,
            List<UIComponent> components
    ) {
        public SceneDefinition {
            components = components != null ? Collections.unmodifiableList(components) : Collections.emptyList();
        }
    }
}