package me.goosbanny.goosboards.command.subcommand;

import me.goosbanny.goosboards.command.SubCommand;
import me.goosbanny.goosboards.core.logging.MessageService;
import org.bukkit.command.CommandSender;

import java.awt.GraphicsEnvironment;
import java.util.Map;
import java.util.Objects;

/**
 * Handles /gb fonts.
 */
public class FontsSubCommand implements SubCommand {

    private final MessageService messageService;

    public FontsSubCommand(MessageService messageService) {
        this.messageService = Objects.requireNonNull(messageService, "messageService");
    }

    @Override
    public String getName() {
        return "fonts";
    }

    @Override
    public void execute(CommandSender sender, String label, String[] args) {
        String[] families = GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames();
        messageService.send(sender, "fonts-header", Map.of("count", String.valueOf(families.length)));
        for (String family : families) {
            messageService.send(sender, "fonts-item", Map.of("font", family));
        }
    }
}
