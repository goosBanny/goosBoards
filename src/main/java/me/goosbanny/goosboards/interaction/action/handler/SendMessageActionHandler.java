package me.goosbanny.goosboards.interaction.action.handler;

import me.goosbanny.goosboards.core.logging.DebugLogger;
import me.goosbanny.goosboards.core.logging.MessageService;
import me.goosbanny.goosboards.integration.PlaceholderHook;
import me.goosbanny.goosboards.interaction.ActionDispatcher;
import me.goosbanny.goosboards.interaction.InteractionRouter;
import me.goosbanny.goosboards.interaction.action.ActionHandler;
import me.goosbanny.goosboards.interaction.economy.EconomyGuard;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.entity.Player;

public class SendMessageActionHandler implements ActionHandler {

    @Override
    public String getType() {
        return "send_message";
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
        String msg = ActionDispatcher.getStringField(rawAction, "message", "");
        if (!msg.isBlank()) {
            String safeName = player.getName().replaceAll("[^a-zA-Z0-9_]", "");
            String resolved = msg.replace("%player_name%", safeName);
            resolved = PlaceholderHook.setPlaceholders(player, resolved);
            String formatting = ActionDispatcher.getStringField(rawAction, "formatting", "minimessage");
            DebugLogger.log("Action", "Player %s sent message action on '%s'",
                    player.getName(), sourceId);
            if ("minimessage".equalsIgnoreCase(formatting)) {
                String converted = MessageService.convertLegacyToMiniMessage(resolved);
                player.sendMessage(MiniMessage.miniMessage().deserialize(converted));
            } else {
                player.sendMessage(resolved.replace('&', '§'));
            }
        }
    }
}
