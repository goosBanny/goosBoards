package me.goosbanny.goosboards.render;

import io.netty.channel.Channel;
import me.goosbanny.goosboards.display.DisplayPlane;
import me.goosbanny.goosboards.render.buffer.CanvasBufferImpl;
import me.goosbanny.goosboards.render.diff.TileDiffer.DirtyTile;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * State container for a single virtual display plane.
 * <p>
 * <b>Concurrency Invariant:</b> Scratch lists ({@code dirtyScratch},
 * {@code fullFlushScratch},
 * {@code channelsScratch}, {@code viewersScratch}, {@code newViewersScratch})
 * are confined to
 * single-threaded access during the render tick. Viewer UUID sets and drop
 * counters are thread-safe.
 */
public class DisplayRenderState {
    final String boardId;
    DisplayPlane plane;
    CanvasBufferImpl canvasBuffer;
    int[] mapIds;
    final Set<UUID> activeViewerUuids = ConcurrentHashMap.newKeySet();
    final Set<UUID> pendingFullFlushUuids = ConcurrentHashMap.newKeySet();
    final Set<UUID> aimingViewers = ConcurrentHashMap.newKeySet();
    final Map<UUID, Set<String>> viewerHoveredComponents = new ConcurrentHashMap<>();
    final Map<UUID, Integer> consecutiveDrops = new ConcurrentHashMap<>();
    final Map<UUID, CanvasBufferImpl> perViewerCanvases = new ConcurrentHashMap<>();
    final List<DirtyTile> dirtyScratch;
    final List<DirtyTile> fullFlushScratch;
    final List<Channel> channelsScratch = new ArrayList<>(16);
    final List<Player> viewersScratch = new ArrayList<>();
    final List<Player> newViewersScratch = new ArrayList<>();
    final List<UUID> activeViewersScratch = new ArrayList<>(16);
    final List<Player> targetViewersScratch = new ArrayList<>(16);
    final Set<String> hitSetScratch = new HashSet<>(16);
    boolean initialRenderDone = false;
    volatile boolean needsRenderPass = false;

    public DisplayRenderState(String boardId, DisplayPlane plane, int[] mapIds) {
        this.boardId = boardId;
        this.plane = plane;
        this.canvasBuffer = new CanvasBufferImpl(plane.getWidthTiles(), plane.getHeightTiles());
        this.mapIds = mapIds;
        int totalTiles = plane.getWidthTiles() * plane.getHeightTiles();
        this.dirtyScratch = new ArrayList<>(totalTiles);
        this.fullFlushScratch = new ArrayList<>(totalTiles);
    }

    public void markNeedsRenderPass() {
        this.needsRenderPass = true;
    }

    public boolean needsRenderPass() {
        return needsRenderPass;
    }

    public CanvasBufferImpl getOrCreateViewerCanvas(UUID viewerId) {
        return perViewerCanvases.computeIfAbsent(viewerId,
                id -> new CanvasBufferImpl(plane.getWidthTiles(), plane.getHeightTiles()));
    }

    public void removeViewer(UUID viewerId) {
        activeViewerUuids.remove(viewerId);
        pendingFullFlushUuids.remove(viewerId);
        aimingViewers.remove(viewerId);
        viewerHoveredComponents.remove(viewerId);
        consecutiveDrops.remove(viewerId);
        perViewerCanvases.remove(viewerId);
    }

    public void clearViewers() {
        activeViewerUuids.clear();
        pendingFullFlushUuids.clear();
        aimingViewers.clear();
        viewerHoveredComponents.clear();
        consecutiveDrops.clear();
        perViewerCanvases.clear();
    }

    public void recordDrop(UUID viewerId) {
        consecutiveDrops.merge(viewerId, 1, Integer::sum);
    }

    public void resetDrop(UUID viewerId) {
        consecutiveDrops.remove(viewerId);
    }

    public int getDrops(UUID viewerId) {
        return consecutiveDrops.getOrDefault(viewerId, 0);
    }

    public void clearPerViewerCanvases() {
        perViewerCanvases.clear();
    }

    public String getBoardId() {
        return boardId;
    }

    public DisplayPlane getPlane() {
        return plane;
    }

    public void setPlane(DisplayPlane plane) {
        this.plane = plane;
    }

    public void updateMapIdsAndBuffer(int[] mapIds, CanvasBufferImpl canvasBuffer) {
        this.mapIds = mapIds;
        this.canvasBuffer = canvasBuffer;
    }

    public CanvasBufferImpl getCanvasBuffer() {
        return canvasBuffer;
    }

    public int[] getMapIds() {
        return mapIds;
    }

    public Set<UUID> getActiveViewerUuids() {
        return activeViewerUuids;
    }

    public Set<UUID> getPendingFullFlushUuids() {
        return pendingFullFlushUuids;
    }

    public Set<UUID> getAimingViewers() {
        return aimingViewers;
    }

    public Map<UUID, Set<String>> getViewerHoveredComponents() {
        return viewerHoveredComponents;
    }

    public Map<UUID, CanvasBufferImpl> getPerViewerCanvases() {
        return perViewerCanvases;
    }

    public boolean isInitialRenderDone() {
        return initialRenderDone;
    }

    public void setInitialRenderDone(boolean initialRenderDone) {
        this.initialRenderDone = initialRenderDone;
    }
}
