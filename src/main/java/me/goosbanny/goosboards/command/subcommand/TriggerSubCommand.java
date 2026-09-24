package me.goosbanny.goosboards.command.subcommand;

import me.goosbanny.goosboards.command.CommandUtils;
import me.goosbanny.goosboards.command.SubCommand;
import me.goosbanny.goosboards.config.ConfigReloadManager;
import me.goosbanny.goosboards.core.logging.MessageService;
import me.goosbanny.goosboards.interaction.ActionDispatcher;
import me.goosbanny.goosboards.scene.BoardConfig;
import me.goosbanny.goosboards.scene.component.UIComponent;
import me.goosbanny.goosboards.scene.component.interactive.ActionListenerComponent;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Handles /gb trigger <display> <player> <identifier>.
 */
public class TriggerSubCommand implements SubCommand {

    private final ConfigReloadManager reloadManager;
    private final MessageService messageService;

    public TriggerSubCommand(ConfigReloadManager reloadManager, MessageService messageService) {
        this.reloadManager = Objects.requireNonNull(reloadManager, "reloadManager");
        this.messageService = Objects.requireNonNull(messageService, "messageService");
    }

    @Override
    public String getName() {
        return "trigger";
    }

    @Override
    public void execute(CommandSender sender, String label, String[] args) {
        if (args.length < 3) {
            messageService.send(sender, "unknown-command", Map.of("sub", "trigger <display> <player> <identifier>", "label", label));
            return;
        }

        String displayId = args[0];
        String targetName = args[1];
        String identifier = args[2];

        Player target = Bukkit.getPlayerExact(targetName);
        if (target == null || !target.isOnline()) {
            messageService.send(sender, "player-not-found", Map.of("player", targetName));
            return;
        }

        String boardId = null;
        BoardConfig foundBoard = null;
        for (Map.Entry<String, BoardConfig> entry : reloadManager.getActiveBoards().entrySet()) {
            if (entry.getValue().displays().containsKey(displayId)) {
                boardId = entry.getKey();
                foundBoard = entry.getValue();
                break;
            }
        }

        if (foundBoard == null) {
            messageService.send(sender, "display-not-found", Map.of("display", displayId));
            return;
        }

        BoardConfig.SceneDefinition scene = reloadManager.getDefaultScene(boardId);
        if (scene == null) {
            messageService.send(sender, "trigger-listener-not-found", Map.of("identifier", identifier, "display", displayId));
            return;
        }

        ActionListenerComponent listener = findActionListener(scene.components(), identifier);
        if (listener == null) {
            messageService.send(sender, "trigger-listener-not-found", Map.of("identifier", identifier, "display", displayId));
            return;
        }

        for (Map.Entry<String, Object> action : listener.getActions().entrySet()) {
            ActionDispatcher.execute(
                    target, displayId, scene.id(), action.getKey(), action.getValue(), null, null
            );
        }

        messageService.send(sender, "trigger-success", Map.of(
                "identifier", identifier,
                "display", displayId,
                "player", targetName
        ));
    }

    private ActionListenerComponent findActionListener(List<UIComponent> components, String identifier) {
        if (components == null) return null;
        for (UIComponent c : components) {
            if (c instanceof ActionListenerComponent alc && alc.getIdentifier().equals(identifier)) {
                return alc;
            }
            ActionListenerComponent found = findActionListener(c.getChildren(), identifier);
            if (found != null) return found;
        }
        return null;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 1) {
            List<String> displayIds = new ArrayList<>();
            for (BoardConfig b : reloadManager.getActiveBoards().values()) {
                displayIds.addAll(b.displays().keySet());
            }
            return CommandUtils.filterPrefix(displayIds, args[0]);
        }
        if (args.length == 2) {
            List<String> players = new ArrayList<>();
            for (Player p : Bukkit.getOnlinePlayers()) {
                players.add(p.getName());
            }
            return CommandUtils.filterPrefix(players, args[1]);
        }
        return List.of();
    }
}
