package me.goosbanny.goosboards.core.logging;

import me.goosbanny.goosboards.GoosBoards;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Map;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MessageServiceTest {

    private Plugin mockPlugin;
    private File testDataFolder;
    private MessageService messageService;

    @BeforeEach
    void setUp() throws Exception {
        testDataFolder = new File("build/tmp/test-messages-" + System.nanoTime());
        testDataFolder.mkdirs();

        mockPlugin = mock(Plugin.class);
        when(mockPlugin.getDataFolder()).thenReturn(testDataFolder);
        when(mockPlugin.getLogger()).thenReturn(Logger.getLogger("MessageTest"));

        String defaultYaml = """
                prefix: "<dark_gray>[<aqua>GoosBoards</aqua>]</dark_gray> "
                test-simple: "{prefix}<green>Operation complete!</green>"
                test-legacy: "{prefix}&aGreen &lBold"
                test-hex: "{prefix}&#ffaa00Gold text"
                test-placeholder: "{prefix}<gray>Reloaded {count} items in {time}ms.</gray>"
                """;

        when(mockPlugin.getResource("messages.yml")).thenAnswer(inv ->
                new ByteArrayInputStream(defaultYaml.getBytes(StandardCharsets.UTF_8))
        );

        File messagesFile = new File(testDataFolder, "messages.yml");
        Files.writeString(messagesFile.toPath(), defaultYaml);

        messageService = new MessageService(mockPlugin);
        messageService.init();
    }

    @Test
    @DisplayName("Legacy Bukkit color codes (&a, &l, etc.) convert accurately to MiniMessage tags")
    void testLegacyCodeConversion() {
        String input = "&aGreen &bAqua &cRed &lBold &rReset";
        String converted = MessageService.convertLegacyToMiniMessage(input);

        assertEquals("<green>Green <aqua>Aqua <red>Red <bold>Bold <reset>Reset", converted);
    }

    @Test
    @DisplayName("Hex color codes &#RRGGBB and &x&r&r&g&g&b&b convert accurately to <#RRGGBB>")
    void testHexColorConversion() {
        String input1 = "&#ff55aaCustom Hex";
        String converted1 = MessageService.convertLegacyToMiniMessage(input1);
        assertEquals("<#ff55aa>Custom Hex", converted1);

        String input2 = "&x&1&2&3&4&5&6Bungee Hex";
        String converted2 = MessageService.convertLegacyToMiniMessage(input2);
        assertEquals("<#123456>Bungee Hex", converted2);
    }

    @Test
    @DisplayName("Placeholders and prefix are cleanly resolved in Component output")
    void testPlaceholderResolution() {
        Component comp = messageService.get("test-placeholder", Map.of("count", "5", "time", "12"));
        String plain = PlainTextComponentSerializer.plainText().serialize(comp);

        assertTrue(plain.contains("[GoosBoards]"));
        assertTrue(plain.contains("Reloaded 5 items in 12ms."));
    }

    @Test
    @DisplayName("Missing keys provide descriptive red fallback component")
    void testMissingKeyFallback() {
        Component comp = messageService.get("nonexistent-key");
        String plain = PlainTextComponentSerializer.plainText().serialize(comp);

        assertTrue(plain.contains("Missing message key: nonexistent-key"));
    }

    @Test
    @DisplayName("Caffeine Component cache returns identical reference on repeated queries with identical inputs")
    void testComponentCaching() {
        Component first = messageService.get("test-simple");
        Component second = messageService.get("test-simple");

        assertSame(first, second, "Repeated calls for identical static message must return cached Component");
    }

    @Test
    @DisplayName("send() forwards formatted Adventure Component to CommandSender")
    void testSendToCommandSender() {
        CommandSender sender = mock(CommandSender.class);
        messageService.send(sender, "test-simple");

        verify(sender).sendMessage(any(Component.class));
    }
}
