package me.goosbanny.goosboards.command.subcommand;

import me.goosbanny.goosboards.command.CommandUtils;
import me.goosbanny.goosboards.command.SubCommand;
import me.goosbanny.goosboards.config.ConfigReloadManager;
import me.goosbanny.goosboards.core.logging.MessageService;
import me.goosbanny.goosboards.scene.BoardConfig;
import org.bukkit.command.CommandSender;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Handles /gb delete <board>.
 */
public class DeleteSubCommand implements SubCommand {

    private final ConfigReloadManager reloadManager;
    private final MessageService messageService;
    private final File boardsFolder;

    public DeleteSubCommand(ConfigReloadManager reloadManager, MessageService messageService, File boardsFolder) {
        this.reloadManager = Objects.requireNonNull(reloadManager, "reloadManager");
        this.messageService = Objects.requireNonNull(messageService, "messageService");
        this.boardsFolder = Objects.requireNonNull(boardsFolder, "boardsFolder");
    }

    @Override
    public String getName() {
        return "delete";
    }

    @Override
    public void execute(CommandSender sender, String label, String[] args) {
        if (args.length < 1 || args[0].startsWith("-")) {
            messageService.send(sender, "unknown-command", Map.of("sub", "delete <board>", "label", label));
            return;
        }
        String rawBoardId = args[0];
        String matchedBoardId = null;
        for (String activeId : reloadManager.getActiveBoards().keySet()) {
            if (activeId.equalsIgnoreCase(rawBoardId)) {
                matchedBoardId = activeId;
                break;
            }
        }
        if (matchedBoardId == null) {
            matchedBoardId = CommandUtils.sanitizeBoardName(rawBoardId);
        }

        BoardConfig existing = reloadManager.getBoard(matchedBoardId);
        File ymlFile = new File(boardsFolder, matchedBoardId + ".yml");
        File yamlFile = new File(boardsFolder, matchedBoardId + ".yaml");

        if (existing == null && !ymlFile.exists() && !yamlFile.exists()) {
            messageService.send(sender, "board-not-found", Map.of("board", rawBoardId));
            return;
        }

        if (existing != null) {
            for (BoardConfig.DisplayDefinition d : existing.displays().values()) {
                UUID displayId = ConfigReloadManager.getDisplayUuid(matchedBoardId, d.id());
                if (reloadManager.getVirtualEntityTracker() != null) {
                    reloadManager.getVirtualEntityTracker().despawnAllForDisplay(displayId);
                }
                if (reloadManager.getRenderEngine() != null) {
                    reloadManager.getRenderEngine().removeDisplay(displayId);
                }
            }
        }

        boolean deleted = true;
        if (ymlFile.exists() && !ymlFile.delete()) {
            deleted = false;
        }
        if (yamlFile.exists() && !yamlFile.delete()) {
            deleted = false;
        }
        if (!deleted) {
            messageService.send(sender, "board-delete-failed", Map.of("board", matchedBoardId));
            return;
        }

        reloadManager.reload();
        messageService.send(sender, "board-deleted", Map.of("board", matchedBoardId));
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 1) {
            return CommandUtils.filterPrefix(new ArrayList<>(reloadManager.getActiveBoards().keySet()), args[0]);
        }
        return List.of();
    }
}
