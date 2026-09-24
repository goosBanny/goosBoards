package me.goosbanny.goosboards.interaction;

import me.goosbanny.goosboards.api.ClickType;
import me.goosbanny.goosboards.raycast.RaycastResult;
import me.goosbanny.goosboards.scene.component.UIComponent;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;
import java.util.function.BiConsumer;

public interface InteractionRouter {
    void handleClick(Player player, RaycastResult.Hit hit, ClickType clickType);
    default void handleClick(Player player, RaycastResult.Hit hit, ClickType clickType, boolean checkRateLimit) {
        handleClick(player, hit, clickType);
    }

    default void handleHover(Player player, RaycastResult.Hit hit) {}
    default void handleHoverExit(Player player, UUID displayId) {}
    default UIComponent findTargetComponent(Player player, List<UIComponent> components, int pixelX, int pixelY) { return null; }
    default void onPlayerQuit(UUID playerId) {}
    @FunctionalInterface
    public interface SceneSwitchListener {
        void onSceneSwitch(Player player, String boardId, String targetScene);
    }

    default void setSceneSwitchHandler(BiConsumer<Player, String> handler) {
        setSceneSwitchHandler((player, boardId, targetScene) -> {
            if (handler != null) {
                handler.accept(player, targetScene);
            }
        });
    }
    default void setSceneSwitchHandler(SceneSwitchListener handler) {}
    default String getHoveredButton(UUID playerId, UUID displayId) { return null; }
    default boolean isHovered(UUID playerId, UUID displayId, String buttonId) { return false; }
    default boolean hasInteractiveTarget(Player player, UUID displayId, int pixelX, int pixelY) { return true; }
    default void clearHoverStates() {}
}