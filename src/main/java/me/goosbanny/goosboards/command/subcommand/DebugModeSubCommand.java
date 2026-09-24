package me.goosbanny.goosboards.command.subcommand;

import me.goosbanny.goosboards.command.CommandUtils;
import me.goosbanny.goosboards.command.SubCommand;
import me.goosbanny.goosboards.core.logging.DebugLogger;
import org.bukkit.command.CommandSender;

import java.util.List;
import java.util.Locale;

/**
 * Handles /gb debugmode [on/off/toggle] and alias debug-mode.
 */
public class DebugModeSubCommand implements SubCommand {

    @Override
    public String getName() {
        return "debugmode";
    }

    @Override
    public List<String> getAliases() {
        return List.of("debug-mode");
    }

    @Override
    public void execute(CommandSender sender, String label, String[] args) {
        if (args.length > 0) {
            String val = args[0].toLowerCase(Locale.ROOT);
            if (val.equals("on") || val.equals("true") || val.equals("enable")) {
                DebugLogger.setEnabled(true);
            } else if (val.equals("off") || val.equals("false") || val.equals("disable")) {
                DebugLogger.setEnabled(false);
            } else {
                DebugLogger.setEnabled(!DebugLogger.isEnabled());
            }
        } else {
            DebugLogger.setEnabled(!DebugLogger.isEnabled());
        }
        sender.sendMessage("§eGoosBoards §8» §7Verbose console debug logging is now " +
                (DebugLogger.isEnabled() ? "§a§lENABLED§r §7(full real-time diagnostics on)" : "§c§lDISABLED§r §7(normal mode)") + ".");
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 1) {
            return CommandUtils.filterPrefix(List.of("on", "off", "toggle"), args[0]);
        }
        return List.of();
    }
}
