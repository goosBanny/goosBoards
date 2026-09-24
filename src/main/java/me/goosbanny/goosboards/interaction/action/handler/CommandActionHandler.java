package me.goosbanny.goosboards.interaction.action.handler;

import me.goosbanny.goosboards.GoosBoards;
import me.goosbanny.goosboards.core.logging.DebugLogger;
import me.goosbanny.goosboards.integration.PlaceholderHook;
import me.goosbanny.goosboards.interaction.ActionDispatcher;
import me.goosbanny.goosboards.interaction.InteractionRouter;
import me.goosbanny.goosboards.interaction.action.ActionHandler;
import me.goosbanny.goosboards.interaction.economy.EconomyGuard;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public class CommandActionHandler implements ActionHandler {

    @Override
    public String getType() {
        return "command";
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
        java.util.List<String> commands = ActionDispatcher.getStringListField(rawAction, "commands", "command");
        if (commands.isEmpty()) {
            return;
        }

        boolean console = ActionDispatcher.getBooleanField(rawAction, "execute-from-console", false);
        String safeName = player.getName().replaceAll("[^a-zA-Z0-9_]", "");

        for (String cmd : commands) {
            if (cmd == null || cmd.isBlank()) {
                continue;
            }
            String resolved = cmd.replace("%player_name%", safeName);
            String finalCmd = PlaceholderHook.setPlaceholders(player, resolved);
            // Sanitize command string against newline/carriage-return command chaining injection
            finalCmd = finalCmd.replace('\r', ' ').replace('\n', ' ').trim();
            if (finalCmd.startsWith("/")) {
                finalCmd = finalCmd.substring(1);
            }
            if (finalCmd.isEmpty()) {
                continue;
            }

            DebugLogger.log("Action", "Player %s executed command '%s' (console=%s) on '%s'",
                    player.getName(), finalCmd, console, sourceId);
            if (console) {
                final String dispatchCmd = finalCmd;
                try {
                    Bukkit.getGlobalRegionScheduler().run(
                            GoosBoards.getInstance(),
                            task -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), dispatchCmd)
                    );
                } catch (Throwable fallback) {
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), dispatchCmd);
                }
            } else {
                player.performCommand(finalCmd);
            }
        }
    }
}
