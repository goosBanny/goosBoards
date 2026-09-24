package me.goosbanny.goosboards.command;

import me.goosbanny.goosboards.config.ConfigReloadManager;
import me.goosbanny.goosboards.core.logging.DebugLogger;
import me.goosbanny.goosboards.core.logging.MessageService;
import me.goosbanny.goosboards.core.metrics.MetricsCollector;

import me.goosbanny.goosboards.raycast.spatial.impl.ChunkBucketSpatialIndex;
import me.goosbanny.goosboards.scene.BoardConfig;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GoosBoardCommandTest {

    private ConfigReloadManager mockReloadManager;
    private MetricsCollector metricsCollector;
    private MessageService mockMessageService;
    private GoosBoardCommand command;
    private CommandSender mockSender;
    private Command mockBukkitCommand;
    private List<String> sentMessages;

    @BeforeEach
    void setUp() {
        mockReloadManager = mock(ConfigReloadManager.class);
        metricsCollector = new MetricsCollector();
        mockMessageService = mock(MessageService.class);
        BoardSelectionManager selectionManager = new BoardSelectionManager();
        ChunkBucketSpatialIndex spatialIndex = new ChunkBucketSpatialIndex();
        File tempDir = new File(System.getProperty("java.io.tmpdir"), "goosboards-test-" + System.nanoTime());
        tempDir.mkdirs();

        command = new GoosBoardCommand(
                mockReloadManager,
                metricsCollector,
                mockMessageService,
                selectionManager,
                spatialIndex,
                tempDir
        );

        mockSender = mock(CommandSender.class);
        mockBukkitCommand = mock(Command.class);
        sentMessages = new ArrayList<>();

        when(mockSender.hasPermission("goosboards.admin")).thenReturn(true);
        when(mockSender.hasPermission("interactiveboard.admin")).thenReturn(false);
        doAnswer(inv -> {
            sentMessages.add(inv.getArgument(0));
            return null;
        }).when(mockSender).sendMessage(anyString());
    }

    @Test
    @DisplayName("Users without any admin permission are rejected with localized message")
    void testPermissionDenied() {
        when(mockSender.hasPermission("goosboards.admin")).thenReturn(false);
        when(mockSender.hasPermission("interactiveboard.admin")).thenReturn(false);

        boolean result = command.onCommand(mockSender, mockBukkitCommand, "goosboard", new String[]{"reload"});
        assertTrue(result);
        verify(mockMessageService).send(mockSender, "no-permission");
        verifyNoInteractions(mockReloadManager);
    }

    @Test
    @DisplayName("Legacy interactiveboard.admin permission also grants access")
    void testLegacyPermissionGranted() {
        when(mockSender.hasPermission("goosboards.admin")).thenReturn(false);
        when(mockSender.hasPermission("interactiveboard.admin")).thenReturn(true);
        when(mockReloadManager.reload()).thenReturn(new ConfigReloadManager.ReloadResult(true, 0, null));

        boolean result = command.onCommand(mockSender, mockBukkitCommand, "ib", new String[]{"reload"});
        assertTrue(result);
        verify(mockReloadManager).reload();
    }

    @Test
    @DisplayName("Empty arguments prints full help message including all new subcommands")
    void testEmptyArgsShowsHelp() {
        boolean result = command.onCommand(mockSender, mockBukkitCommand, "goosboard", new String[]{});
        assertTrue(result);

        verify(mockMessageService).send(mockSender, "help-header");
        verify(mockMessageService).send(eq(mockSender), eq("help-reload"),      anyMap());
        verify(mockMessageService).send(eq(mockSender), eq("help-debug"),       anyMap());
        verify(mockMessageService).send(eq(mockSender), eq("help-list"),        anyMap());
        verify(mockMessageService).send(eq(mockSender), eq("help-create"),      anyMap());
        verify(mockMessageService).send(eq(mockSender), eq("help-cancel"),      anyMap());
        verify(mockMessageService).send(eq(mockSender), eq("help-delete"),      anyMap());
        verify(mockMessageService).send(eq(mockSender), eq("help-reset"),       anyMap());
        verify(mockMessageService).send(eq(mockSender), eq("help-teleport"),    anyMap());
        verify(mockMessageService).send(eq(mockSender), eq("help-fonts"),       anyMap());
        verify(mockMessageService).send(eq(mockSender), eq("help-name"),        anyMap());
        verify(mockMessageService).send(eq(mockSender), eq("help-coordinates"), anyMap());
        verify(mockMessageService).send(eq(mockSender), eq("help-trigger"),     anyMap());
        verify(mockMessageService).send(eq(mockSender), eq("help-showcase"),    anyMap());
        verify(mockMessageService).send(mockSender, "help-footer");
        verifyNoInteractions(mockReloadManager);
    }

    @Test
    @DisplayName("Unknown subcommand sends unknown message and displays help")
    void testUnknownSubcommand() {
        boolean result = command.onCommand(mockSender, mockBukkitCommand, "gb", new String[]{"foobar"});
        assertTrue(result);

        verify(mockMessageService).send(eq(mockSender), eq("unknown-command"), anyMap());
        verify(mockMessageService).send(mockSender, "help-header");
    }

    @Test
    @DisplayName("/goosboard reload triggers reload and reports success")
    void testReloadSuccess() {
        when(mockReloadManager.reload()).thenReturn(new ConfigReloadManager.ReloadResult(true, 3, null));

        boolean result = command.onCommand(mockSender, mockBukkitCommand, "goosboard", new String[]{"reload"});
        assertTrue(result);

        verify(mockMessageService).send(mockSender, "reload-start");
        verify(mockReloadManager).reload();
        ArgumentCaptor<Map<String, String>> captor = ArgumentCaptor.forClass(Map.class);
        verify(mockMessageService).send(eq(mockSender), eq("reload-success"), captor.capture());
        assertEquals("3", captor.getValue().get("boards"));
        assertNotNull(captor.getValue().get("time"));
    }

    @Test
    @DisplayName("/goosboard reload --silent or -s suppresses start and success messages")
    void testReloadSilentFlag() {
        when(mockReloadManager.reload()).thenReturn(new ConfigReloadManager.ReloadResult(true, 2, null));

        boolean result = command.onCommand(mockSender, mockBukkitCommand, "gb", new String[]{"reload", "--silent"});
        assertTrue(result);

        verify(mockReloadManager).reload();
        verify(mockMessageService, never()).send(mockSender, "reload-start");
        verify(mockMessageService, never()).send(eq(mockSender), eq("reload-success"), anyMap());

        // Also test short flag -s
        boolean resultShort = command.onCommand(mockSender, mockBukkitCommand, "gb", new String[]{"reload", "-s"});
        assertTrue(resultShort);
        verify(mockReloadManager, times(2)).reload();
    }

    @Test
    @DisplayName("/goosboard reload reports failure and rollback on error")
    void testReloadFailure() {
        when(mockReloadManager.reload()).thenReturn(new ConfigReloadManager.ReloadResult(false, 0, "Invalid YAML syntax"));

        boolean result = command.onCommand(mockSender, mockBukkitCommand, "goosboard", new String[]{"reload"});
        assertTrue(result);

        verify(mockReloadManager).reload();
        ArgumentCaptor<Map<String, String>> captor = ArgumentCaptor.forClass(Map.class);
        verify(mockMessageService).send(eq(mockSender), eq("reload-failure"), captor.capture());
        assertEquals("Invalid YAML syntax", captor.getValue().get("error"));
        verify(mockMessageService).send(mockSender, "reload-rollback");
    }

    @Test
    @DisplayName("/goosboard debug with no board sends usage error")
    void testDebugMissingBoardArg() {
        boolean result = command.onCommand(mockSender, mockBukkitCommand, "goosboard", new String[]{"debug"});
        assertTrue(result);
        verify(mockMessageService).send(eq(mockSender), eq("unknown-command"), anyMap());
    }

    @Test
    @DisplayName("/goosboard debug <board> reports board-not-found for unknown board")
    void testDebugNotFound() {
        when(mockReloadManager.getBoard("nonexistent")).thenReturn(null);

        boolean result = command.onCommand(mockSender, mockBukkitCommand, "goosboard", new String[]{"debug", "nonexistent"});
        assertTrue(result);
        verify(mockMessageService).send(eq(mockSender), eq("board-not-found"), anyMap());
    }

    @Test
    @DisplayName("/goosboard debug <board> displays full diagnostics metrics")
    void testDebugMetricsDisplay() {
        String boardId = "spawn-board";
        BoardConfig mockBoard = new BoardConfig(
                new BoardConfig.BoardSettings(true, true),
                Collections.emptyMap(),
                Collections.emptyMap()
        );
        when(mockReloadManager.getBoard(boardId)).thenReturn(mockBoard);

        metricsCollector.recordRenderTime(boardId, 1_500_000L);
        metricsCollector.recordRenderTime(boardId, 3_500_000L);
        metricsCollector.recordPacketsSent(boardId, 25);
        metricsCollector.setActiveViewers(boardId, 42);
        metricsCollector.setCacheHitRatioSupplier(() -> 98.5);

        boolean result = command.onCommand(mockSender, mockBukkitCommand, "goosboard", new String[]{"debug", boardId});
        assertTrue(result);

        verify(mockMessageService).send(mockSender, "debug-header");
        verify(mockMessageService).send(eq(mockSender), eq("debug-board-id"), anyMap());
        verify(mockMessageService).send(eq(mockSender), eq("debug-latency"), anyMap());
        verify(mockMessageService).send(eq(mockSender), eq("debug-packet-rate"), anyMap());
        verify(mockMessageService).send(eq(mockSender), eq("debug-cache-hit"), anyMap());
        verify(mockMessageService).send(eq(mockSender), eq("debug-viewers"), anyMap());
        verify(mockMessageService).send(mockSender, "debug-footer");
    }

    @Test
    @DisplayName("/goosboard debug <board> --raw outputs compact single-line metrics")
    void testDebugRawFlag() {
        String boardId = "spawn-board";
        BoardConfig mockBoard = new BoardConfig(
                new BoardConfig.BoardSettings(true, true),
                Collections.emptyMap(),
                Collections.emptyMap()
        );
        when(mockReloadManager.getBoard(boardId)).thenReturn(mockBoard);

        boolean result = command.onCommand(mockSender, mockBukkitCommand, "goosboard", new String[]{"debug", boardId, "--raw"});
        assertTrue(result);

        assertTrue(sentMessages.stream().anyMatch(m -> m.startsWith("board=spawn-board;min_ms=")));
        verifyNoInteractions(mockMessageService);
    }

    @Test
    @DisplayName("/goosboard debugmode toggles global debug logger state")
    void testDebugModeToggle() {
        try {
            DebugLogger.setEnabled(false);
            boolean res = command.onCommand(mockSender, mockBukkitCommand, "goosboard", new String[]{"debugmode"});
            assertTrue(res);
            assertTrue(DebugLogger.isEnabled());
            assertTrue(sentMessages.stream().anyMatch(m -> m.contains("ENABLED")));

            // Explicit off
            res = command.onCommand(mockSender, mockBukkitCommand, "goosboard", new String[]{"debugmode", "off"});
            assertTrue(res);
            assertFalse(DebugLogger.isEnabled());
            assertTrue(sentMessages.stream().anyMatch(m -> m.contains("DISABLED")));

            // Explicit on
            res = command.onCommand(mockSender, mockBukkitCommand, "goosboard", new String[]{"debug-mode", "on"});
            assertTrue(res);
            assertTrue(DebugLogger.isEnabled());
        } finally {
            DebugLogger.setEnabled(false);
        }
    }

    @Test
    @DisplayName("/goosboard list outputs active boards and respects empty state")
    void testListCommand() {
        // 1. Empty state
        when(mockReloadManager.getActiveBoards()).thenReturn(Collections.emptyMap());
        boolean resEmpty = command.onCommand(mockSender, mockBukkitCommand, "goosboard", new String[]{"list"});
        assertTrue(resEmpty);
        verify(mockMessageService).send(mockSender, "list-empty");

        // 2. Active boards
        when(mockReloadManager.getActiveBoards()).thenReturn(Map.of(
                "hub", new BoardConfig(null, Collections.emptyMap(), Collections.emptyMap()),
                "shop", new BoardConfig(null, Collections.emptyMap(), Collections.emptyMap())
        ));
        boolean resActive = command.onCommand(mockSender, mockBukkitCommand, "goosboard", new String[]{"list"});
        assertTrue(resActive);
        verify(mockMessageService).send(eq(mockSender), eq("list-header"), anyMap());
        verify(mockMessageService, times(2)).send(eq(mockSender), eq("list-item"), anyMap());
    }

    @Test
    @DisplayName("/goosboard list --verbose outputs detailed display breakdowns")
    void testListVerboseFlag() {
        BoardConfig.DisplayDefinition def = new BoardConfig.DisplayDefinition(
                "d1", "world", 2, 2, null, "south", 32.0, 16.0
        );
        when(mockReloadManager.getActiveBoards()).thenReturn(Map.of(
                "hub", new BoardConfig(null, Map.of("d1", def), Collections.emptyMap())
        ));

        boolean result = command.onCommand(mockSender, mockBukkitCommand, "goosboard", new String[]{"list", "-v"});
        assertTrue(result);
        assertTrue(sentMessages.stream().anyMatch(m -> m.contains("d1") && m.contains("world") && m.contains("2x2 blocks")));
    }

    @Test
    @DisplayName("/gb delete sends board-not-found for nonexistent board")
    void testDeleteNotFound() {
        when(mockReloadManager.getBoard("ghost")).thenReturn(null);
        boolean result = command.onCommand(mockSender, mockBukkitCommand, "gb", new String[]{"delete", "ghost"});
        assertTrue(result);
        verify(mockMessageService).send(eq(mockSender), eq("board-not-found"), anyMap());
        verify(mockReloadManager, never()).reload();
    }

    @Test
    @DisplayName("/gb reset sends board-not-found for nonexistent board")
    void testResetNotFound() {
        when(mockReloadManager.getBoard("ghost")).thenReturn(null);
        boolean result = command.onCommand(mockSender, mockBukkitCommand, "gb", new String[]{"reset", "ghost"});
        assertTrue(result);
        verify(mockMessageService).send(eq(mockSender), eq("board-not-found"), anyMap());
        verify(mockReloadManager, never()).reload();
    }

    @Test
    @DisplayName("/gb create without args starts selection or shows already-active for non-player")
    void testCreateNonPlayer() {
        // Console sender is not a Player
        boolean result = command.onCommand(mockSender, mockBukkitCommand, "gb", new String[]{"create"});
        assertTrue(result);
        verify(mockMessageService).send(mockSender, "selection-player-only");
    }

    @Test
    @DisplayName("/gb cancel without active session sends selection-not-active for non-player")
    void testCancelNonPlayer() {
        boolean result = command.onCommand(mockSender, mockBukkitCommand, "gb", new String[]{"cancel"});
        assertTrue(result);
        verify(mockMessageService).send(mockSender, "selection-player-only");
    }

    @Test
    @DisplayName("/gb fonts works for console sender and does not throw")
    void testFontsCommand() {
        boolean result = command.onCommand(mockSender, mockBukkitCommand, "gb", new String[]{"fonts"});
        assertTrue(result);
        // Should always send the fonts header
        verify(mockMessageService, atLeastOnce()).send(eq(mockSender), eq("fonts-header"), anyMap());
    }

    @Test
    @DisplayName("/gb name requires in-game player sender")
    void testNameNonPlayer() {
        boolean result = command.onCommand(mockSender, mockBukkitCommand, "gb", new String[]{"name"});
        assertTrue(result);
        verify(mockMessageService).send(mockSender, "selection-player-only");
    }

    @Test
    @DisplayName("/gb coordinates requires in-game player sender")
    void testCoordinatesNonPlayer() {
        boolean result = command.onCommand(mockSender, mockBukkitCommand, "gb", new String[]{"coordinates"});
        assertTrue(result);
        verify(mockMessageService).send(mockSender, "selection-player-only");
    }

    @Test
    @DisplayName("/gb trigger with missing args sends unknown-command")
    void testTriggerMissingArgs() {
        boolean result = command.onCommand(mockSender, mockBukkitCommand, "gb", new String[]{"trigger", "display1"});
        assertTrue(result);
        verify(mockMessageService).send(eq(mockSender), eq("unknown-command"), anyMap());
    }

    @Test
    @DisplayName("Tab completion auto-completes all new subcommands and flags")
    void testTabCompletionNewSubcommands() {
        when(mockReloadManager.getActiveBoards()).thenReturn(Map.of("hub-board", mock(BoardConfig.class)));

        // Level 1: all subcommands including new ones
        List<String> subs = command.onTabComplete(mockSender, mockBukkitCommand, "goosboard", new String[]{""});
        assertTrue(subs.contains("reload"));
        assertTrue(subs.contains("debug"));
        assertTrue(subs.contains("list"));
        assertTrue(subs.contains("create"));
        assertTrue(subs.contains("cancel"));
        assertTrue(subs.contains("delete"));
        assertTrue(subs.contains("reset"));
        assertTrue(subs.contains("teleport"));
        assertTrue(subs.contains("fonts"));
        assertTrue(subs.contains("name"));
        assertTrue(subs.contains("coordinates"));
        assertTrue(subs.contains("trigger"));
        assertTrue(subs.contains("showcase"));

        // Level 2: delete/reset complete with board ids
        List<String> deleteBoards = command.onTabComplete(mockSender, mockBukkitCommand, "goosboard", new String[]{"delete", "h"});
        assertTrue(deleteBoards.contains("hub-board"));

        // Level 2: teleport completes with display ids
        List<String> tpBoards = command.onTabComplete(mockSender, mockBukkitCommand, "goosboard", new String[]{"teleport", ""});
        assertNotNull(tpBoards);

        // Level 2: reload flags
        List<String> reloadFlags = command.onTabComplete(mockSender, mockBukkitCommand, "goosboard", new String[]{"reload", "-"});
        assertTrue(reloadFlags.contains("--silent"));
        assertTrue(reloadFlags.contains("-s"));

        // Level 2: list flags
        List<String> listFlags = command.onTabComplete(mockSender, mockBukkitCommand, "goosboard", new String[]{"list", "-"});
        assertTrue(listFlags.contains("--verbose"));
        assertTrue(listFlags.contains("-v"));

        // Level 3: debug raw flag
        List<String> rawFlags = command.onTabComplete(mockSender, mockBukkitCommand, "goosboard", new String[]{"debug", "hub-board", "-"});
        assertTrue(rawFlags.contains("--raw"));
        assertTrue(rawFlags.contains("-r"));

        // Non-admin gets empty list
        when(mockSender.hasPermission("goosboards.admin")).thenReturn(false);
        when(mockSender.hasPermission("interactiveboard.admin")).thenReturn(false);
        List<String> unauthorized = command.onTabComplete(mockSender, mockBukkitCommand, "goosboard", new String[]{""});
        assertTrue(unauthorized.isEmpty());
    }

    @Test
    @DisplayName("/gb showcase requires in-game player")
    void testShowcaseNonPlayer() {
        boolean result = command.onCommand(mockSender, mockBukkitCommand, "gb", new String[]{"showcase"});
        assertTrue(result);
        verify(mockMessageService).send(mockSender, "selection-player-only");
    }

    @Test
    @DisplayName("/gb showcase automatically computes 8x8 geometry and writes showcase board for player")
    void testShowcasePlayerAutoGeometry() {
        Player mockPlayer = mock(Player.class);
        when(mockPlayer.hasPermission("goosboards.admin")).thenReturn(true);
        when(mockPlayer.getUniqueId()).thenReturn(UUID.randomUUID());
        World mockWorld = mock(World.class);
        when(mockWorld.getName()).thenReturn("world");
        when(mockPlayer.getWorld()).thenReturn(mockWorld);
        Location playerLoc = new Location(mockWorld, 10.0, 64.0, 20.0, 0f, 0f); // facing South (+Z)
        when(mockPlayer.getLocation()).thenReturn(playerLoc);
        when(mockReloadManager.reload()).thenReturn(new ConfigReloadManager.ReloadResult(true, 1, null));

        boolean result = command.onCommand(mockPlayer, mockBukkitCommand, "gb", new String[]{"showcase", "test-showcase"});
        assertTrue(result);

        verify(mockReloadManager).reload();
        verify(mockPlayer).sendMessage(contains("Created 8x8 interactive showcase board"));
    }

    @Test
    @DisplayName("/gb delete removes .yaml variant file as well as .yml")
    void testDeleteYamlFile() throws Exception {
        File boardsFolder = command.getBoardsFolder();
        File yamlFile = new File(boardsFolder, "custom-board.yaml");
        yamlFile.createNewFile();
        assertTrue(yamlFile.exists());

        when(mockReloadManager.getActiveBoards()).thenReturn(Map.of(
                "custom-board", new BoardConfig(null, Collections.emptyMap(), Collections.emptyMap())
        ));
        when(mockReloadManager.getBoard("custom-board")).thenReturn(
                new BoardConfig(null, Collections.emptyMap(), Collections.emptyMap())
        );

        boolean result = command.onCommand(mockSender, mockBukkitCommand, "gb", new String[]{"delete", "custom-board"});
        assertTrue(result);
        assertFalse(yamlFile.exists(), "custom-board.yaml must have been deleted");
        verify(mockReloadManager).reload();
    }

    @Test
    @DisplayName("/gb reset resets active scene and triggers reload")
    void testResetActiveScene() {
        when(mockReloadManager.getActiveBoards()).thenReturn(Map.of(
                "hub-board", new BoardConfig(null, Collections.emptyMap(), Collections.emptyMap())
        ));
        when(mockReloadManager.getBoard("hub-board")).thenReturn(
                new BoardConfig(null, Collections.emptyMap(), Collections.emptyMap())
        );

        boolean result = command.onCommand(mockSender, mockBukkitCommand, "gb", new String[]{"reset", "hub-board"});
        assertTrue(result);
        verify(mockReloadManager).resetActiveScene("hub-board");
        verify(mockReloadManager).reload();
    }
}