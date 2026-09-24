package me.goosbanny.goosboards.command;

import org.bukkit.command.CommandSender;

import java.util.List;

/**
 * Strategy interface for individual /goosboard subcommands.
 */
public interface SubCommand {

    /**
     * Primary name of the subcommand (e.g., "reload", "create").
     */
    String getName();

    /**
     * Optional aliases for the subcommand (e.g., "tp" for "teleport").
     */
    default List<String> getAliases() {
        return List.of();
    }

    /**
     * Executes the subcommand logic.
     *
     * @param sender the command sender (player or console)
     * @param label  the alias used to invoke the root command (e.g., "gb", "goosboard")
     * @param args   the arguments passed after the subcommand name
     */
    void execute(CommandSender sender, String label, String[] args);

    /**
     * Computes tab-completion candidates for this subcommand.
     *
     * @param sender the command sender
     * @param args   arguments including the subcommand name or subsequent arguments
     * @return matching completion suggestions
     */
    default List<String> tabComplete(CommandSender sender, String[] args) {
        return List.of();
    }
}
