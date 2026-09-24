package me.goosbanny.goosboards.interaction.action.handler;

import me.goosbanny.goosboards.core.logging.DebugLogger;
import me.goosbanny.goosboards.interaction.ActionDispatcher;
import me.goosbanny.goosboards.interaction.InteractionRouter;
import me.goosbanny.goosboards.interaction.action.ActionHandler;
import me.goosbanny.goosboards.interaction.economy.EconomyGuard;
import org.bukkit.entity.Player;

public class PlaySoundActionHandler implements ActionHandler {

    @Override
    public String getType() {
        return "play_sound";
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
        String sound = ActionDispatcher.getStringField(rawAction, "sound", "");
        if (!sound.isBlank()) {
            float pitch = (float) ActionDispatcher.getDoubleField(rawAction, "pitch", 1.0);
            float volume = (float) ActionDispatcher.getDoubleField(rawAction, "volume", 1.0);
            DebugLogger.log("Action", "Player %s played sound '%s' (pitch=%.2f, vol=%.2f) on '%s'",
                    player.getName(), sound, pitch, volume, sourceId);
            player.playSound(player.getLocation(), sound, volume, pitch);
        }
    }
}
