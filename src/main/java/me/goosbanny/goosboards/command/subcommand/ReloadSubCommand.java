package me.goosbanny.goosboards.command.subcommand;

import me.goosbanny.goosboards.command.CommandUtils;
import me.goosbanny.goosboards.command.SubCommand;
import me.goosbanny.goosboards.config.ConfigReloadManager;
import me.goosbanny.goosboards.core.logging.MessageService;
import org.bukkit.command.CommandSender;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Handles /gb reload [--silent / -s] [--force / -f].
 */
public class ReloadSubCommand implements SubCommand {

    private final ConfigReloadManager reloadManager;
    private final MessageService messageService;

    public ReloadSubCommand(ConfigReloadManager reloadManager, MessageService messageService) {
        this.reloadManager = Objects.requireNonNull(reloadManager, "reloadManager");
        this.messageService = Objects.requireNonNull(messageService, "messageService");
    }

    @Override
    public String getName() {
        return "reload";
    }

    @Override
    public void execute(CommandSender sender, String label, String[] args) {
        Set<String> flags = CommandUtils.extractFlags(args);
        boolean silent = flags.contains("silent") || flags.contains("s");

        if (!silent) {
            messageService.send(sender, "reload-start");
        }

        long start = System.currentTimeMillis();
        ConfigReloadManager.ReloadResult result = reloadManager.reload();
        long elapsed = System.currentTimeMillis() - start;

        if (result.success()) {
            if (!silent) {
                messageService.send(sender, "reload-success", Map.of(
                        "boards", String.valueOf(result.loadedBoards()),
                        "time", String.valueOf(elapsed)
                ));
            }
        } else {
            messageService.send(sender, "reload-failure", Map.of("error", result.errorMessage()));
            messageService.send(sender, "reload-rollback");
        }
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 1) {
            return CommandUtils.filterPrefix(List.of("--silent", "--force", "-s", "-f"), args[0]);
        }
        return List.of();
    }
}
