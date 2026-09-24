package me.goosbanny.goosboards.config;

import me.goosbanny.goosboards.GoosBoards;
import me.goosbanny.goosboards.core.logging.DebugLogger;
import me.goosbanny.goosboards.core.logging.MessageService;
import me.goosbanny.goosboards.core.metrics.MetricsCollector;
import me.goosbanny.goosboards.storage.StorageManager;

import me.goosbanny.goosboards.config.ConfigManager;
import me.goosbanny.goosboards.interaction.InteractionRouter;
import me.goosbanny.goosboards.media.DiskMediaCache;
import me.goosbanny.goosboards.protocol.TextDisplayEntityTracker;
import me.goosbanny.goosboards.protocol.VirtualEntityTracker;
import me.goosbanny.goosboards.raycast.spatial.impl.ChunkBucketSpatialIndex;
import me.goosbanny.goosboards.display.DisplayPlane;
import me.goosbanny.goosboards.raycast.spatial.DisplaySpatialIndex;
import me.goosbanny.goosboards.display.ProximityTracker;
import me.goosbanny.goosboards.raycast.Vector3d;
import me.goosbanny.goosboards.protocol.packet.BatchedMapSender;
import me.goosbanny.goosboards.render.BoardRenderEngine;
import me.goosbanny.goosboards.integration.placeholder.PlaceholderService;
import me.goosbanny.goosboards.display.DistanceLodTracker;
import me.goosbanny.goosboards.render.cache.RenderStateCache;
import me.goosbanny.goosboards.scene.BoardConfig;
import me.goosbanny.goosboards.scene.parser.BoardYamlParser;
import me.goosbanny.goosboards.scene.component.visual.GifComponent;
import me.goosbanny.goosboards.scene.component.visual.Head2DComponent;
import me.goosbanny.goosboards.scene.component.visual.ImageComponent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Production staged reload manager ensuring zero-downtime atomic swaps.
 * Validates board YAML definitions into an isolated staging map before applying
 * changes.
 * On reload success, despawns existing virtual frames and immediately
 * rehydrates online viewers
 * to prevent blank boards or ghost frames without requiring player movement.
 */
public class ConfigReloadManager {

    public record ReloadResult(boolean success, int loadedBoards, String errorMessage) {
    }

    private final ConfigManager configManager;
    private final BoardYamlParser yamlParser;
    private final RenderStateCache renderStateCache;
    private final DisplaySpatialIndex spatialIndex;
    private final MetricsCollector metricsCollector;
    private final Logger logger;

    private VirtualEntityTracker virtualEntityTracker;
    private TextDisplayEntityTracker textDisplayTracker;
    private ProximityTracker proximityTracker;
    private BoardRenderEngine renderEngine;
    private MessageService messageService;
    private InteractionRouter interactionRouter;

    private final AtomicReference<Map<String, BoardConfig>> activeBoards = new AtomicReference<>(
            Collections.emptyMap());
    private final Map<UUID, String> displayToBoardMap = new ConcurrentHashMap<>();
    private final Map<UUID, DisplayPlane> displayPlaneMap = new ConcurrentHashMap<>();
    private final Set<UUID> registeredDisplayIds = ConcurrentHashMap.newKeySet();

    public ConfigReloadManager(
            ConfigManager configManager,
            BoardYamlParser yamlParser,
            RenderStateCache renderStateCache,
            DisplaySpatialIndex spatialIndex,
            MetricsCollector metricsCollector,
            Logger logger) {
        this.configManager = Objects.requireNonNull(configManager, "configManager");
        this.yamlParser = Objects.requireNonNull(yamlParser, "yamlParser");
        this.renderStateCache = Objects.requireNonNull(renderStateCache, "renderStateCache");
        this.spatialIndex = Objects.requireNonNull(spatialIndex, "spatialIndex");
        this.metricsCollector = Objects.requireNonNull(metricsCollector, "metricsCollector");
        this.logger = logger != null ? logger : Logger.getLogger("GoosBoards");
    }

    public void setVirtualEntityTracker(VirtualEntityTracker tracker) {
        this.virtualEntityTracker = tracker;
    }

    public void setTextDisplayTracker(TextDisplayEntityTracker tracker) {
        this.textDisplayTracker = tracker;
    }

    public void setProximityTracker(ProximityTracker tracker) {
        this.proximityTracker = tracker;
    }

    public void setRenderEngine(BoardRenderEngine engine) {
        this.renderEngine = engine;
    }

    public VirtualEntityTracker getVirtualEntityTracker() {
        return this.virtualEntityTracker;
    }

