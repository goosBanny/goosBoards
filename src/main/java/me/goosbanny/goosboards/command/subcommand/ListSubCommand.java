package me.goosbanny.goosboards.command.subcommand;

import me.goosbanny.goosboards.command.CommandUtils;
import me.goosbanny.goosboards.command.SubCommand;
import me.goosbanny.goosboards.config.ConfigReloadManager;
import me.goosbanny.goosboards.core.logging.MessageService;
import me.goosbanny.goosboards.scene.BoardConfig;
import org.bukkit.command.CommandSender;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Handles /gb list [--verbose / -v].
 */
public class ListSubCommand implements SubCommand {

    private final ConfigReloadManager reloadManager;
    private final MessageService messageService;

    public ListSubCommand(ConfigReloadManager reloadManager, MessageService messageService) {
        this.reloadManager = Objects.requireNonNull(reloadManager, "reloadManager");
        this.messageService = Objects.requireNonNull(messageService, "messageService");
    }

    @Override
    public String getName() {
        return "list";
    }

    @Override
    public void execute(CommandSender sender, String label, String[] args) {
        Map<String, BoardConfig> boards = reloadManager.getActiveBoards();
        if (boards.isEmpty()) {
            messageService.send(sender, "list-empty");
            return;
        }

        Set<String> flags = CommandUtils.extractFlags(args);
        boolean verbose = flags.contains("verbose") || flags.contains("v");

        messageService.send(sender, "list-header", Map.of("count", String.valueOf(boards.size())));
        for (Map.Entry<String, BoardConfig> entry : boards.entrySet()) {
            String bId = entry.getKey();
            BoardConfig b = entry.getValue();
            messageService.send(sender, "list-item", Map.of(
                    "board", bId,
                    "displays", String.valueOf(b.displays().size()),
                    "scenes", String.valueOf(b.scenes().size())
            ));

            if (verbose) {
                for (BoardConfig.DisplayDefinition d : b.displays().values()) {
                    sender.sendMessage(String.format("   §8└─ §7Display '§f%s§7' in world '§f%s§7' [%dx%d blocks, facing %s]",
                            d.id(), d.world(), d.width(), d.height(), d.direction()));
                }
            }
        }
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 1) {
            return CommandUtils.filterPrefix(List.of("--verbose", "-v"), args[0]);
        }
        return List.of();
    }
}
