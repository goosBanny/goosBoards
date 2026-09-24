package me.goosbanny.goosboards.config;

import me.goosbanny.goosboards.core.metrics.MetricsCollector;

import me.goosbanny.goosboards.config.ConfigManager;
import me.goosbanny.goosboards.raycast.spatial.impl.ChunkBucketSpatialIndex;
import me.goosbanny.goosboards.render.cache.RenderStateCache;
import me.goosbanny.goosboards.scene.BoardConfig;
import me.goosbanny.goosboards.scene.parser.BoardYamlParser;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ConfigReloadTest {

    @TempDir
    File tempDir;

    private ConfigManager configManager;
    private ConfigReloadManager reloadManager;
    private RenderStateCache mockCache;
    private ChunkBucketSpatialIndex spatialIndex;
    private MetricsCollector metricsCollector;

    @BeforeEach
    void setUp() {
        File boardsFolder = new File(tempDir, "boards");
        boardsFolder.mkdirs();

        configManager = mock(ConfigManager.class);
        when(configManager.getBoardsFolder()).thenReturn(boardsFolder);

        mockCache = mock(RenderStateCache.class);
        spatialIndex = new ChunkBucketSpatialIndex();
        metricsCollector = new MetricsCollector();

        reloadManager = new ConfigReloadManager(
                configManager,
                new BoardYamlParser(),
                mockCache,
                spatialIndex,
                metricsCollector,
                Logger.getLogger("ReloadTest")
        );
    }

    @Test
    @DisplayName("Task 8.1: Valid board configuration reloads atomically and populates displays")
    void testValidStagedReload() throws IOException {
        String validBoardYaml = """
                settings:
                  dithering: true
                displays:
                  spawn-wall:
                    world: "world"
                    width: 2
                    height: 2
                    top-left:
                      x: 10.0
                      y: 64.0
                      z: 20.0
                    direction: south
                    distance: 32.0
                    interaction-distance: 16.0
                scenes:
                  main:
                    bg:
                      type: background
                      position: 0 0
                      size: 256 256
                      color: "50 50 50 255"
                """;

        File boardFile = new File(configManager.getBoardsFolder(), "test_board.yml");
        Files.writeString(boardFile.toPath(), validBoardYaml);

        ConfigReloadManager.ReloadResult result = reloadManager.reload();
        assertTrue(result.success(), "Staged reload should succeed with valid YAML");
        assertEquals(1, result.loadedBoards());

        assertNotNull(reloadManager.getBoard("test_board"));
        BoardConfig board = reloadManager.getBoard("test_board");
        assertEquals(1, board.displays().size());
        assertEquals(1, board.scenes().size());

        // Verify cache invalidation
        verify(mockCache, times(1)).invalidateAll();
    }

    @Test
    @DisplayName("Task 8.1 / 8.2: Malformed board fails gracefully leaving existing boards untouched")
    void testCorruptedReloadDoesNotDropActiveBoards() throws IOException {
        // 1. Initial valid board setup
        String validBoardYaml = """
                displays:
                  main-disp:
                    world: "world"
                    width: 2
                    height: 2
                scenes:
                  scene-1:
                    bg:
                      type: background
                      position: 0 0
                      size: 256 256
                      color: "10 10 10 255"
                """;
        File board1 = new File(configManager.getBoardsFolder(), "good_board.yml");
        Files.writeString(board1.toPath(), validBoardYaml);

        ConfigReloadManager.ReloadResult initialResult = reloadManager.reload();
        assertTrue(initialResult.success());
        assertEquals(1, reloadManager.getActiveBoards().size());

        // 2. Introduce a corrupt / malformed board
        String corruptYaml = """
                displays:
                  broken-disp:
                    world: "world"
                scenes:
                  # Missing components and invalid indentation
                  broken: [][ invalid yaml !!
                """;
        File board2 = new File(configManager.getBoardsFolder(), "broken_board.yml");
        Files.writeString(board2.toPath(), corruptYaml);

        // 3. Attempt reload
        ConfigReloadManager.ReloadResult failResult = reloadManager.reload();
        assertFalse(failResult.success(), "Reload should fail when corrupt board is present");
        assertNotNull(failResult.errorMessage(), "Actionable error message must be provided");

        // 4. Verify previous active boards remain completely untouched
        assertEquals(1, reloadManager.getActiveBoards().size(), "Previous board count must remain intact");
        assertNotNull(reloadManager.getBoard("good_board"), "Original live board must still be accessible");
    }
}