    public BoardRenderEngine getRenderEngine() {
        return this.renderEngine;
    }

    public void setMessageService(MessageService messageService) {
        this.messageService = messageService;
    }

    public void setInteractionRouter(InteractionRouter interactionRouter) {
        this.interactionRouter = interactionRouter;
    }

    /**
     * Executes staged reload. Validates all board configurations in an isolated
     * staging map.
     * If validation fails on any file, rolls back without modifying live boards.
     */
    public ReloadResult reload() {
        // 1. Update and reload global config.yml, messages, and hover states
        configManager.updateAndReloadConfig();
        if (messageService != null) {
            messageService.updateAndReload();
        }
        if (interactionRouter != null) {
            interactionRouter.clearHoverStates();
        }

        if (configManager != null && configManager.getConfig() != null) {
            var cfg = configManager.getConfig();
            int maxSkins = cfg.getInt("media-cache.max-cached-skins", DiskMediaCache.DEFAULT_MAX_SKINS);
            int maxImages = cfg.getInt("media-cache.max-cached-images", DiskMediaCache.DEFAULT_MAX_IMAGES);
            int retentionDays = cfg.getInt("media-cache.retention-days", DiskMediaCache.DEFAULT_RETENTION_DAYS);
            DiskMediaCache.configureLimits(maxSkins, maxImages, retentionDays);
            DiskMediaCache.pruneCacheAsync();

            double nearDist = cfg.getDouble("performance.lod.near-distance", 6.0);
            int nearDiv = cfg.getInt("performance.lod.near-divisor", 1);
            double medDist = cfg.getDouble("performance.lod.medium-distance", 16.0);
            int medDiv = cfg.getInt("performance.lod.medium-divisor", 4);
            int farDiv = cfg.getInt("performance.lod.far-divisor", 20);
            DistanceLodTracker.configureLod(nearDist, nearDiv, medDist, medDiv, farDiv);

            int maxTiles = cfg.getInt("performance.network.max-tiles-per-player-per-tick", 64);
            boolean checkWritability = cfg.getBoolean("performance.network.check-channel-writability", true);
            BatchedMapSender.setMaxTilesPerPlayerPerTick(maxTiles);
            BatchedMapSender.setCheckChannelWritability(checkWritability);

            if (renderEngine != null) {
                renderEngine.setRenderTickInterval(cfg.getInt("performance.render-tick-interval", 1));
                renderEngine.setHoverRaycastIntervalTicks(cfg.getInt("performance.hover-raycast-interval-ticks", 1));
            }

            int maxGifFps = cfg.getInt("gif.max-fps", 20);
            int defaultGifFps = cfg.getInt("gif.default-fps", 10);
            GifComponent.setGlobalSettings(maxGifFps, defaultGifFps);

            int defaultPlaceholderRefresh = cfg.getInt("placeholders.default-refresh-ticks", 20);
            long placeholderDebounceMs = cfg.getLong("placeholders.debounce-ms",
                    cfg.getLong("rendering.placeholder-debounce-ms", 150L));
            PlaceholderService.configure(defaultPlaceholderRefresh, placeholderDebounceMs);
        }

        // 2. Stage-parse all board YAML files
        File boardsFolder = configManager.getBoardsFolder();
        File[] files = boardsFolder.listFiles((dir, name) -> name.endsWith(".yml") || name.endsWith(".yaml"));

        Map<String, BoardConfig> stagingMap = new HashMap<>();

        if (files != null) {
            for (File file : files) {
                String boardId = file.getName().replaceFirst("\\.(yml|yaml)$", "");
                try {
                    BoardConfig config = yamlParser.parse(file);
                    if (config.scenes() == null || config.scenes().isEmpty()) {
                        String err = "Board '" + boardId + "' has no scenes defined.";
                        logger.warning(err);
                        return new ReloadResult(false, 0, err);
                    }
                    stagingMap.put(boardId, config);
                } catch (Exception e) {
                    String err = "Syntax/validation error in board '" + file.getName() + "': " + e.getMessage();
                    logger.log(Level.SEVERE, err, e);
                    return new ReloadResult(false, 0, err);
                }
            }
        }

        // 3. Validation passed: Snapshot online players outside synchronized block to
        // prevent lock inversion
        List<Player> onlineSnapshot = Bukkit.getServer() != null ? new ArrayList<>(Bukkit.getOnlinePlayers())
                : Collections.emptyList();
        List<DisplayPlane> planeSnapshot;

        synchronized (this) {
            if (virtualEntityTracker != null) {
                virtualEntityTracker.despawnAll();
            }
            if (textDisplayTracker != null) {
                textDisplayTracker.despawnAll();
            }

            // 4. Atomic swap active boards
            Map<String, BoardConfig> previousBoards = activeBoards.getAndSet(Collections.unmodifiableMap(stagingMap));

            // Purge stale board and scene entries from activeSceneIds
            activeSceneIds.keySet().retainAll(stagingMap.keySet());
            activeSceneIds.entrySet().removeIf(entry -> {
                BoardConfig cfg = stagingMap.get(entry.getKey());
                return cfg == null || cfg.scenes() == null || !cfg.scenes().containsKey(entry.getValue());
            });
            resetAllNonPersistentScenes();

            // 5. Invalidate modified scenes from render state cache and asset caches
            renderStateCache.invalidateAll();
            Head2DComponent.clearCache();
            ImageComponent.clearCache();
            GifComponent.clearCache();
            PlaceholderService.clearCache();

            // 6. Update spatial index for displays
            updateSpatialIndex(stagingMap);

            // 7. Synchronize render engine with latest displays first so map IDs are
            // available
            if (renderEngine != null) {
                try {
                    renderEngine.syncDisplays();
                } catch (Throwable t) {
                    logger.log(Level.SEVERE, "Failed to sync displays during reload; rolling back to previous boards",
                            t);
                    activeBoards.set(previousBoards);
                    updateSpatialIndex(previousBoards);
                    throw (t instanceof RuntimeException re ? re : new RuntimeException(t));
                }
            }

            // Snapshot plane references under lock for stable rehydration
            planeSnapshot = new ArrayList<>(displayPlaneMap.values());
        } // Release lock before network I/O

        // 8. Hot-reload viewer rehydration: respawn virtual frames with active display
        // map IDs
        int rehydratedCount = rehydrateViewers(onlineSnapshot, planeSnapshot);

        logger.info("Successfully reloaded " + stagingMap.size() + " boards with zero downtime. Rehydrated "
                + rehydratedCount + " viewer rig(s).");
        return new ReloadResult(true, stagingMap.size(), null);
    }

