package me.goosbanny.goosboards.command.subcommand;

import me.goosbanny.goosboards.command.CommandUtils;
import me.goosbanny.goosboards.command.SubCommand;
import me.goosbanny.goosboards.config.ConfigReloadManager;
import me.goosbanny.goosboards.core.logging.MessageService;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Handles /gb reset <board>.
 */
public class ResetSubCommand implements SubCommand {

    private final ConfigReloadManager reloadManager;
    private final MessageService messageService;

    public ResetSubCommand(ConfigReloadManager reloadManager, MessageService messageService) {
        this.reloadManager = Objects.requireNonNull(reloadManager, "reloadManager");
        this.messageService = Objects.requireNonNull(messageService, "messageService");
    }

    @Override
    public String getName() {
        return "reset";
    }

    @Override
    public void execute(CommandSender sender, String label, String[] args) {
        if (args.length < 1 || args[0].startsWith("-")) {
            messageService.send(sender, "unknown-command", Map.of("sub", "reset <board>", "label", label));
            return;
        }
        String boardId = args[0];
        String matchedBoardId = null;
        for (String activeId : reloadManager.getActiveBoards().keySet()) {
            if (activeId.equalsIgnoreCase(boardId)) {
                matchedBoardId = activeId;
                break;
            }
        }
        if (matchedBoardId == null) {
            messageService.send(sender, "board-not-found", Map.of("board", boardId));
            return;
        }
        reloadManager.resetActiveScene(matchedBoardId);
        reloadManager.reload();
        messageService.send(sender, "board-reset", Map.of("board", matchedBoardId));
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 1) {
            return CommandUtils.filterPrefix(new ArrayList<>(reloadManager.getActiveBoards().keySet()), args[0]);
        }
        return List.of();
    }
}
