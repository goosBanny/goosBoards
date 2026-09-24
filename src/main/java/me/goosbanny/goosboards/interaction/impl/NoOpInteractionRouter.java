package me.goosbanny.goosboards.interaction.impl;

import me.goosbanny.goosboards.interaction.InteractionRouter;

import me.goosbanny.goosboards.api.ClickType;
import me.goosbanny.goosboards.raycast.RaycastResult;
import org.bukkit.entity.Player;

public class NoOpInteractionRouter implements InteractionRouter {
    @Override
    public void handleClick(Player player, RaycastResult.Hit hit, ClickType clickType) {
        // No-op placeholder for M2
    }
}