package me.goosbanny.goosboards.scene;

import me.goosbanny.goosboards.integration.placeholder.PlaceholderService;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

/**
 * Contextual data provided to components during rendering.
 *
 * @param viewer                  the player viewing the display (may be null for global context-independent passes)
 * @param resolvedPlaceholders    resolved placeholder mapping for this render pass
 * @param hoveredComponentId     primary hovered component ID
 * @param hoveredComponentIds     all hovered component IDs (including ancestor containers for hover inheritance)
 * @param isAimingAtBoard         whether the player crosshair intersects the display
 * @param placeholderRefreshTicks configured placeholder refresh interval in ticks (-1 = global default)
 * @param currentTick             current server/engine tick count
 */
public record RenderContext(
        Player viewer,
        Map<String, String> resolvedPlaceholders,
        String hoveredComponentId,
        Set<String> hoveredComponentIds,
        boolean isAimingAtBoard,
        int placeholderRefreshTicks,
        long currentTick
) {

    public static final RenderContext EMPTY = new RenderContext(null, Collections.emptyMap(), null, Collections.emptySet(), false, -1, 0L);

    public RenderContext(
            Player viewer,
            Map<String, String> resolvedPlaceholders,
            String hoveredComponentId,
            Set<String> hoveredComponentIds,
            boolean isAimingAtBoard
    ) {
        this(viewer, resolvedPlaceholders, hoveredComponentId, hoveredComponentIds, isAimingAtBoard, -1, 0L);
    }

    public RenderContext(Player viewer, Map<String, String> resolvedPlaceholders) {
        this(viewer, resolvedPlaceholders, null, Collections.emptySet(), false, -1, 0L);
    }

    public RenderContext(Player viewer, Map<String, String> resolvedPlaceholders, String hoveredComponentId, boolean isAimingAtBoard) {
        this(viewer, resolvedPlaceholders, hoveredComponentId,
                hoveredComponentId != null ? Set.of(hoveredComponentId) : Collections.emptySet(),
                isAimingAtBoard, -1, 0L);
    }

    public static RenderContext empty() {
        return EMPTY;
    }

    public static RenderContext of(Player viewer) {
        return of(viewer, Collections.emptySet(), false, -1, 0L);
    }

    public static RenderContext of(Player viewer, String hoveredComponentId, boolean isAimingAtBoard) {
        Set<String> set = hoveredComponentId != null ? Set.of(hoveredComponentId) : Collections.emptySet();
        return of(viewer, set, isAimingAtBoard, -1, 0L);
    }

    public static RenderContext of(Player viewer, Set<String> hoveredComponentIds, boolean isAimingAtBoard) {
        return of(viewer, hoveredComponentIds, isAimingAtBoard, -1, 0L);
    }

    public static RenderContext of(
            Player viewer,
            Set<String> hoveredComponentIds,
            boolean isAimingAtBoard,
            int placeholderRefreshTicks,
            long currentTick
    ) {
        Set<String> safeSet = (hoveredComponentIds != null && !hoveredComponentIds.isEmpty())
                ? Set.copyOf(hoveredComponentIds)
                : Collections.emptySet();
        String primary = !safeSet.isEmpty() ? safeSet.iterator().next() : null;
        return new RenderContext(viewer, Collections.emptyMap(), primary, safeSet, isAimingAtBoard, placeholderRefreshTicks, currentTick);
    }

    public boolean isHovered(String componentId) {
        if (componentId == null) return false;
        if (hoveredComponentIds != null && hoveredComponentIds.contains(componentId)) {
            return true;
        }
        return componentId.equals(hoveredComponentId);
    }

    public String resolve(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        return PlaceholderService.resolve(viewer, text, placeholderRefreshTicks, currentTick, resolvedPlaceholders);
    }
}