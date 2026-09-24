package me.goosbanny.goosboards.command.subcommand;

import me.goosbanny.goosboards.command.BoardSelectionManager;
import me.goosbanny.goosboards.command.SubCommand;
import me.goosbanny.goosboards.core.logging.MessageService;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Objects;

/**
 * Handles /gb cancel.
 */
public class CancelSubCommand implements SubCommand {

    private final BoardSelectionManager selectionManager;
    private final MessageService messageService;

    public CancelSubCommand(BoardSelectionManager selectionManager, MessageService messageService) {
        this.selectionManager = Objects.requireNonNull(selectionManager, "selectionManager");
        this.messageService = Objects.requireNonNull(messageService, "messageService");
    }

    @Override
    public String getName() {
        return "cancel";
    }

    @Override
    public void execute(CommandSender sender, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            messageService.send(sender, "selection-player-only");
            return;
        }
        boolean cancelled = selectionManager.cancelSession(player.getUniqueId());
        if (cancelled) {
            messageService.send(player, "selection-cancelled");
            messageService.sendActionBar(player, "selection-cancelled-actionbar");
        } else {
            messageService.send(player, "selection-not-active");
        }
    }
}
