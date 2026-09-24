package me.goosbanny.goosboards.command;

import me.goosbanny.goosboards.command.subcommand.CancelSubCommand;
import me.goosbanny.goosboards.command.subcommand.CoordinatesSubCommand;
import me.goosbanny.goosboards.command.subcommand.CreateSubCommand;
import me.goosbanny.goosboards.command.subcommand.DebugModeSubCommand;
import me.goosbanny.goosboards.command.subcommand.DebugSubCommand;
import me.goosbanny.goosboards.command.subcommand.DeleteSubCommand;
import me.goosbanny.goosboards.command.subcommand.FontsSubCommand;
import me.goosbanny.goosboards.command.subcommand.ListSubCommand;
import me.goosbanny.goosboards.command.subcommand.NameSubCommand;
import me.goosbanny.goosboards.command.subcommand.ReloadSubCommand;
import me.goosbanny.goosboards.command.subcommand.ResetSubCommand;
import me.goosbanny.goosboards.command.subcommand.ShowcaseSubCommand;
import me.goosbanny.goosboards.command.subcommand.TeleportSubCommand;
import me.goosbanny.goosboards.command.subcommand.TriggerSubCommand;
import me.goosbanny.goosboards.config.ConfigReloadManager;
import me.goosbanny.goosboards.core.logging.MessageService;
import me.goosbanny.goosboards.core.metrics.MetricsCollector;
import me.goosbanny.goosboards.raycast.spatial.DisplaySpatialIndex;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Main administration and diagnostics command coordinator for GoosBoards (/goosboard, /gb).
 * Delegates to modular SubCommand implementations following Single Responsibility and Open-Closed principles.
 */
public class GoosBoardCommand implements CommandExecutor, TabCompleter {

    private final MessageService messageService;
    private final File boardsFolder;
    private final Map<String, SubCommand> subcommands = new LinkedHashMap<>();
    private final List<String> primarySubCommandNames = new ArrayList<>();

    public GoosBoardCommand(
            ConfigReloadManager reloadManager,
            MetricsCollector metricsCollector,
            MessageService messageService,
            BoardSelectionManager selectionManager,
            DisplaySpatialIndex spatialIndex,
            File boardsFolder
    ) {
        this.messageService = Objects.requireNonNull(messageService, "messageService");
        this.boardsFolder = Objects.requireNonNull(boardsFolder, "boardsFolder");

        registerSubCommand(new ReloadSubCommand(reloadManager, messageService));
        registerSubCommand(new DebugSubCommand(reloadManager, metricsCollector, messageService));
        registerSubCommand(new DebugModeSubCommand());
        registerSubCommand(new ListSubCommand(reloadManager, messageService));
        registerSubCommand(new CreateSubCommand(reloadManager, selectionManager, messageService, boardsFolder));
        registerSubCommand(new CancelSubCommand(selectionManager, messageService));
        registerSubCommand(new DeleteSubCommand(reloadManager, messageService, boardsFolder));
        registerSubCommand(new ResetSubCommand(reloadManager, messageService));
        registerSubCommand(new TeleportSubCommand(reloadManager, spatialIndex, messageService));
        registerSubCommand(new FontsSubCommand(messageService));
        registerSubCommand(new NameSubCommand(reloadManager, spatialIndex, messageService));
        registerSubCommand(new CoordinatesSubCommand(reloadManager, spatialIndex, messageService));
        registerSubCommand(new TriggerSubCommand(reloadManager, messageService));
        registerSubCommand(new ShowcaseSubCommand(reloadManager, selectionManager, messageService, boardsFolder));
    }

    public void registerSubCommand(SubCommand subCommand) {
        String name = subCommand.getName().toLowerCase(Locale.ROOT);
        subcommands.put(name, subCommand);
        if (!primarySubCommandNames.contains(name)) {
            primarySubCommandNames.add(name);
        }
        for (String alias : subCommand.getAliases()) {
            subcommands.put(alias.toLowerCase(Locale.ROOT), subCommand);
        }
    }

    public File getBoardsFolder() {
        return boardsFolder;
    }

    private boolean hasPermission(CommandSender sender) {
        return sender.hasPermission("goosboards.admin") || sender.hasPermission("interactiveboard.admin");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!hasPermission(sender)) {
            messageService.send(sender, "no-permission");
            return true;
        }

        if (args.length == 0) {
            sendHelp(sender, label);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        String[] subArgs = Arrays.copyOfRange(args, 1, args.length);

        SubCommand executor = subcommands.get(sub);
        if (executor != null) {
            executor.execute(sender, label, subArgs);
        } else {
            messageService.send(sender, "unknown-command", Map.of("sub", sub, "label", label));
            sendHelp(sender, label);
        }
        return true;
    }

    private void sendHelp(CommandSender sender, String label) {
        messageService.send(sender, "help-header");
        messageService.send(sender, "help-create",      Map.of("label", label));
        messageService.send(sender, "help-cancel",      Map.of("label", label));
        messageService.send(sender, "help-delete",      Map.of("label", label));
        messageService.send(sender, "help-reset",       Map.of("label", label));
        messageService.send(sender, "help-teleport",    Map.of("label", label));
        messageService.send(sender, "help-fonts",       Map.of("label", label));
        messageService.send(sender, "help-name",        Map.of("label", label));
        messageService.send(sender, "help-coordinates", Map.of("label", label));
        messageService.send(sender, "help-trigger",     Map.of("label", label));
        messageService.send(sender, "help-reload",      Map.of("label", label));
        messageService.send(sender, "help-debug",       Map.of("label", label));
        messageService.send(sender, "help-list",        Map.of("label", label));
        messageService.send(sender, "help-showcase",    Map.of("label", label));
        messageService.send(sender, "help-footer");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!hasPermission(sender)) {
            return Collections.emptyList();
        }

        if (args.length == 1) {
            return CommandUtils.filterPrefix(primarySubCommandNames, args[0]);
        }

        if (args.length >= 2) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            SubCommand executor = subcommands.get(sub);
            if (executor != null) {
                String[] subArgs = Arrays.copyOfRange(args, 1, args.length);
                return executor.tabComplete(sender, subArgs);
            }
        }

        return Collections.emptyList();
    }
}