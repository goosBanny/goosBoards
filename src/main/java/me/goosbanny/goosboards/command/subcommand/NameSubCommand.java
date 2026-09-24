package me.goosbanny.goosboards.command.subcommand;

import me.goosbanny.goosboards.command.CommandUtils;
import me.goosbanny.goosboards.command.SubCommand;
import me.goosbanny.goosboards.config.ConfigReloadManager;
import me.goosbanny.goosboards.core.logging.MessageService;
import me.goosbanny.goosboards.display.DisplayPlane;
import me.goosbanny.goosboards.raycast.spatial.DisplaySpatialIndex;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.Objects;

/**
 * Handles /gb name.
 */
public class NameSubCommand implements SubCommand {

    private final ConfigReloadManager reloadManager;
    private final DisplaySpatialIndex spatialIndex;
    private final MessageService messageService;

    public NameSubCommand(ConfigReloadManager reloadManager, DisplaySpatialIndex spatialIndex, MessageService messageService) {
        this.reloadManager = Objects.requireNonNull(reloadManager, "reloadManager");
        this.spatialIndex = Objects.requireNonNull(spatialIndex, "spatialIndex");
        this.messageService = Objects.requireNonNull(messageService, "messageService");
    }

    @Override
    public String getName() {
        return "name";
    }

    @Override
    public void execute(CommandSender sender, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            messageService.send(sender, "selection-player-only");
            return;
        }

        DisplayPlane hit = CommandUtils.raycastPlayerLook(player, spatialIndex);
        if (hit == null) {
            messageService.send(sender, "name-none");
            return;
        }

        String boardId = reloadManager.getBoardIdForDisplay(hit.id());
        if (boardId == null) boardId = "?";
        String displayLabel = CommandUtils.resolveDisplayLabel(hit.id(), reloadManager);

        messageService.send(sender, "name-result", Map.of(
                "board", boardId,
                "display", displayLabel
        ));
    }
}
