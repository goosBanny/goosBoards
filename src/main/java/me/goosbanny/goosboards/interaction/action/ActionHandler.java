package me.goosbanny.goosboards.interaction.action;

import me.goosbanny.goosboards.interaction.InteractionRouter;
import me.goosbanny.goosboards.interaction.economy.EconomyGuard;
import org.bukkit.entity.Player;

/**
 * Strategy interface for executing a specific type of action (e.g. command, scene switch, sound).
 */
public interface ActionHandler {

    /**
     * Type identifier matching the YAML 'type' property (e.g. "command", "switch_scene").
     */
    String getType();

    /**
     * Executes the action for the triggering player.
     */
    void execute(
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
    );
}
