package me.goosbanny.goosboards.interaction.impl;

import me.goosbanny.goosboards.core.logging.DebugLogger;
import me.goosbanny.goosboards.interaction.ActionDispatcher;
import me.goosbanny.goosboards.interaction.InteractionRateLimiter;
import me.goosbanny.goosboards.interaction.InteractionRouter;
import me.goosbanny.goosboards.interaction.economy.EconomyGuard;
import me.goosbanny.goosboards.protocol.packet.ClickPacketListener;

import me.goosbanny.goosboards.api.event.BoardClickEvent;
import me.goosbanny.goosboards.api.event.BoardHoverEvent;
import me.goosbanny.goosboards.api.event.BoardSceneChangeEvent;
import me.goosbanny.goosboards.api.ClickType;
import me.goosbanny.goosboards.core.scheduler.UniversalScheduler;
import me.goosbanny.goosboards.raycast.RaycastResult;
import me.goosbanny.goosboards.scene.BoardConfig;
import me.goosbanny.goosboards.scene.component.interactive.ButtonComponent;
import me.goosbanny.goosboards.scene.Rect;
import me.goosbanny.goosboards.scene.component.container.ScrollPaneComponent;
import me.goosbanny.goosboards.scene.component.UIComponent;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * Production interaction router dispatching clicks and hovers to scene components,
 * enforcing token-bucket rate limits and atomic economy guards.
 */
public class DefaultInteractionRouter implements InteractionRouter {

    @FunctionalInterface
    public interface SceneProvider {
        BoardConfig.SceneDefinition getActiveScene(Player player, UUID displayId);
    }

    private final UniversalScheduler scheduler;
    private final InteractionRateLimiter rateLimiter;
    private final EconomyGuard economyGuard;
    private final SceneProvider sceneProvider;
    private Function<UUID, String> boardResolver;
    private BiConsumer<Player, String> sceneSwitchHandler;
    private SceneSwitchListener sceneSwitchListener;

    // Per-player, per-display hovered component ID: playerId -> (displayId -> componentId)
    private final Map<UUID, Map<UUID, String>> playerHoveredButtons = new ConcurrentHashMap<>();
    private final Map<UUID, AtomicLong> playerHoverRevisions = new ConcurrentHashMap<>();

    public DefaultInteractionRouter(
            UniversalScheduler scheduler,
            InteractionRateLimiter rateLimiter,
            EconomyGuard economyGuard,
            SceneProvider sceneProvider
    ) {
        this(scheduler, rateLimiter, economyGuard, sceneProvider, null);
    }

