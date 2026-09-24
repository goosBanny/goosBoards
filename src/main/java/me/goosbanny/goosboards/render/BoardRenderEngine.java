package me.goosbanny.goosboards.render;

import me.goosbanny.goosboards.GoosBoards;
import me.goosbanny.goosboards.display.DistanceLodTracker;
import me.goosbanny.goosboards.protocol.packet.BatchedMapSender;
import me.goosbanny.goosboards.render.buffer.CanvasBuffer;
import me.goosbanny.goosboards.render.buffer.CanvasBufferImpl;
import me.goosbanny.goosboards.render.diff.TileDiffer;
import me.goosbanny.goosboards.render.palette.PaletteQuantizer;

import me.goosbanny.goosboards.display.PlayerSnapshot;
import me.goosbanny.goosboards.render.buffer.BoardMaskingUtil;
import me.goosbanny.goosboards.raycast.Vector3d;
import io.netty.channel.Channel;
import me.goosbanny.goosboards.core.scheduler.UniversalScheduler;
import me.goosbanny.goosboards.command.BoardSelectionManager;
import me.goosbanny.goosboards.integration.placeholder.PlaceholderService;
import me.goosbanny.goosboards.config.ConfigReloadManager;
import me.goosbanny.goosboards.core.logging.DebugLogger;
import me.goosbanny.goosboards.core.metrics.MetricsCollector;
import me.goosbanny.goosboards.protocol.VirtualEntityRig;
import me.goosbanny.goosboards.protocol.VirtualEntityTracker;
import me.goosbanny.goosboards.display.DisplayPlane;
import me.goosbanny.goosboards.raycast.DisplayRaycaster;
import me.goosbanny.goosboards.raycast.MutableRaycastHit;
import me.goosbanny.goosboards.display.ProximityTracker;
import me.goosbanny.goosboards.render.diff.TileDiffer.DirtyTile;
import me.goosbanny.goosboards.scene.BoardConfig;
import me.goosbanny.goosboards.scene.component.interactive.ButtonComponent;
import me.goosbanny.goosboards.scene.component.misc.DelayedTimerComponent;
import me.goosbanny.goosboards.scene.component.visual.GifComponent;
import me.goosbanny.goosboards.scene.component.visual.Head2DComponent;
import me.goosbanny.goosboards.scene.component.visual.ImageComponent;
import me.goosbanny.goosboards.scene.Rect;
import me.goosbanny.goosboards.scene.RenderContext;
import me.goosbanny.goosboards.scene.component.container.ScrollPaneComponent;
import me.goosbanny.goosboards.scene.component.UIComponent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import me.goosbanny.goosboards.interaction.InteractionRouter;
import me.goosbanny.goosboards.raycast.RaycastResult;
import me.goosbanny.goosboards.render.palette.ColorUtils;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.bukkit.util.Vector;

/**
 * Real-time render loop engine driving double-buffered canvas rendering,
 * distance LOD, planar back-face culling, Netty backpressure checks,
 * packet broadcasting, and performance metrics.
 */
public class BoardRenderEngine {

    private final ConfigReloadManager reloadManager;
    private final VirtualEntityTracker entityTracker;
    private final MetricsCollector metricsCollector;
    private final UniversalScheduler scheduler;
    private final MapIdAllocator mapIdAllocator;
    private final ReconciliationTask reconciliationTask;

    private final Map<UUID, DisplayRenderState> displayStates = new ConcurrentHashMap<>();
    private final Map<UUID, PlayerSnapshot> playerSnapshots = new ConcurrentHashMap<>();
    private final List<PlayerSnapshot> snapshotsScratch = new ArrayList<>(64);
    private final Set<UUID> respawnedPlayers = ConcurrentHashMap.newKeySet();
    private ScheduledExecutorService renderScheduler;
    private long tickCounter = 0;
    private volatile boolean running = false;
    private final AtomicBoolean isTicking = new AtomicBoolean(false);

    public BoardRenderEngine(
            ConfigReloadManager reloadManager,
            VirtualEntityTracker entityTracker,
            MetricsCollector metricsCollector,
            UniversalScheduler scheduler,
            MapIdAllocator mapIdAllocator) {
        this.reloadManager = reloadManager;
        this.entityTracker = entityTracker;
        this.metricsCollector = metricsCollector;
        this.scheduler = scheduler;
        this.mapIdAllocator = mapIdAllocator;
        this.reconciliationTask = new ReconciliationTask(entityTracker);
    }

    private ProximityTracker proximityTracker;
    private int proximityInterval = 2;
    private int renderTickInterval = 1;
    private int hoverRaycastIntervalTicks = 1;
    private BoardSelectionManager selectionManager;

    private static final ThreadLocal<MutableRaycastHit> RAYCAST_SCRATCH = ThreadLocal
            .withInitial(MutableRaycastHit::new);

    public void markRespawned(UUID playerId) {
        if (playerId != null) {
            respawnedPlayers.add(playerId);
        }
    }

    public PlayerSnapshot getPlayerSnapshot(UUID playerId) {
        return playerId != null ? playerSnapshots.get(playerId) : null;
    }

    public int getRenderTickInterval() {
        return renderTickInterval;
    }

    public void setRenderTickInterval(int renderTickInterval) {
        this.renderTickInterval = Math.max(1, renderTickInterval);
    }

    public int getHoverRaycastIntervalTicks() {
        return hoverRaycastIntervalTicks;
    }

    public void setHoverRaycastIntervalTicks(int hoverRaycastIntervalTicks) {
        this.hoverRaycastIntervalTicks = Math.max(1, hoverRaycastIntervalTicks);
    }

    public void setProximityTracker(ProximityTracker proximityTracker, int proximityInterval) {
        this.proximityTracker = proximityTracker;
        this.proximityInterval = Math.max(1, proximityInterval);
    }

    private InteractionRouter interactionRouter;

