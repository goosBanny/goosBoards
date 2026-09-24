package me.goosbanny.goosboards.interaction;

import me.goosbanny.goosboards.interaction.action.handler.CommandActionHandler;
import me.goosbanny.goosboards.interaction.action.handler.SendMessageActionHandler;
import me.goosbanny.goosboards.interaction.economy.ChargeResult;
import me.goosbanny.goosboards.interaction.economy.EconomyGuard;
import me.goosbanny.goosboards.scene.BoardConfig;
import me.goosbanny.goosboards.scene.parser.BoardYamlParser;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.io.File;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ActionDispatcherTest {

    private Player mockPlayer;
    private EconomyGuard mockEconomy;
    private UUID playerUuid;

    @BeforeEach
    void setUp() {
        playerUuid = UUID.randomUUID();
        mockPlayer = mock(Player.class);
        when(mockPlayer.getUniqueId()).thenReturn(playerUuid);
        when(mockPlayer.getName()).thenReturn("TesterGoose");

        mockEconomy = mock(EconomyGuard.class);
    }

    @Test
    @DisplayName("CommandActionHandler executes a single command string")
    void testSingleCommandExecution() {
        CommandActionHandler handler = new CommandActionHandler();
        Map<String, Object> rawAction = new HashMap<>();
        rawAction.put("type", "command");
        rawAction.put("command", "say Hello from %player_name%!");
        rawAction.put("execute-from-console", false);

        handler.execute(mockPlayer, "board1", "btn1", "scene1", "action1", rawAction, null, false, 0.0, null);

        verify(mockPlayer, times(1)).performCommand("say Hello from TesterGoose!");
    }

    @Test
    @DisplayName("CommandActionHandler executes a list of commands via 'commands' key")
    void testMultipleCommandsList() {
        CommandActionHandler handler = new CommandActionHandler();
        Map<String, Object> rawAction = new HashMap<>();
        rawAction.put("type", "command");
        rawAction.put("commands", List.of(
                "lp user %player_name% parent add vip",
                "give %player_name% diamond 5",
                "/say Welcome %player_name%!"
        ));
        rawAction.put("execute-from-console", false);

        handler.execute(mockPlayer, "board1", "btn1", "scene1", "action1", rawAction, null, false, 0.0, null);

        verify(mockPlayer, times(1)).performCommand("lp user TesterGoose parent add vip");
        verify(mockPlayer, times(1)).performCommand("give TesterGoose diamond 5");
        verify(mockPlayer, times(1)).performCommand("say Welcome TesterGoose!");
    }

    @Test
    @DisplayName("CommandActionHandler executes a list of commands via 'command' key as a list")
    void testCommandKeyAsList() {
        CommandActionHandler handler = new CommandActionHandler();
        Map<String, Object> rawAction = new HashMap<>();
        rawAction.put("type", "command");
        rawAction.put("command", List.of(
                "fly %player_name% on",
                "heal %player_name%"
        ));
        rawAction.put("execute-from-console", false);

        handler.execute(mockPlayer, "board1", "btn1", "scene1", "action1", rawAction, null, false, 0.0, null);

        verify(mockPlayer, times(1)).performCommand("fly TesterGoose on");
        verify(mockPlayer, times(1)).performCommand("heal TesterGoose");
    }

    @Test
    @DisplayName("SendMessageActionHandler sends multiple messages in sequence")
    void testMultipleMessagesList() {
        SendMessageActionHandler handler = new SendMessageActionHandler();
        Map<String, Object> rawAction = new HashMap<>();
        rawAction.put("type", "send_message");
        rawAction.put("formatting", "minimessage");
        rawAction.put("messages", List.of(
                "<green>Message line 1 for %player_name%</green>",
                "<yellow>Message line 2</yellow>"
        ));

        handler.execute(mockPlayer, "board1", "btn1", "scene1", "action1", rawAction, null, false, 0.0, null);

        verify(mockPlayer, times(2)).sendMessage(any(Component.class));
    }

    @Test
    @DisplayName("ActionDispatcher successfully charges Vault economy and executes commands")
    void testEconomyChargeSuccess() {
        when(mockEconomy.tryCharge(eq(playerUuid), eq(50.0), anyString()))
                .thenReturn(new ChargeResult(true, "tx_12345"));

        Map<String, Object> rawAction = new HashMap<>();
        rawAction.put("type", "command");
        rawAction.put("price", 50.0);
        rawAction.put("commands", List.of(
                "lp user %player_name% parent add vip",
                "give %player_name% diamond 5"
        ));
        rawAction.put("execute-from-console", false);

        boolean success = ActionDispatcher.execute(mockPlayer, "board1", "btn_vip", "ranks", "buy", rawAction, mockEconomy, null);

        assertTrue(success);
        verify(mockEconomy, times(1)).tryCharge(eq(playerUuid), eq(50.0), anyString());
        verify(mockPlayer, times(1)).performCommand("lp user TesterGoose parent add vip");
        verify(mockPlayer, times(1)).performCommand("give TesterGoose diamond 5");
    }

    @Test
    @DisplayName("ActionDispatcher halts execution and sends failure message when economy charge fails")
    void testEconomyChargeFailure() {
        when(mockEconomy.tryCharge(eq(playerUuid), eq(100.0), anyString()))
                .thenReturn(new ChargeResult(false, "Insufficient balance ($100.00 required)"));

        Map<String, Object> rawAction = new HashMap<>();
        rawAction.put("type", "command");
        rawAction.put("price", 100.0);
        rawAction.put("commands", List.of(
                "lp user %player_name% parent add mvp"
        ));
        rawAction.put("execute-from-console", false);

        boolean success = ActionDispatcher.execute(mockPlayer, "board1", "btn_mvp", "ranks", "buy", rawAction, mockEconomy, null);

        assertFalse(success);
        verify(mockEconomy, times(1)).tryCharge(eq(playerUuid), eq(100.0), anyString());
        // Verify commands were NOT executed
        verify(mockPlayer, never()).performCommand(anyString());
        // Verify player received failure message
        verify(mockPlayer, times(1)).sendMessage(contains("Transaction failed: Insufficient balance"));
    }

    @Test
    @DisplayName("Updated showcase.yml parses cleanly with Vault pricing and command lists")
    void testShowcaseYmlParsesSuccessfully() {
        File showcaseFile = new File("src/main/resources/boards/showcase.yml");
        assertTrue(showcaseFile.exists(), "src/main/resources/boards/showcase.yml must exist on disk");

        BoardYamlParser parser = new BoardYamlParser();
        BoardConfig config = parser.parse(showcaseFile);

        assertNotNull(config, "Parsed BoardConfig must not be null");
        assertTrue(config.scenes().containsKey("ranks"), "showcase.yml must contain 'ranks' scene");
        assertTrue(config.scenes().containsKey("default"), "showcase.yml must contain 'default' scene");
        assertTrue(config.scenes().containsKey("gallery"), "showcase.yml must contain 'gallery' scene");
    }
}