    private int rehydrateViewers(List<Player> onlineSnapshot, List<DisplayPlane> planeSnapshot) {
        int rehydratedCount = 0;
        for (Player player : onlineSnapshot) {
            if (player == null || !player.isOnline()) {
                continue;
            }
            Vector3d pos = new Vector3d(player.getLocation().getX(), player.getLocation().getY(),
                    player.getLocation().getZ());
            String worldName = player.getWorld().getName();
            for (DisplayPlane plane : planeSnapshot) {
                if (!plane.worldName().equals(worldName)) {
                    continue;
                }
                Vector3d center = plane.center();
                double dx = pos.x() - center.x();
                double dy = pos.y() - center.y();
                double dz = pos.z() - center.z();
                double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
                double maxDist = plane.activationRadius() > 0 ? plane.activationRadius() : 32.0;

                if (dist <= maxDist && virtualEntityTracker != null) {
                    // Guard against duplicate spawns if async render tick already spawned the rig
                    if (virtualEntityTracker.getRig(player, plane.id()) != null) {
                        if (renderEngine != null) {
                            renderEngine.registerViewer(plane.id(), player.getUniqueId());
                        }
                        continue;
                    }
                    int[] mapIds = renderEngine != null ? renderEngine.getMapIds(plane.id()) : null;
                    if (mapIds != null) {
                        virtualEntityTracker.spawnRig(player, plane, mapIds);
                    } else {
                        virtualEntityTracker.spawnRig(player, plane);
                    }
                    if (renderEngine != null) {
                        renderEngine.registerViewer(plane.id(), player.getUniqueId());
                    }
                    rehydratedCount++;
                }
            }
            if (proximityTracker != null) {
                proximityTracker.updatePlayerProximity(player);
            }
        }
        return rehydratedCount;
    }

    private void updateSpatialIndex(Map<String, BoardConfig> boards) {
        // Unregister old displays
        for (UUID oldId : registeredDisplayIds) {
            spatialIndex.unregister(oldId);
        }
        registeredDisplayIds.clear();
        displayToBoardMap.clear();
        displayPlaneMap.clear();

        // Register new displays
        for (Map.Entry<String, BoardConfig> entry : boards.entrySet()) {
            String boardId = entry.getKey();
            BoardConfig board = entry.getValue();

            for (BoardConfig.DisplayDefinition def : board.displays().values()) {
                UUID displayId = getDisplayUuid(boardId, def.id());
                Vector3d[] vectors = getOrientationVectors(def.direction());

                DisplayPlane plane = new DisplayPlane(
                        displayId,
                        def.world(),
                        def.topLeft(),
                        def.width(),
                        def.height(),
                        vectors[0], // rightUnit
                        vectors[1], // downUnit
                        def.distance(),
                        def.interactionDistance(),
                        def.glow(),
                        def.hoverGlow(),
                        def.glowColor(),
                        def.hoverGlowColor(),
                        def.glowMode(),
                        def.shape(),
                        def.cornerRadius());

                spatialIndex.register(plane);
                registeredDisplayIds.add(displayId);
                displayToBoardMap.put(displayId, boardId);
                displayPlaneMap.put(displayId, plane);
            }
        }
    }