    public DefaultInteractionRouter(
            UniversalScheduler scheduler,
            InteractionRateLimiter rateLimiter,
            EconomyGuard economyGuard,
            SceneProvider sceneProvider,
            Function<UUID, String> boardResolver
    ) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.rateLimiter = Objects.requireNonNull(rateLimiter, "rateLimiter");
        this.economyGuard = Objects.requireNonNull(economyGuard, "economyGuard");
        this.sceneProvider = Objects.requireNonNull(sceneProvider, "sceneProvider");
        this.boardResolver = boardResolver;
    }

    public void setBoardResolver(Function<UUID, String> boardResolver) {
        this.boardResolver = boardResolver;
    }

    @Override
    public void setSceneSwitchHandler(BiConsumer<Player, String> sceneSwitchHandler) {
        this.sceneSwitchHandler = sceneSwitchHandler;
        this.sceneSwitchListener = (player, boardId, targetScene) -> {
            if (sceneSwitchHandler != null) {
                sceneSwitchHandler.accept(player, targetScene);
            }
        };
    }

    @Override
    public void setSceneSwitchHandler(SceneSwitchListener sceneSwitchListener) {
        this.sceneSwitchListener = sceneSwitchListener;
        this.sceneSwitchHandler = (player, targetScene) -> {
            if (sceneSwitchListener != null) {
                sceneSwitchListener.onSceneSwitch(player, null, targetScene);
            }
        };
    }

    @Override
    public void onPlayerQuit(UUID playerId) {
        if (playerId != null) {
            playerHoveredButtons.remove(playerId);
            playerHoverRevisions.remove(playerId);
        }
    }

    @Override
    public void handleClick(Player player, RaycastResult.Hit hit, ClickType clickType) {
        handleClick(player, hit, clickType, true);
    }

    @Override
    public void handleClick(Player player, RaycastResult.Hit hit, ClickType clickType, boolean checkRateLimit) {
        if (player == null || hit == null) {
            return;
        }

        // 1. Check rate limit if requested
        if (checkRateLimit && !rateLimiter.tryConsumeClick(player.getUniqueId())) {
            return;
        }

        // 2. Resolve active scene for this display
        UUID displayId = hit.displayId();
        String boardId = boardResolver != null ? boardResolver.apply(displayId) : null;
        BoardConfig.SceneDefinition scene = sceneProvider.getActiveScene(player, displayId);
        if (scene == null || scene.components() == null) {
            return;
        }

        // 3. Find deepest interactive component hit
        UIComponent hitComp = findTargetComponent(player, scene.components(), hit.pixelX(), hit.pixelY());
        if (!(hitComp instanceof ButtonComponent button)) {
            DebugLogger.log("Interact",
                    "Player %s %s hit display %s at (%d, %d) on board '%s' (scene '%s') - hit %s (no button)",
                    player.getName(), clickType, displayId, hit.pixelX(), hit.pixelY(),
                    boardId != null ? boardId : "unknown", scene.id(), hitComp != null ? hitComp.getId() : "none");
            return;
        }

        Map<String, Object> actions = button.getOnClickActions();
        if (actions.isEmpty()) {
            DebugLogger.log("Interact",
                    "Player %s clicked button '%s' on board '%s' at (%d, %d) - button has no actions",
                    player.getName(), button.getId(), boardId != null ? boardId : "unknown", hit.pixelX(), hit.pixelY());
            return;
        }

        DebugLogger.log("Interact",
                "Player %s clicked button '%s' on board '%s' (scene '%s') at (%d, %d) - triggering %d action(s)",
                player.getName(), button.getId(), boardId != null ? boardId : "unknown", scene.id(), hit.pixelX(), hit.pixelY(), actions.size());

        // 4. Dispatch actions directly on player's entity thread (caller ClickPacketListener is already running on entity scheduler)
        BoardClickEvent clickEvent = new BoardClickEvent(
                player,
                scene.id(),
                displayId.toString(),
                hit.pixelX(),
                hit.pixelY(),
                clickType
        );
        if (Bukkit.getServer() != null) {
            Bukkit.getPluginManager().callEvent(clickEvent);
        }

        if (clickEvent.isCancelled()) {
            DebugLogger.log("Interact",
                    "Click on button '%s' cancelled by event for %s", button.getId(), player.getName());
            return;
        }

        org.bukkit.Location preLoc = null;
        try {
            preLoc = player.getLocation();
        } catch (Throwable ignored) {}

        for (Map.Entry<String, Object> entry : actions.entrySet()) {
            if (preLoc != null) {
                org.bukkit.Location currentLoc = null;
                try {
                    currentLoc = player.getLocation();
                } catch (Throwable ignored) {}
                if (currentLoc != null && (currentLoc.getWorld() != preLoc.getWorld()
                        || (currentLoc.getBlockX() >> 4) != (preLoc.getBlockX() >> 4)
                        || (currentLoc.getBlockZ() >> 4) != (preLoc.getBlockZ() >> 4))) {
                    scheduler.runOnEntity(player, p -> executeAction(p, boardId, button, scene.id(), entry.getKey(), entry.getValue()));
                    return;
                }
            }
            boolean success = executeAction(player, boardId, button, scene.id(), entry.getKey(), entry.getValue());
            if (!success) {
                break;
            }
            try {
                preLoc = player.getLocation();
            } catch (Throwable ignored) {}
        }
    }

    @Override
    public void handleHover(Player player, RaycastResult.Hit hit) {
        if (player == null || hit == null) {
            return;
        }

        UUID playerId = player.getUniqueId();
        long taskRevision = playerHoverRevisions.computeIfAbsent(playerId, k -> new AtomicLong()).incrementAndGet();

        // Resolve active scene
        UUID displayId = hit.displayId();
        String boardId = boardResolver != null ? boardResolver.apply(displayId) : null;
        BoardConfig.SceneDefinition scene = sceneProvider.getActiveScene(player, displayId);
        if (scene == null || scene.components() == null) {
            return;
        }

        UIComponent hitComp = findTargetComponent(player, scene.components(), hit.pixelX(), hit.pixelY());
        Map<UUID, String> displayHover = playerHoveredButtons.computeIfAbsent(playerId, k -> new ConcurrentHashMap<>());

        if (hitComp != null) {
            String compId = hitComp.getId();
            String prevCompId = displayHover.put(displayId, compId);
            if (hitComp instanceof ButtonComponent button) {
                if (!Objects.equals(prevCompId, compId)) {
                    if (!rateLimiter.tryConsumeHover(playerId)) {
                        return;
                    }
                    Map<String, Object> hoverActions = button.getOnHoverActions();
                    if (!hoverActions.isEmpty()) {
                        scheduler.runOnEntity(player, p -> {
                            AtomicLong curRev = playerHoverRevisions.get(p.getUniqueId());
                            if (curRev != null && curRev.get() != taskRevision) {
                                return; // Stale hover event discarded
                            }
                            BoardHoverEvent hoverEvent = new BoardHoverEvent(
                                    p,
                                    scene.id(),
                                    hit.pixelX(),
                                    hit.pixelY()
                            );
                            if (Bukkit.getServer() != null) {
                                Bukkit.getPluginManager().callEvent(hoverEvent);
                            }

                            for (Map.Entry<String, Object> entry : hoverActions.entrySet()) {
                                executeAction(p, boardId, button, scene.id(), entry.getKey(), entry.getValue());
                            }
                        });
                    }
                }
            }
        } else {
            displayHover.remove(displayId);
        }
    }

    @Override
    public void handleHoverExit(Player player, UUID displayId) {
        if (player == null || displayId == null) return;
        playerHoverRevisions.computeIfAbsent(player.getUniqueId(), k -> new AtomicLong()).incrementAndGet();
        Map<UUID, String> displayHover = playerHoveredButtons.get(player.getUniqueId());
        if (displayHover != null) {
            displayHover.remove(displayId);
        }
    }

    @Override
    public String getHoveredButton(UUID playerId, UUID displayId) {
        if (playerId == null || displayId == null) return null;
        Map<UUID, String> displayHover = playerHoveredButtons.get(playerId);
        return displayHover != null ? displayHover.get(displayId) : null;
    }

    @Override
    public boolean isHovered(UUID playerId, UUID displayId, String buttonId) {
        return Objects.equals(getHoveredButton(playerId, displayId), buttonId);
    }

    @Override
    public boolean hasInteractiveTarget(Player player, UUID displayId, int pixelX, int pixelY) {
        if (displayId == null) return false;
        BoardConfig.SceneDefinition scene = sceneProvider.getActiveScene(player, displayId);
        if (scene == null || scene.components() == null) return false;
        UIComponent hitComp = findTargetComponent(player, scene.components(), pixelX, pixelY);
        if (hitComp instanceof ButtonComponent button) {
            return !button.getOnClickActions().isEmpty();
        }
        return false;
    }

    @Override
    public void clearHoverStates() {
        playerHoveredButtons.clear();
        playerHoverRevisions.clear();
    }

    private UIComponent findComponentById(List<UIComponent> components, String id) {
        if (components == null || id == null) return null;
        for (UIComponent comp : components) {
            if (id.equals(comp.getId())) return comp;
            UIComponent childFound = findComponentById(comp.getChildren(), id);
            if (childFound != null) return childFound;
        }
        return null;
    }

    /**
     * Finds the deepest component matching the specified pixel coordinates.
     */
    public UIComponent findTargetComponent(List<UIComponent> components, int pixelX, int pixelY) {
        return findTargetComponent(null, components, pixelX, pixelY);
    }

    public UIComponent findTargetComponent(Player player, List<UIComponent> components, int pixelX, int pixelY) {
        if (components == null) {
            return null;
        }
        UUID viewerId = player != null ? player.getUniqueId() : null;

        // Pass 1: Prioritize interactive components (ButtonComponent).
        // This ensures text, labels, or images overlaid on or inside a button properly trigger the button!
        UIComponent interactiveHit = findInteractiveComponent(components, pixelX, pixelY, viewerId);
        if (interactiveHit != null) {
            return interactiveHit;
        }

        // Pass 2: Fallback to deepest component (for passive components or scroll panes)
        for (int i = components.size() - 1; i >= 0; i--) {
            UIComponent comp = components.get(i);
            UIComponent hit = testComponentHit(comp, pixelX, pixelY, viewerId);
            if (hit != null) {
                return hit;
            }
        }
        return null;
    }

    private ButtonComponent findInteractiveComponent(List<UIComponent> components, int pixelX, int pixelY, UUID viewerId) {
        if (components == null) return null;
        for (int i = components.size() - 1; i >= 0; i--) {
            UIComponent comp = components.get(i);
            ButtonComponent btn = testInteractiveHit(comp, pixelX, pixelY, viewerId);
            if (btn != null) {
                return btn;
            }
        }
        return null;
    }

    private ButtonComponent testInteractiveHit(UIComponent comp, int pixelX, int pixelY, UUID viewerId) {
        Rect bounds = comp.getBounds();
        if (bounds == null) {
            return null;
        }

        int localX = pixelX - bounds.x();
        int localY = pixelY - bounds.y();

        if (comp instanceof ScrollPaneComponent scrollPane) {
            if (scrollPane.isInViewport(localX, localY)) {
                int contentX = localX + scrollPane.getScrollOffsetX();
                int contentY = localY + scrollPane.getEffectiveScrollOffsetY(viewerId);
                return findInteractiveComponent(scrollPane.getChildren(), contentX, contentY, viewerId);
            }
            return null;
        }

        if (comp instanceof ButtonComponent button) {
            // Check if click is on any nested button first
            List<UIComponent> children = button.getChildren();
            for (int i = children.size() - 1; i >= 0; i--) {
                ButtonComponent childBtn = testInteractiveHit(children.get(i), pixelX, pixelY, viewerId);
                if (childBtn != null) {
                    return childBtn;
                }
            }

            // Check if click hits this button directly OR any of its non-interactive children (e.g. child text/icon)
            if (button.containsPixel(localX, localY)) {
                return button;
            }
            for (UIComponent child : children) {
                Rect cb = child.getBounds();
                if (cb != null && child.containsPixel(pixelX - cb.x(), pixelY - cb.y())) {
                    return button;
                }
            }
            return null;
        }

        // For non-button containers (like panels or background boxes), search their children for buttons
        List<UIComponent> children = comp.getChildren();
        if (children != null && !children.isEmpty()) {
            for (int i = children.size() - 1; i >= 0; i--) {
                ButtonComponent childBtn = testInteractiveHit(children.get(i), pixelX, pixelY, viewerId);
                if (childBtn != null) {
                    return childBtn;
                }
            }
        }

        return null;
    }

    private UIComponent testComponentHit(UIComponent comp, int pixelX, int pixelY, UUID viewerId) {
        Rect bounds = comp.getBounds();
        if (bounds == null) {
            return null;
        }

        int localX = pixelX - bounds.x();
        int localY = pixelY - bounds.y();

        if (comp instanceof ScrollPaneComponent scrollPane) {
            if (scrollPane.isInViewport(localX, localY)) {
                int contentX = localX + scrollPane.getScrollOffsetX();
                int contentY = localY + scrollPane.getEffectiveScrollOffsetY(viewerId);

                List<UIComponent> children = scrollPane.getChildren();
                for (int i = children.size() - 1; i >= 0; i--) {
                    UIComponent child = children.get(i);
                    UIComponent childHit = testComponentHit(child, contentX, contentY, viewerId);
                    if (childHit != null) {
                        return childHit;
                    }
                }
                return scrollPane;
            }
            return null;
        }

        List<UIComponent> children = comp.getChildren();
        for (int i = children.size() - 1; i >= 0; i--) {
            UIComponent child = children.get(i);
            UIComponent childHit = testComponentHit(child, pixelX, pixelY, viewerId);
            if (childHit != null) {
                return childHit;
            }
        }

        if (comp.containsPixel(localX, localY)) {
            return comp;
        }
        return null;
    }

    private boolean executeAction(Player player, String boardId, ButtonComponent button, String currentSceneId, String actionKey, Object rawAction) {
        return ActionDispatcher.execute(player, boardId, button.getId(), currentSceneId, actionKey, rawAction, economyGuard, sceneSwitchListener);
    }

    private String getStringField(Object obj, String key, String def) {
        if (obj instanceof ConfigurationSection sec) {
            return sec.getString(key, def);
        } else if (obj instanceof Map<?, ?> map) {
            Object val = map.get(key);
            return val != null ? String.valueOf(val) : def;
        }
        return def;
    }

    private double getDoubleField(Object obj, String key, double def) {
        if (obj instanceof ConfigurationSection sec) {
            return sec.getDouble(key, def);
        } else if (obj instanceof Map<?, ?> map) {
            Object val = map.get(key);
            if (val instanceof Number n) {
                return n.doubleValue();
            } else if (val instanceof String s) {
                try {
                    return Double.parseDouble(s);
                } catch (NumberFormatException ignored) {}
            }
        }
        return def;
    }

    private boolean getBooleanField(Object obj, String key, boolean def) {
        if (obj instanceof ConfigurationSection sec) {
            return sec.getBoolean(key, def);
        } else if (obj instanceof Map<?, ?> map) {
            Object val = map.get(key);
            if (val instanceof Boolean b) {
                return b;
            } else if (val instanceof String s) {
                return Boolean.parseBoolean(s);
            }
        }
        return def;
    }
}