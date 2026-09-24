package me.goosbanny.goosboards.interaction.action.handler;

import me.goosbanny.goosboards.api.event.BoardSceneChangeEvent;
import me.goosbanny.goosboards.core.logging.DebugLogger;
import me.goosbanny.goosboards.interaction.ActionDispatcher;
import me.goosbanny.goosboards.interaction.InteractionRouter;
import me.goosbanny.goosboards.interaction.action.ActionHandler;
import me.goosbanny.goosboards.interaction.economy.EconomyGuard;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public class SwitchSceneActionHandler implements ActionHandler {

    @Override
    public String getType() {
        return "switch_scene";
    }

    @Override
    public void execute(
            Player player,
            String boardId,
            String sourceId,
            String currentSceneId,
            String actionKey,
            Object rawAction,
            InteractionRouter.SceneSwitchListener sceneSwitchListener,
            boolean charged,
            double price,
            EconomyGuard economyGuard
    ) {
        String targetScene = ActionDispatcher.getStringField(rawAction, "scene", "");
        if (!targetScene.isBlank()) {
            BoardSceneChangeEvent sceneEvent =
                    new BoardSceneChangeEvent(player, sourceId, currentSceneId, targetScene);
            if (Bukkit.getServer() != null) {
                Bukkit.getPluginManager().callEvent(sceneEvent);
            }

            if (!sceneEvent.isCancelled()) {
                DebugLogger.log("Action", "Player %s switching scene on board '%s': '%s' -> '%s' (via '%s')",
                        player.getName(), boardId != null ? boardId : "auto", currentSceneId, targetScene, sourceId);
                if (sceneSwitchListener != null) {
                    sceneSwitchListener.onSceneSwitch(player, boardId, targetScene);
                }
            } else {
                DebugLogger.log("Action", "Scene change '%s' -> '%s' for %s cancelled by event",
                        currentSceneId, targetScene, player.getName());
                if (charged && economyGuard != null) {
                    economyGuard.refund(player.getUniqueId(), price);
                }
            }
        }
    }
}