    public static UUID getDisplayUuid(String boardId, String displayId) {
        return UUID.nameUUIDFromBytes((boardId + ":" + displayId).getBytes(StandardCharsets.UTF_8));
    }

    private Vector3d[] getOrientationVectors(String direction) {
        if (direction == null) {
            return new Vector3d[] { new Vector3d(1, 0, 0), new Vector3d(0, -1, 0) };
        }
        return switch (direction.toLowerCase()) {
            case "north" -> new Vector3d[] { new Vector3d(-1, 0, 0), new Vector3d(0, -1, 0) };
            case "east" -> new Vector3d[] { new Vector3d(0, 0, -1), new Vector3d(0, -1, 0) };
            case "west" -> new Vector3d[] { new Vector3d(0, 0, 1), new Vector3d(0, -1, 0) };
            case "up" -> new Vector3d[] { new Vector3d(1, 0, 0), new Vector3d(0, 0, 1) };
            case "down" -> new Vector3d[] { new Vector3d(1, 0, 0), new Vector3d(0, 0, -1) };
            default -> new Vector3d[] { new Vector3d(1, 0, 0), new Vector3d(0, -1, 0) }; // south
        };
    }

    public Map<String, BoardConfig> getActiveBoards() {
        return activeBoards.get();
    }

    public BoardConfig getBoard(String boardId) {
        return activeBoards.get().get(boardId);
    }

    public String getBoardIdForDisplay(UUID displayId) {
        return displayToBoardMap.get(displayId);
    }

    public DisplayPlane getDisplayPlane(UUID displayId) {
        return displayPlaneMap.get(displayId);
    }

    private final Map<String, String> activeSceneIds = new ConcurrentHashMap<>();
    private final Map<String, Map<UUID, String>> playerActiveScenes = new ConcurrentHashMap<>();
    private StorageManager storageManager;

    public void setStorageManager(StorageManager storageManager) {
        this.storageManager = storageManager;
    }

    public Collection<DisplayPlane> getAllDisplayPlanes() {
        return Collections.unmodifiableCollection(displayPlaneMap.values());
    }

    public BoardConfig.SceneDefinition getActiveScene(String boardId) {
        return getActiveScene(boardId, null);
    }

    public String getActiveSceneId(String boardId) {
        return getActiveSceneId(boardId, null);
    }

    public String getActiveSceneId(String boardId, UUID playerId) {
        BoardConfig board = getBoard(boardId);
        if (board == null || board.scenes() == null || board.scenes().isEmpty()) {
            return "default";
        }
        if (playerId != null) {
            Map<UUID, String> pMap = playerActiveScenes.get(boardId);
            if (pMap != null) {
                String pScene = pMap.get(playerId);
                if (pScene != null && board.scenes().containsKey(pScene)) {
                    return pScene;
                }
            }
        }
        String active = activeSceneIds.get(boardId);
        if (active != null && board.scenes().containsKey(active)) {
            return active;
        }
        if (board.scenes().containsKey("default")) {
            return "default";
        }
        return board.scenes().keySet().iterator().next();
    }

    public BoardConfig.SceneDefinition getActiveScene(String boardId, UUID playerId) {
        BoardConfig board = getBoard(boardId);
        if (board == null || board.scenes() == null || board.scenes().isEmpty()) {
            return null;
        }
        if (playerId != null) {
            Map<UUID, String> pMap = playerActiveScenes.get(boardId);
            if (pMap != null) {
                String pScene = pMap.get(playerId);
                if (pScene != null && board.scenes().containsKey(pScene)) {
                    return board.scenes().get(pScene);
                }
            }
        }
        String active = activeSceneIds.get(boardId);
        if (active != null && board.scenes().containsKey(active)) {
            return board.scenes().get(active);
        }
        BoardConfig.SceneDefinition def = board.scenes().get("default");
        if (def != null) {
            return def;
        }
        return board.scenes().values().iterator().next();
    }

