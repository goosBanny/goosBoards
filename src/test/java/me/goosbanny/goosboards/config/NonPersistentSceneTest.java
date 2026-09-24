package me.goosbanny.goosboards.config;

import me.goosbanny.goosboards.core.metrics.MetricsCollector;

import me.goosbanny.goosboards.config.ConfigManager;
import me.goosbanny.goosboards.raycast.spatial.impl.ChunkBucketSpatialIndex;
import me.goosbanny.goosboards.render.cache.RenderStateCache;
import me.goosbanny.goosboards.scene.parser.BoardYamlParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class NonPersistentSceneTest {

    @TempDir
    File tempDir;

    private ConfigReloadManager reloadManager;

    @BeforeEach
    void setUp() throws IOException {
        File boardsFolder = new File(tempDir, "boards");
        boardsFolder.mkdirs();

        String boardYaml = """
                settings:
                  dithering: true
                  persistent: false
                  reset-on-rejoin: true
                  reset-on-reload: true
                  reset-on-disappear: true
                displays:
                  main:
                    world: world
                    width: 2
                    height: 2
                    top-left:
                      x: 0.0
                      y: 64.0
                      z: 0.0
                    direction: north
                scenes:
                  default:
                    background:
                      type: background
                      position: "0 0"
                      size: "100% 100%"
                      color: "#000000"
                  gallery:
                    background:
                      type: background
                      position: "0 0"
                      size: "100% 100%"
                      color: "#FFFFFF"
                """;
        Files.writeString(new File(boardsFolder, "showcase.yml").toPath(), boardYaml);

        ConfigManager configManager = mock(ConfigManager.class);
        when(configManager.getBoardsFolder()).thenReturn(boardsFolder);

        reloadManager = new ConfigReloadManager(
                configManager,
                new BoardYamlParser(),
                mock(RenderStateCache.class),
                new ChunkBucketSpatialIndex(),
                new MetricsCollector(),
                Logger.getLogger("NonPersistentTest")
        );

        reloadManager.reload();
    }

    @Test
    @DisplayName("Non-persistent board isolates per-player scene in memory and resets on rejoin or reload")
    void testNonPersistentSceneLifecycle() {
        UUID player1 = UUID.randomUUID();
        UUID player2 = UUID.randomUUID();
        String boardId = "showcase";

        // Initial scene is default
        assertFalse(reloadManager.hasPerPlayerScenes(boardId));
        assertEquals("default", reloadManager.getActiveSceneId(boardId, player1));
        assertEquals("default", reloadManager.getActiveSceneId(boardId, player2));

        // Player 1 switches to gallery
        reloadManager.setActiveScene(boardId, player1, "gallery");
        assertTrue(reloadManager.hasPerPlayerScenes(boardId));
        assertTrue(reloadManager.hasPerPlayerScene(boardId, player1));
        assertFalse(reloadManager.hasPerPlayerScene(boardId, player2));
        assertEquals("gallery", reloadManager.getActiveSceneId(boardId, player1));
        assertEquals("default", reloadManager.getActiveSceneId(boardId, player2), "Player 2 scene must remain isolated at default");

        // Player 1 triggers rejoin/disappear reset
        reloadManager.resetPlayerAllNonPersistentScenes(player1);
        assertFalse(reloadManager.hasPerPlayerScenes(boardId));
        assertEquals("default", reloadManager.getActiveSceneId(boardId, player1), "Player 1 scene must reset to default after rejoin/disappear");

        // Switch both and test reload reset
        reloadManager.setActiveScene(boardId, player1, "gallery");
        reloadManager.setActiveScene(boardId, player2, "gallery");
        assertTrue(reloadManager.hasPerPlayerScenes(boardId));
        reloadManager.resetAllNonPersistentScenes();

        assertFalse(reloadManager.hasPerPlayerScenes(boardId));
        assertEquals("default", reloadManager.getActiveSceneId(boardId, player1), "Player 1 must reset to default on reload");
        assertEquals("default", reloadManager.getActiveSceneId(boardId, player2), "Player 2 must reset to default on reload");
    }
}