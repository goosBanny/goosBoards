package me.goosbanny.goosboards.config;

import me.goosbanny.goosboards.core.metrics.MetricsCollector;

import me.goosbanny.goosboards.config.ConfigManager;
import me.goosbanny.goosboards.protocol.TextDisplayEntityTracker;
import me.goosbanny.goosboards.protocol.VirtualEntityTracker;
import me.goosbanny.goosboards.raycast.spatial.impl.ChunkBucketSpatialIndex;
import me.goosbanny.goosboards.display.DisplayPlane;
import me.goosbanny.goosboards.display.ProximityTracker;
import me.goosbanny.goosboards.render.cache.RenderStateCache;
import me.goosbanny.goosboards.scene.parser.BoardYamlParser;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

class HotReloadRehydrationTest {

    private Server mockServer;
    private Player mockPlayer;
    private World mockWorld;
    private VirtualEntityTracker mockVirtualTracker;
    private TextDisplayEntityTracker mockTextTracker;
    private final java.util.concurrent.atomic.AtomicBoolean proximityUpdated = new java.util.concurrent.atomic.AtomicBoolean(false);
    private ProximityTracker testProximityTracker;
    private ConfigReloadManager reloadManager;
    private File boardsFolder;

    private static void setBukkitServer(Server server) {
        try {
            Field field = Bukkit.class.getDeclaredField("server");
            field.setAccessible(true);
            field.set(null, server);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @BeforeEach
    void setUp() throws Exception {
        boardsFolder = new File("build/tmp/test-reload-" + System.nanoTime() + "/boards");
        boardsFolder.mkdirs();

        mockServer = mock(Server.class);
        when(mockServer.getLogger()).thenReturn(Logger.getAnonymousLogger());
        setBukkitServer(mockServer);

        UUID worldUid = UUID.randomUUID();
        mockWorld = mock(World.class);
        when(mockWorld.getName()).thenReturn("world");
        when(mockWorld.getUID()).thenReturn(worldUid);

        mockPlayer = mock(Player.class);
        when(mockPlayer.isOnline()).thenReturn(true);
        when(mockPlayer.getUniqueId()).thenReturn(UUID.randomUUID());
        when(mockPlayer.getWorld()).thenReturn(mockWorld);
        when(mockPlayer.getLocation()).thenReturn(new Location(mockWorld, 10.0, 64.0, 20.0));

        when(mockServer.getOnlinePlayers()).thenAnswer(inv -> Collections.singletonList(mockPlayer));

        mockVirtualTracker = mock(VirtualEntityTracker.class);
        mockTextTracker = mock(TextDisplayEntityTracker.class);
        proximityUpdated.set(false);
        testProximityTracker = new ProximityTracker(new ChunkBucketSpatialIndex(), 32.0) {
            @Override
            public void updatePlayerProximity(Player player) {
                proximityUpdated.set(true);
            }
        };

        ConfigManager configManager = mock(ConfigManager.class);
        when(configManager.getBoardsFolder()).thenReturn(boardsFolder);

        reloadManager = new ConfigReloadManager(
                configManager,
                new BoardYamlParser(),
                mock(RenderStateCache.class),
                new ChunkBucketSpatialIndex(),
                new MetricsCollector(),
                Logger.getLogger("HotReloadTest")
        );

        reloadManager.setVirtualEntityTracker(mockVirtualTracker);
        reloadManager.setTextDisplayTracker(mockTextTracker);
        reloadManager.setProximityTracker(testProximityTracker);
    }

    @AfterEach
    void tearDown() {
        setBukkitServer(null);
    }

    @Test
    @DisplayName("Safeguard 5: Reload cleanly despawns stale rigs and rehydrates active viewers without player movement")
    void testHotReloadRehydration() throws Exception {
        String boardYaml = """
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
        Files.writeString(new File(boardsFolder, "spawn.yml").toPath(), boardYaml);

        ConfigReloadManager.ReloadResult result = reloadManager.reload();
        assertTrue(result.success());

        // 1. Despawn phase before reloading
        verify(mockVirtualTracker).despawnAll();
        verify(mockTextTracker).despawnAll();

        // 2. Rehydration phase after loading displays: re-spawns virtual rig for nearby viewer
        verify(mockVirtualTracker).spawnRig(eq(mockPlayer), any(DisplayPlane.class));

        // 3. Proximity cache immediately refreshed without waiting for movement
        assertTrue(proximityUpdated.get(), "Proximity tracker should be updated for active players");
    }
}