    public boolean hasPerPlayerScenes(String boardId) {
        if (boardId == null)
            return false;
        Map<UUID, String> pMap = playerActiveScenes.get(boardId);
        return pMap != null && !pMap.isEmpty();
    }

    public boolean hasPerPlayerScene(String boardId, UUID playerId) {
        if (boardId == null || playerId == null)
            return false;
        Map<UUID, String> pMap = playerActiveScenes.get(boardId);
        return pMap != null && pMap.containsKey(playerId);
    }

    public boolean setActiveScene(String boardId, String sceneId) {
        return setActiveScene(boardId, null, sceneId);
    }

    public boolean setActiveScene(String boardId, UUID playerId, String sceneId) {
        BoardConfig board = getBoard(boardId);
        if (board != null && board.scenes() != null && board.scenes().containsKey(sceneId)) {
            DebugLogger.log("Scene", "Board '%s' active scene set to '%s' for %s",
                    boardId, sceneId, playerId != null ? playerId : "all");
            if (playerId != null) {
                playerActiveScenes.computeIfAbsent(boardId, k -> new ConcurrentHashMap<>()).put(playerId, sceneId);
                if (board.settings().persistent() && storageManager != null) {
                    storageManager.savePlayerSceneAsync(playerId, boardId, sceneId);
                }
                if (renderEngine != null) {
                    renderEngine.forceFullRedrawForPlayer(boardId, playerId);
                }
            } else {
                activeSceneIds.put(boardId, sceneId);
                if (renderEngine != null) {
                    renderEngine.forceFullRedraw(boardId);
                }
            }
            return true;
        }
        return false;
    }

    public void resetPlayerScene(String boardId, UUID playerId) {
        if (boardId == null || playerId == null)
            return;
        BoardConfig board = getBoard(boardId);
        if (board != null && board.settings().persistent() && !board.settings().resetOnDisappear()) {
            return;
        }
        Map<UUID, String> pMap = playerActiveScenes.get(boardId);
        if (pMap != null && pMap.remove(playerId) != null) {
            if (renderEngine != null) {
                renderEngine.forceFullRedrawForPlayer(boardId, playerId);
            }
        }
    }

    public void resetPlayerAllNonPersistentScenes(UUID playerId) {
        if (playerId == null)
            return;
        for (Map.Entry<String, BoardConfig> entry : getActiveBoards().entrySet()) {
            if (!entry.getValue().settings().persistent() || entry.getValue().settings().resetOnRejoin()) {
                Map<UUID, String> pMap = playerActiveScenes.get(entry.getKey());
                if (pMap != null && pMap.remove(playerId) != null) {
                    if (renderEngine != null) {
                        renderEngine.forceFullRedrawForPlayer(entry.getKey(), playerId);
                    }
                }
            }
        }
    }

    public void loadPlayerPersistentScenesAsync(UUID playerId) {
        if (playerId == null || storageManager == null) {
            return;
        }
        for (Map.Entry<String, BoardConfig> entry : getActiveBoards().entrySet()) {
            String boardId = entry.getKey();
            BoardConfig board = entry.getValue();
            if (board != null && board.settings().persistent() && !board.settings().resetOnRejoin()) {
                storageManager.getPlayerSceneAsync(playerId, boardId).thenAccept(optScene -> {
                    if (optScene.isPresent()) {
                        String sceneId = optScene.get();
                        if (board.scenes() != null && board.scenes().containsKey(sceneId)) {
                            playerActiveScenes.computeIfAbsent(boardId, k -> new ConcurrentHashMap<>()).put(playerId, sceneId);
                            if (renderEngine != null) {
                                renderEngine.forceFullRedrawForPlayer(boardId, playerId);
                            }
                        }
                    }
                });
            }
        }
    }

    public void resetAllNonPersistentScenes() {
        for (Map.Entry<String, BoardConfig> entry : getActiveBoards().entrySet()) {
            if (!entry.getValue().settings().persistent() || entry.getValue().settings().resetOnReload()) {
                playerActiveScenes.remove(entry.getKey());
                if (renderEngine != null) {
                    renderEngine.forceFullRedraw(entry.getKey());
                }
            }
        }
    }

    public void resetActiveScene(String boardId) {
        activeSceneIds.remove(boardId);
        playerActiveScenes.remove(boardId);
        if (renderEngine != null) {
            renderEngine.forceFullRedraw(boardId);
        }
    }

    public BoardConfig.SceneDefinition getDefaultScene(String boardId) {
        return getActiveScene(boardId, null);
    }
}