    public void setInteractionRouter(InteractionRouter interactionRouter) {
        this.interactionRouter = interactionRouter;
    }

    public void setSelectionManager(BoardSelectionManager selectionManager) {
        this.selectionManager = selectionManager;
    }

    public static void renderBoardOutline(CanvasBuffer canvas, String colorStr, int thickness) {
        BoardMaskingUtil.renderBoardOutline(canvas, colorStr, thickness);
    }

    public static void renderBoardOutline(CanvasBuffer canvas, String colorStr, int thickness, String shape,
            int cornerRadius) {
        BoardMaskingUtil.renderBoardOutline(canvas, colorStr, thickness, shape, cornerRadius);
    }

    public static void maskOutsideBoard(CanvasBuffer canvas, String shape, int cornerRadius) {
        BoardMaskingUtil.maskOutsideBoard(canvas, shape, cornerRadius);
    }

    public static void collectHitComponentIds(List<UIComponent> components, int px, int py, UUID viewerId,
            Set<String> out) {
        if (components == null || components.isEmpty() || out == null)
            return;
        for (UIComponent comp : components) {
            Rect bounds = comp.getBounds();
            if (comp instanceof ScrollPaneComponent sp) {
                int localX = px - bounds.x();
                int localY = py - bounds.y();
                if (sp.isInViewport(localX, localY)) {
                    out.add(sp.getId());
                    int contentX = localX + sp.getScrollOffsetX();
                    int contentY = localY + sp.getEffectiveScrollOffsetY(viewerId);
                    collectHitComponentIds(sp.getChildren(), contentX, contentY, viewerId, out);
                }
            } else if (bounds != null && bounds.contains(px, py)
                    && comp.containsPixel(px - bounds.x(), py - bounds.y())) {
                out.add(comp.getId());
                collectHitComponentIds(comp.getChildren(), px, py, viewerId, out);
            }
        }
    }

    public synchronized void start() {
        if (running)
            return;
        running = true;
        renderScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "GoosBoards-Render-Worker");
            t.setDaemon(true);
            t.setPriority(Thread.NORM_PRIORITY + 1);
            return t;
        });
        long periodMs = 50L * Math.max(1, renderTickInterval);
        renderScheduler.scheduleAtFixedRate(this::tick, 500L, periodMs, TimeUnit.MILLISECONDS);
    }

    public synchronized void stop() {
        running = false;
        if (renderScheduler != null && !renderScheduler.isShutdown()) {
            renderScheduler.shutdown();
            try {
                if (!renderScheduler.awaitTermination(2, TimeUnit.SECONDS)) {
                    renderScheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                renderScheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        for (DisplayRenderState state : displayStates.values()) {
            for (int mid : state.mapIds) {
                mapIdAllocator.free(mid);
            }
            state.clearViewers();
        }
        displayStates.clear();
    }

    /**
     * Synchronizes display render states with the latest active boards from
     * ConfigReloadManager.
     */
    public void syncDisplays() {
        Set<UUID> currentDisplayIds = ConcurrentHashMap.newKeySet();
        Collection<DisplayPlane> planes = reloadManager.getAllDisplayPlanes();

        for (DisplayPlane plane : planes) {
            UUID displayId = plane.id();
            currentDisplayIds.add(displayId);

            String boardId = reloadManager.getBoardIdForDisplay(displayId);
            if (boardId == null)
                continue;

            DisplayRenderState state = displayStates.computeIfAbsent(displayId, id -> {
                int tileCount = plane.getWidthTiles() * plane.getHeightTiles();
                int[] mapIds = new int[tileCount];
                for (int i = 0; i < tileCount; i++) {
                    mapIds[i] = mapIdAllocator.allocate();
                }
                return new DisplayRenderState(boardId, plane, mapIds);
            });

            // Update plane reference in case settings (geometry, glow, activationRadius,
            // etc.) changed!
            boolean dimensionsChanged = (state.plane.getWidthTiles() != plane.getWidthTiles())
                    || (state.plane.getHeightTiles() != plane.getHeightTiles());
            state.setPlane(plane);
            if (dimensionsChanged) {
                for (int mid : state.mapIds) {
                    mapIdAllocator.free(mid);
                }
                int tileCount = plane.getWidthTiles() * plane.getHeightTiles();
                int[] newMapIds = new int[tileCount];
                for (int i = 0; i < tileCount; i++) {
                    newMapIds[i] = mapIdAllocator.allocate();
                }
                state.updateMapIdsAndBuffer(newMapIds,
                        new CanvasBufferImpl(plane.getWidthTiles(), plane.getHeightTiles()));
            }

            // On syncDisplays (e.g. reload or board create): invalidate canvas buffer
            // and force full flush to all active viewers so they receive the freshly
            // rendered scene
            state.canvasBuffer.clear();
            state.canvasBuffer.markAllTilesDirty();
            state.clearPerViewerCanvases();
            state.initialRenderDone = false;
            state.clearViewers();
            state.pendingFullFlushUuids.addAll(state.activeViewerUuids);
        }

        // Clean up removed displays and free map IDs
        displayStates.entrySet().removeIf(entry -> {
            if (!currentDisplayIds.contains(entry.getKey())) {
                if (entityTracker != null) {
                    entityTracker.despawnAllForDisplay(entry.getKey());
                }
                for (int mid : entry.getValue().mapIds) {
                    mapIdAllocator.free(mid);
                }
                return true;
            }
            return false;
        });
    }

    public void onPlayerQuit(UUID playerId) {
        playerSnapshots.remove(playerId);
        respawnedPlayers.remove(playerId);
        for (DisplayRenderState state : displayStates.values()) {
            state.removeViewer(playerId);
        }
    }

    /**
     * Registers a player as an active viewer for a display so that the render
     * engine
     * does not re-spawn their rig on the next tick. Called by ConfigReloadManager
     * after hot-rehydrating viewers during reload, ensuring the engine's viewer
     * tracking stays consistent with the entity tracker's state and queues them for
     * full flush.
     */
    public void registerViewer(UUID displayId, UUID playerId) {
        DisplayRenderState state = displayStates.get(displayId);
        if (state != null && playerId != null) {
            state.activeViewerUuids.add(playerId);
            state.pendingFullFlushUuids.add(playerId);
            DebugLogger.log("Engine", "Pre-registered viewer %s for display %s (rehydration, queued for full flush)",
                    playerId, displayId);
        }
    }

    public void tick() {
        if (!running)
            return;
        if (!isTicking.compareAndSet(false, true)) {
            // Previous render tick is still in progress; skip this cycle to prevent thread
            // overlap
            return;
        }
        try {
            tickCounter++;

            // Sweep expired interactive board selection sessions when active (every 100
            // ticks = 5s)
            if (selectionManager != null && selectionManager.hasActiveSessions() && (tickCounter % 100 == 0)) {
                selectionManager.sweepExpiredSessions();
            }

            if (Bukkit.getServer() == null)
                return;
            if (displayStates.isEmpty())
                return;
            if (renderTickInterval > 1 && (tickCounter % renderTickInterval != 0))
                return;

            // Non-blocking decoupled spatial snapshot ingestion (eliminates thread parking
            // & CompletableFuture.get)
            Collection<? extends Player> onlinePlayers = Bukkit.getOnlinePlayers();
            for (Player p : onlinePlayers) {
                if (p == null || !p.isOnline())
                    continue;
                UUID pId = p.getUniqueId();
                // If player is confirmed nowhere near any board and already has a snapshot,
                // throttle polling
                if (proximityTracker != null && !proximityTracker.isNearAnyBoard(pId)
                        && playerSnapshots.containsKey(pId)) {
                    if (tickCounter % 20 != 0)
                        continue;
                }
                scheduler.runOnEntity(p, player -> {
                    if (!player.isOnline())
                        return;
                    try {
                        Location loc = player.getLocation();
                        if (loc.getWorld() != null) {
                            Vector dir = loc.getDirection();
                            playerSnapshots.put(player.getUniqueId(), new PlayerSnapshot(
                                    player.getUniqueId(),
                                    player.getName(),
                                    loc.getWorld().getName(),
                                    loc.getX(),
                                    loc.getY(),
                                    loc.getZ(),
                                    player.getEyeHeight(),
                                    dir.getX(),
                                    dir.getY(),
                                    dir.getZ(),
                                    player));
                        }
                    } catch (Throwable ignored) {
                    }
                });
            }

            // Reuse scratch list to eliminate per-tick allocation
            snapshotsScratch.clear();
            playerSnapshots.entrySet().removeIf(entry -> {
                PlayerSnapshot snap = entry.getValue();
                if (snap.player() == null || !snap.player().isOnline()) {
                    return true;
                }
                snapshotsScratch.add(snap);
                return false;
            });

            if (snapshotsScratch.isEmpty() || displayStates.isEmpty()) {
                return;
            }

            // Coalesced broad-phase proximity update reusing existing player snapshots
            if (proximityTracker != null && (tickCounter % proximityInterval == 0)) {
                proximityTracker.updateWithSnapshots(snapshotsScratch);
            }

            // Autonomous 3-second reconciliation loop
            if (tickCounter % 60 == 0) {
                try {
                    reconciliationTask.reconcile(displayStates, snapshotsScratch);
                } catch (Throwable t) {
                    DebugLogger.log("Engine", "Error during reconciliation: %s", t.getMessage());
                }
            }

            for (DisplayRenderState state : displayStates.values()) {
                try {
                    tickDisplay(state, snapshotsScratch);
                } catch (Throwable t) {
                    DebugLogger.log("Engine", "Unhandled exception during render tick of board '%s': %s", state.boardId,
                            t.getMessage());
                    t.printStackTrace();
                }
            }
        } finally {
            isTicking.set(false);
        }
    }

    private void tickDisplay(DisplayRenderState state, List<PlayerSnapshot> online) {
        DisplayPlane plane = state.plane;
        String boardId = state.boardId;
        double maxDist = plane.activationRadius() > 0 ? plane.activationRadius() : 32.0;

        BoardConfig boardCfg = reloadManager != null ? reloadManager.getBoard(boardId) : null;
        int placeholderRefreshTicks = (boardCfg != null && boardCfg.settings() != null
                && boardCfg.settings().placeholderRefreshTicks() > 0)
                        ? boardCfg.settings().placeholderRefreshTicks()
                        : -1;
        int effectiveRefreshTicks = placeholderRefreshTicks > 0
                ? placeholderRefreshTicks
                : PlaceholderService.getDefaultRefreshTicks();

        BoardConfig.SceneDefinition baseScene = reloadManager != null ? reloadManager.getActiveScene(boardId) : null;
        boolean hasDynamicComponent = baseScene != null && baseScene.components() != null
                && hasDynamicComponent(baseScene.components());

        state.viewersScratch.clear();
        state.newViewersScratch.clear();
        List<Player> viewersToRender = state.viewersScratch;
        List<Player> newViewers = state.newViewersScratch;

        for (PlayerSnapshot snap : online) {
            if (!snap.worldName().equals(plane.worldName())) {
                if (state.activeViewerUuids.contains(snap.id())) {
                    entityTracker.despawnRig(snap.player(), plane.id());
                    state.removeViewer(snap.id());
                    PlaceholderService.clearViewer(snap.id());
                    if (reloadManager != null) {
                        reloadManager.resetPlayerScene(boardId, snap.id());
                    }
                }
                continue;
            }

            double px = snap.x();
            double py = snap.y();
            double pz = snap.z();

            Vector3d center = plane.center();
            double dx = px - center.x();
            double dy = py - center.y();
            double dz = pz - center.z();
            double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);

            boolean hadRig = state.activeViewerUuids.contains(snap.id());
            double effectiveMaxDist = hadRig ? (maxDist + Math.min(2.0, maxDist * 0.1)) : maxDist;

            if (dist <= effectiveMaxDist) {
                boolean isRespawned = respawnedPlayers.remove(snap.id());
                // If player just joined and client terrain is still loading (< 10 ticks lived),
                // wait before spawning rig,
                // but bypass delay if player was already online and respawned
                if (!isRespawned && snap.player().getTicksLived() < 10) {
                    continue;
                }

                if (!hadRig) {
                    DebugLogger.log("Engine",
                            "Player %s entered radius of board '%s' (dist=%.1fm <= max=%.1fm) -> spawning rig",
                            snap.name(), boardId, dist, maxDist);
                    entityTracker.spawnRig(snap.player(), plane, state.mapIds);
                    state.activeViewerUuids.add(snap.id());
                    newViewers.add(snap.player());
                } else if (state.pendingFullFlushUuids.contains(snap.id())) {
                    int drops = state.getDrops(snap.id());
                    int retryInterval = (drops >= 10) ? 40 : ((drops >= 3) ? 10 : (drops > 0 ? 2 : 1));
                    if (drops >= 10 && tickCounter % 40 == 0) {
                        DebugLogger.log("Engine",
                                "Backpressure warning: %s has %d consecutive dropped flushes on board '%s'; backing off to every %d ticks",
                                snap.name(), drops, boardId, retryInterval);
                    }
                    if ((tickCounter % retryInterval) == 0) {
                        state.pendingFullFlushUuids.remove(snap.id());
                        newViewers.add(snap.player());
                    }
                }

                // Check planar back-face culling
                double eyeY = py + snap.eyeHeight();
                boolean occluded = DistanceLodTracker.isViewingBackFace(px, eyeY, pz, plane);

                // Raycast for hover & aiming
                boolean doHoverRaycast = (hoverRaycastIntervalTicks <= 1)
                        || (tickCounter % hoverRaycastIntervalTicks == 0);
                boolean isAiming = false;
                state.hitSetScratch.clear();
                if (!occluded) {
                    if (doHoverRaycast) {
                        MutableRaycastHit hit = RAYCAST_SCRATCH.get();
                        double maxRayDist = Math.max(plane.interactionRadius(), plane.activationRadius());
                        DisplayRaycaster.intersect(
                                plane,
                                px, eyeY, pz,
                                snap.dirX(), snap.dirY(), snap.dirZ(),
                                maxRayDist,
                                hit);
                        isAiming = hit.hit;
                        if (isAiming && hit.distance <= plane.interactionRadius()) {
                            BoardConfig.SceneDefinition activeScene = reloadManager.getActiveScene(boardId, snap.id());
                            if (activeScene == null)
                                activeScene = reloadManager.getDefaultScene(boardId);
                            if (activeScene != null && activeScene.components() != null && interactionRouter != null) {
                                collectHitComponentIds(activeScene.components(), hit.getIntPixelX(), hit.getIntPixelY(),
                                        snap.id(), state.hitSetScratch);
                                RaycastResult.Hit rayHit = new RaycastResult.Hit(plane.id(), hit.getIntPixelX(),
                                        hit.getIntPixelY(), hit.distance);
                                interactionRouter.handleHover(snap.player(), rayHit);
                            }
                        }
                    } else {
                        isAiming = state.aimingViewers.contains(snap.id());
                        Set<String> prev = state.viewerHoveredComponents.get(snap.id());
                        if (prev != null) {
                            state.hitSetScratch.addAll(prev);
                        }
                    }
                }

                if (!isAiming && state.aimingViewers.contains(snap.id())) {
                    if (interactionRouter != null) {
                        UUID planeId = plane.id();
                        interactionRouter.handleHoverExit(snap.player(), planeId);
                    }
                }

                boolean wasAiming = state.aimingViewers.contains(snap.id());
                Set<String> prevHovered = state.viewerHoveredComponents.get(snap.id());

                if (isAiming) {
                    state.aimingViewers.add(snap.id());
                } else {
                    state.aimingViewers.remove(snap.id());
                }

                boolean hoverCompChanged;
                if (isAiming && !state.hitSetScratch.isEmpty()) {
                    if (prevHovered == null) {
                        Set<String> set = new HashSet<>(state.hitSetScratch);
                        state.viewerHoveredComponents.put(snap.id(), set);
                        hoverCompChanged = true;
                    } else if (!prevHovered.equals(state.hitSetScratch)) {
                        prevHovered.clear();
                        prevHovered.addAll(state.hitSetScratch);
                        hoverCompChanged = true;
                    } else {
                        hoverCompChanged = false;
                    }
                } else {
                    hoverCompChanged = (prevHovered != null && !prevHovered.isEmpty());
                    state.viewerHoveredComponents.remove(snap.id());
                }

                boolean hoverChanged = (wasAiming != isAiming) || hoverCompChanged;

                // Check distance LOD, hover change, dynamic animation (GIFs), or placeholder
                // refresh ticks
                BoardConfig.SceneDefinition viewerScene = reloadManager != null ? reloadManager.getActiveScene(boardId, snap.id()) : baseScene;
                boolean isViewerSceneDynamic = (viewerScene != null && viewerScene.components() != null && hasDynamicComponent(viewerScene.components()))
                        || hasDynamicComponent;

                boolean isPlaceholderRefreshTick = (tickCounter % effectiveRefreshTicks == 0);
                boolean shouldRenderLOD = DistanceLodTracker.shouldRender(dist, occluded, tickCounter);
                boolean needsViewerRender = hoverChanged
                        || !hadRig
                        || (shouldRenderLOD && (isViewerSceneDynamic || isPlaceholderRefreshTick));

                if (needsViewerRender) {
                    viewersToRender.add(snap.player());
                } else if (occluded && tickCounter % 40 == 0) {
                    DebugLogger.log("Engine", "Player %s is behind board '%s' (back-face occluded)", snap.name(),
                            boardId);
                }

                // Maintain Minecraft entity glow packet if configured
                VirtualEntityRig rig = entityTracker.getRig(snap.id(), plane.id());
                if (rig != null) {
                    boolean shouldGlow = plane.glow() || (plane.hoverGlow() && isAiming);
                    rig.setGlowing(shouldGlow, plane.glowColor());
                }
            } else {
                if (hadRig) {
                    DebugLogger.log("Engine",
                            "Player %s exited radius of board '%s' (dist=%.1fm > max=%.1fm) -> despawning rig",
                            snap.name(), boardId, dist, effectiveMaxDist);
                    entityTracker.despawnRig(snap.id(), plane.id());
                    state.removeViewer(snap.id());
                    PlaceholderService.clearViewer(snap.id());
                    if (reloadManager != null) {
                        reloadManager.resetPlayerScene(boardId, snap.id());
                    }
                }
            }
        }

        // Clean up any stale active viewers who walked out of range, disconnected, or
        // switched worlds
        double maxDistSq = (maxDist + Math.min(2.0, maxDist * 0.1)) * (maxDist + Math.min(2.0, maxDist * 0.1));
        Vector3d planeCenter = plane.center();
        state.activeViewersScratch.clear();
        state.activeViewersScratch.addAll(state.activeViewerUuids);
        int activeCount = state.activeViewersScratch.size();
        for (int i = 0; i < activeCount; i++) {
            UUID activeId = state.activeViewersScratch.get(i);
            PlayerSnapshot pSnap = playerSnapshots.get(activeId);
            if (pSnap == null || pSnap.player() == null || !pSnap.player().isOnline()
                    || !pSnap.worldName().equals(plane.worldName())) {
                entityTracker.despawnRig(activeId, plane.id());
                state.removeViewer(activeId);
                PlaceholderService.clearViewer(activeId);
                if (reloadManager != null) {
                    reloadManager.resetPlayerScene(boardId, activeId);
                }
            } else {
                double dx = pSnap.x() - planeCenter.x();
                double dy = pSnap.y() - planeCenter.y();
                double dz = pSnap.z() - planeCenter.z();
                if ((dx * dx + dy * dy + dz * dz) > maxDistSq) {
                    entityTracker.despawnRig(activeId, plane.id());
                    state.removeViewer(activeId);
                    PlaceholderService.clearViewer(activeId);
                    if (reloadManager != null) {
                        reloadManager.resetPlayerScene(boardId, activeId);
                    }
                }
            }
        }

        // Update active viewer count in metrics
        metricsCollector.setActiveViewers(boardId, state.activeViewerUuids.size());

        boolean forceRender = state.needsRenderPass;
        state.needsRenderPass = false;

        if (viewersToRender.isEmpty() && newViewers.isEmpty() && state.initialRenderDone && !forceRender) {
            return;
        }

        if (forceRender) {
            for (PlayerSnapshot snap : online) {
                if (state.activeViewerUuids.contains(snap.id()) && !viewersToRender.contains(snap.player())) {
                    viewersToRender.add(snap.player());
                }
            }
        }

        // Render scene components onto the canvas
        BoardConfig.SceneDefinition scene = reloadManager.getActiveScene(boardId);
        if (scene == null || scene.components() == null) {
            return;
        }

        boolean hasPerPlayerScenes = reloadManager != null && reloadManager.hasPerPlayerScenes(boardId);

        if ((!state.initialRenderDone || forceRender) && !state.activeViewerUuids.isEmpty()) {
            if (hasPerPlayerScenes) {
                DebugLogger.log("Engine", "Board '%s': rendering per-player scenes for %d active viewer(s)",
                        boardId, state.activeViewerUuids.size());
            } else {
                DebugLogger.log("Engine", "Board '%s': rendering scene '%s' (%d components) for %d active viewer(s)",
                        boardId, scene.id(), scene.components().size(), state.activeViewerUuids.size());
            }
        }

        long startNanos = System.nanoTime();
        boolean sceneIsContextDependent = hasPerPlayerScenes || plane.hoverGlow() || !state.aimingViewers.isEmpty()
                || !state.viewerHoveredComponents.isEmpty()
                || hasContextDependentComponent(scene.components());

        if (sceneIsContextDependent) {
            // Per-viewer isolated rendering and dirty diff
            int totalPacketsSent = 0;
            Set<UUID> flushedViewers = new HashSet<>();

            // 1. Process new viewers: full flush per-viewer canvas
            if (!newViewers.isEmpty()) {
                for (Player nv : newViewers) {
                    CanvasBufferImpl perViewerCanvas = state.getOrCreateViewerCanvas(nv.getUniqueId());
                    perViewerCanvas.clear();
                    boolean isAiming = state.aimingViewers.contains(nv.getUniqueId());
                    Set<String> hoveredComps = state.viewerHoveredComponents.getOrDefault(nv.getUniqueId(),
                            Collections.emptySet());
                    RenderContext ctx = RenderContext.of(nv, hoveredComps, isAiming, placeholderRefreshTicks,
                            tickCounter);
                    BoardConfig.SceneDefinition viewerScene = reloadManager.getActiveScene(boardId, nv.getUniqueId());
                    if (viewerScene == null)
                        viewerScene = scene;
                    for (UIComponent comp : viewerScene.components()) {
                        comp.render(perViewerCanvas, ctx);
                    }
                    if (plane.isCircular() || plane.isRounded()) {
                        maskOutsideBoard(perViewerCanvas, plane.shape(), plane.cornerRadius());
                    }
                    if ((isAiming && plane.hoverGlow()) || plane.glow()) {
                        String outlineCol = (isAiming && plane.hoverGlow()) ? plane.hoverGlowColor()
                                : plane.glowColor();
                        renderBoardOutline(perViewerCanvas, outlineCol, 2, plane.shape(), plane.cornerRadius());
                    }
                    state.fullFlushScratch.clear();
                    for (int ty = 0; ty < plane.getHeightTiles(); ty++) {
                        for (int tx = 0; tx < plane.getWidthTiles(); tx++) {
                            int tIdx = ty * plane.getWidthTiles() + tx;
                            byte[] tileData = perViewerCanvas.getSubTile(tx, ty);
                            long hash = TileDiffer.computeHash(tileData);
                            state.fullFlushScratch.add(new DirtyTile(tx, ty, tIdx, hash, tileData));
                        }
                    }
                    boolean sent = BatchedMapSender.sendDirtyTiles(nv, state.mapIds, state.fullFlushScratch, true);
                    if (!sent) {
                        state.pendingFullFlushUuids.add(nv.getUniqueId());
                        state.recordDrop(nv.getUniqueId());
                    } else {
                        state.resetDrop(nv.getUniqueId());
                        for (int i = 0; i < state.fullFlushScratch.size(); i++) {
                            DirtyTile dt = state.fullFlushScratch.get(i);
                            perViewerCanvas.markClean(dt.tileX(), dt.tileY(), dt.newHash());
                        }
                        totalPacketsSent += state.fullFlushScratch.size();
                    }
                    flushedViewers.add(nv.getUniqueId());
                }
            }

            // 2. Process regular active viewers: render and send dirty tiles
            for (Player viewer : viewersToRender) {
                if (flushedViewers.contains(viewer.getUniqueId())) {
                    continue;
                }
                CanvasBufferImpl perViewerCanvas = state.getOrCreateViewerCanvas(viewer.getUniqueId());
                boolean isAiming = state.aimingViewers.contains(viewer.getUniqueId());
                Set<String> hoveredComps = state.viewerHoveredComponents.getOrDefault(viewer.getUniqueId(),
                        Collections.emptySet());
                RenderContext ctx = RenderContext.of(viewer, hoveredComps, isAiming, placeholderRefreshTicks,
                        tickCounter);
                BoardConfig.SceneDefinition viewerScene = reloadManager.getActiveScene(boardId, viewer.getUniqueId());
                if (viewerScene == null)
                    viewerScene = scene;
                for (UIComponent comp : viewerScene.components()) {
                    comp.render(perViewerCanvas, ctx);
                }
                if (plane.isCircular() || plane.isRounded()) {
                    maskOutsideBoard(perViewerCanvas, plane.shape(), plane.cornerRadius());
                }
                if ((isAiming && plane.hoverGlow()) || plane.glow()) {
                    String outlineCol = (isAiming && plane.hoverGlow()) ? plane.hoverGlowColor() : plane.glowColor();
                    renderBoardOutline(perViewerCanvas, outlineCol, 2, plane.shape(), plane.cornerRadius());
                }
                state.dirtyScratch.clear();
                TileDiffer.findDirtyTiles(perViewerCanvas, plane.getWidthTiles(), plane.getHeightTiles(),
                        state.dirtyScratch);
                if (!state.dirtyScratch.isEmpty()) {
                    boolean ok = BatchedMapSender.sendDirtyTiles(viewer, state.mapIds, state.dirtyScratch);
                    if (!ok) {
                        state.recordDrop(viewer.getUniqueId());
                    } else {
                        state.resetDrop(viewer.getUniqueId());
                        int sentCount = Math.min(state.dirtyScratch.size(),
                                BatchedMapSender.getMaxTilesPerPlayerPerTick());
                        totalPacketsSent += sentCount;
                        for (int i = 0; i < sentCount; i++) {
                            DirtyTile dt = state.dirtyScratch.get(i);
                            perViewerCanvas.markClean(dt.tileX(), dt.tileY(), dt.newHash());
                        }
                    }
                }
            }

            long elapsedNanos = System.nanoTime() - startNanos;
            metricsCollector.recordRenderTime(boardId, elapsedNanos);
            metricsCollector.recordPacketsSent(boardId, totalPacketsSent);
        } else {
            // Clean up per-viewer canvases if switching to static scene
            state.clearPerViewerCanvases();

            Player primaryViewer = !viewersToRender.isEmpty() ? viewersToRender.get(0)
                    : (!newViewers.isEmpty() ? newViewers.get(0) : null);
            RenderContext renderCtx = primaryViewer != null
                    ? RenderContext.of(primaryViewer, Collections.emptySet(), false, placeholderRefreshTicks,
                            tickCounter)
                    : RenderContext.empty();

            boolean needsFullPass = !state.initialRenderDone || forceRender || hasDynamicComponent;

            CanvasBufferImpl canvas = state.canvasBuffer;
            state.dirtyScratch.clear();
            if (needsFullPass) {
                for (UIComponent comp : scene.components()) {
                    comp.render(canvas, renderCtx);
                }
                if (plane.isCircular() || plane.isRounded()) {
                    maskOutsideBoard(canvas, plane.shape(), plane.cornerRadius());
                }
                if (plane.glow()) {
                    renderBoardOutline(canvas, plane.glowColor(), 2, plane.shape(), plane.cornerRadius());
                }
                TileDiffer.findDirtyTiles(canvas, plane.getWidthTiles(), plane.getHeightTiles(), state.dirtyScratch);
            }

            // If new viewers joined, force send all tiles to them so board appears
            // instantly.
            // Snapshots the back buffer so sent data is decoupled from future canvas
            // writes.
            Set<UUID> flushedViewers = new HashSet<>();
            if (!newViewers.isEmpty()) {
                state.fullFlushScratch.clear();
                for (int ty = 0; ty < plane.getHeightTiles(); ty++) {
                    for (int tx = 0; tx < plane.getWidthTiles(); tx++) {
                        int tIdx = ty * plane.getWidthTiles() + tx;
                        byte[] tileData = canvas.getSubTile(tx, ty); // back buffer snapshotted
                        long hash = TileDiffer.computeHash(tileData);
                        state.fullFlushScratch.add(new DirtyTile(tx, ty, tIdx, hash, tileData));
                    }
                }
                DebugLogger.log("Engine", "Board '%s': flushing FULL canvas (%d tiles) to %d new viewer(s)", boardId,
                        state.fullFlushScratch.size(), newViewers.size());
                for (Player nv : newViewers) {
                    boolean sent = BatchedMapSender.sendDirtyTiles(nv, state.mapIds, state.fullFlushScratch, true);
                    if (!sent) {
                        // Netty channel not writable or saturated! Retain in pendingFullFlushUuids for
                        // next tick with backoff
                        state.pendingFullFlushUuids.add(nv.getUniqueId());
                        state.recordDrop(nv.getUniqueId());
                        DebugLogger.log("Engine",
                                "Board '%s': full flush to %s dropped (backpressure, drop #%d) -> re-queued",
                                boardId, nv.getName(), state.getDrops(nv.getUniqueId()));
                    } else {
                        state.resetDrop(nv.getUniqueId());
                    }
                    flushedViewers.add(nv.getUniqueId());
                }
                metricsCollector.recordPacketsSent(boardId, newViewers.size() * state.fullFlushScratch.size());
            }

            if (!state.dirtyScratch.isEmpty()) {
                state.channelsScratch.clear();
                state.targetViewersScratch.clear();
                List<Player> targetViewers = state.targetViewersScratch;
                for (Player viewer : viewersToRender) {
                    if (flushedViewers.contains(viewer.getUniqueId())) {
                        continue;
                    }
                    targetViewers.add(viewer);
                    Channel ch = BatchedMapSender.getChannel(viewer);
                    if (ch != null && ch.isActive() && ch.isWritable()) {
                        state.channelsScratch.add(ch);
                    } else {
                        state.recordDrop(viewer.getUniqueId());
                        state.pendingFullFlushUuids.add(viewer.getUniqueId());
                    }
                }

                int sentCount = Math.min(state.dirtyScratch.size(), BatchedMapSender.getMaxTilesPerPlayerPerTick());
                int packetCount = 0;
                if (!state.channelsScratch.isEmpty()) {
                    BatchedMapSender.broadcastDirtyPackets(state.channelsScratch, state.mapIds, state.dirtyScratch);
                    packetCount = state.channelsScratch.size() * sentCount;
                } else if (!targetViewers.isEmpty()) {
                    // Guaranteed fallback: send via PlayerManager when Netty channel handle is
                    // non-direct
                    for (Player viewer : targetViewers) {
                        boolean ok = BatchedMapSender.sendDirtyTiles(viewer, state.mapIds, state.dirtyScratch);
                        if (!ok) {
                            state.recordDrop(viewer.getUniqueId());
                            state.pendingFullFlushUuids.add(viewer.getUniqueId());
                        } else {
                            state.resetDrop(viewer.getUniqueId());
                        }
                    }
                    packetCount = targetViewers.size() * sentCount;
                }

                if (!flushedViewers.isEmpty() && targetViewers.isEmpty()) {
                    DebugLogger.log("Engine",
                            "Board '%s': found %d dirty tile(s) -> skipped (covered by full flush to %d viewer(s))",
                            boardId, state.dirtyScratch.size(), flushedViewers.size());
                } else {
                    DebugLogger.log("Engine",
                            "Board '%s': found %d dirty tile(s) -> sent %d packets (%d channels, %d viewers)",
                            boardId, state.dirtyScratch.size(), packetCount, state.channelsScratch.size(),
                            targetViewers.size());
                }

                for (int i = 0; i < sentCount; i++) {
                    DirtyTile dt = state.dirtyScratch.get(i);
                    canvas.markClean(dt.tileX(), dt.tileY(), dt.newHash());
                }

                long elapsedNanos = System.nanoTime() - startNanos;
                metricsCollector.recordRenderTime(boardId, elapsedNanos);
                metricsCollector.recordPacketsSent(boardId, packetCount);
            }
        }

        state.initialRenderDone = true;
    }

    public DisplayRenderState getDisplayState(UUID displayId) {
        return displayStates.get(displayId);
    }

    public int[] getMapIds(UUID displayId) {
        DisplayRenderState state = displayStates.get(displayId);
        return state != null ? state.mapIds : null;
    }

    public void removeDisplay(UUID displayId) {
        if (entityTracker != null) {
            entityTracker.despawnAllForDisplay(displayId);
        }
        DisplayRenderState removed = displayStates.remove(displayId);
        if (removed != null) {
            for (int mid : removed.mapIds) {
                mapIdAllocator.free(mid);
            }
            removed.activeViewerUuids.clear();
        }
    }

    /**
     * Flags displays belonging to the board for a dirty render pass on the next
     * tick
     * without wiping the canvas buffer or sending a full 64-tile burst.
     */
    public void requestDirtyPass(String boardId) {
        if (boardId == null) {
            requestDirtyPassAll();
            return;
        }
        for (DisplayRenderState state : displayStates.values()) {
            if (state.boardId.equalsIgnoreCase(boardId)) {
                state.markNeedsRenderPass();
            }
        }
    }

    /**
     * Flags all active displays for a dirty render pass on the next tick.
     */
    public void requestDirtyPassAll() {
        for (DisplayRenderState state : displayStates.values()) {
            state.markNeedsRenderPass();
        }
    }

    /**
     * Forces an immediate full canvas invalidation and queues all active viewers
     * for a full canvas flush on the next tick for all displays belonging to the
     * given board.
     */
    public void forceFullRedraw(String boardId) {
        if (boardId == null)
            return;
        for (DisplayRenderState state : displayStates.values()) {
            if (state.boardId.equalsIgnoreCase(boardId)) {
                state.canvasBuffer.clear();
                state.canvasBuffer.markAllTilesDirty();
                state.clearPerViewerCanvases();
                state.initialRenderDone = false;
                state.pendingFullFlushUuids.addAll(state.activeViewerUuids);
            }
        }
    }

    /**
     * Forces an immediate canvas invalidation for a specific player on the given
     * board.
     */
    public void forceFullRedrawForPlayer(String boardId, UUID playerId) {
        if (boardId == null || playerId == null)
            return;
        DebugLogger.log("Engine", "Board '%s': queued immediate full redraw for player %s", boardId, playerId);
        for (DisplayRenderState state : displayStates.values()) {
            if (state.boardId.equalsIgnoreCase(boardId)) {
                CanvasBufferImpl perViewer = state.perViewerCanvases.get(playerId);
                if (perViewer != null) {
                    perViewer.clear();
                    perViewer.markAllTilesDirty();
                }
                state.pendingFullFlushUuids.add(playerId);
                state.markNeedsRenderPass();
            }
        }
    }

    /**
     * Invalidate only the sub-tiles overlapping the specified component bounding
     * box on displays
     * belonging to the board, triggering a localized dirty render pass without
     * wiping or re-sending
     * unaffected components.
     */
    public void invalidateComponentRegion(String boardId, Rect bounds) {
        if (boardId == null)
            return;
        boolean found = false;
        for (DisplayRenderState state : displayStates.values()) {
            if (state.boardId.equalsIgnoreCase(boardId)) {
                found = true;
                if (bounds != null && bounds.width() > 0 && bounds.height() > 0) {
                    state.canvasBuffer.markRegionDirty(bounds.x(), bounds.y(), bounds.width(), bounds.height());
                    for (CanvasBufferImpl perViewer : state.perViewerCanvases.values()) {
                        perViewer.markRegionDirty(bounds.x(), bounds.y(), bounds.width(), bounds.height());
                    }
                } else {
                    state.canvasBuffer.markAllTilesDirty();
                    for (CanvasBufferImpl perViewer : state.perViewerCanvases.values()) {
                        perViewer.markAllTilesDirty();
                    }
                }
                state.markNeedsRenderPass();
            }
        }
        if (!found) {
            requestDirtyPass(boardId);
        }
    }

    /**
     * Requests localized redraw for components matching the given asset key (image
     * URL, file name, or skin name).
     * Finds the component in active board scenes, invalidates ONLY that component's
     * bounding box and intersecting tiles,
     * retaining all other rendered components (player balances, stats, static text)
     * intact.
     */
    public void requestComponentRedraw(String assetKey) {
        if (assetKey == null || assetKey.isBlank()) {
            requestDirtyPassAll();
            return;
        }
        boolean matchedAny = false;
        if (reloadManager != null) {
            Map<String, BoardConfig> boards = reloadManager.getActiveBoards();
            if (boards != null) {
                for (Map.Entry<String, BoardConfig> entry : boards.entrySet()) {
                    String boardId = entry.getKey();
                    BoardConfig config = entry.getValue();
                    if (config == null || config.scenes() == null)
                        continue;

                    BoardConfig.SceneDefinition activeScene = reloadManager.getActiveScene(boardId);
                    if (activeScene == null)
                        activeScene = reloadManager.getDefaultScene(boardId);
                    if (activeScene == null || activeScene.components() == null)
                        continue;

                    for (UIComponent comp : activeScene.components()) {
                        if (matchesAsset(comp, assetKey)) {
                            invalidateComponentRegion(boardId, comp.getBounds());
                            matchedAny = true;
                        }
                    }
                }
            }
        }
        if (!matchedAny) {
            requestDirtyPassAll();
        }
    }

    private boolean matchesAsset(UIComponent comp, String assetKey) {
        if (comp == null || assetKey == null)
            return false;
        if (comp instanceof ImageComponent img) {
            if (assetKey.equalsIgnoreCase(img.getImageName())
                    || assetKey.equalsIgnoreCase(img.getHoverImageName())
                    || assetKey.equalsIgnoreCase(img.getFallback())) {
                return true;
            }
        } else if (comp instanceof Head2DComponent head) {
            if (assetKey.equalsIgnoreCase(head.getSkin())
                    || assetKey.equalsIgnoreCase(head.getFallbackSkin())
                    || head.getSkin().contains(assetKey)
                    || assetKey.contains(head.getSkin())) {
                return true;
            }
        } else if (comp instanceof ButtonComponent btn) {
            if (assetKey.equalsIgnoreCase(btn.getImageName())
                    || assetKey.equalsIgnoreCase(btn.getHoverImageName())) {
                return true;
            }
        } else if (comp instanceof GifComponent gif) {
            if (assetKey.equalsIgnoreCase(gif.getImageName())
                    || assetKey.contains(gif.getImageName())
                    || gif.getImageName().contains(assetKey)) {
                return true;
            }
        }
        for (UIComponent child : comp.getChildren()) {
            if (matchesAsset(child, assetKey)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasDynamicComponent(List<UIComponent> components) {
        if (components == null)
            return false;
        int size = components.size();
        for (int i = 0; i < size; i++) {
            UIComponent comp = components.get(i);
            if (comp.isDynamic()) {
                return true;
            }
            if (hasDynamicComponent(comp.getChildren())) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasContextDependentComponent(List<UIComponent> components) {
        if (components == null)
            return false;
        int size = components.size();
        for (int i = 0; i < size; i++) {
            UIComponent comp = components.get(i);
            if (comp.isContextDependent()) {
                return true;
            }
            if (hasContextDependentComponent(comp.getChildren())) {
                return true;
            }
        }
        return false;
    }
}