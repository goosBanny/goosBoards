package me.goosbanny.goosboards.display;

import me.goosbanny.goosboards.raycast.Vector3d;
import me.goosbanny.goosboards.raycast.spatial.DisplaySpatialIndex;
import me.goosbanny.goosboards.render.BoardRenderEngine;

import me.goosbanny.goosboards.core.scheduler.UniversalScheduler;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;


import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * High-performance broad-phase proximity gate tracking nearby boards on a periodic task schedule.
 * Decouples board proximity from player look angles and eliminates PlayerMoveEvent overhead.
 */
public class ProximityTracker {

    private static final Map<String, UUID> WORLD_UUID_CACHE = new ConcurrentHashMap<>();

    private final DisplaySpatialIndex spatialIndex;
    private final double activationRadius;
    private final UniversalScheduler scheduler;

    private final Map<UUID, DisplayPos> lastKnownPositions = new ConcurrentHashMap<>();
    private final Map<UUID, List<DisplayPlane>> nearbyBoardsCache = new ConcurrentHashMap<>();

    public ProximityTracker(DisplaySpatialIndex spatialIndex, double activationRadius) {
        this(spatialIndex, activationRadius, null);
    }

    public ProximityTracker(DisplaySpatialIndex spatialIndex, double activationRadius, UniversalScheduler scheduler) {
        this.spatialIndex = Objects.requireNonNull(spatialIndex, "spatialIndex");
        this.activationRadius = activationRadius > 0 ? activationRadius : 32.0;
        this.scheduler = scheduler;
    }

    /**
     * Executes the periodic broad-phase sweep across all online players.
     * Safely executes on the player's owning region thread if a scheduler is provided.
     * Only recalculates buckets when a player's integer block coordinates change.
     */
    public void sweep() {
        if (Bukkit.getServer() != null) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (scheduler != null) {
                    scheduler.runOnEntity(player, this::updatePlayerProximity);
                } else {
                    updatePlayerProximity(player);
                }
            }
        }
    }

    public void updatePlayerProximity(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        Location loc = player.getLocation();
        if (loc.getWorld() == null) {
            return;
        }
        updatePlayerProximity(player.getUniqueId(), loc.getWorld().getName(), loc.getX(), loc.getY(), loc.getZ());
    }

    /**
     * Updates proximity for a player using pre-snapshotted coordinates without Folia region-thread overhead.
     */
    private static final java.util.function.Function<String, UUID> WORLD_ID_FUNCTION =
            w -> UUID.nameUUIDFromBytes(w.getBytes(StandardCharsets.UTF_8));

    public void updatePlayerProximity(UUID uuid, String worldName, double x, double y, double z) {
        if (uuid == null || worldName == null) {
            return;
        }

        int bx = (int) Math.floor(x);
        int by = (int) Math.floor(y);
        int bz = (int) Math.floor(z);

        DisplayPos previousPos = lastKnownPositions.get(uuid);
        if (previousPos != null && previousPos.x() == bx && previousPos.y() == by && previousPos.z() == bz) {
            UUID cachedWorldId = WORLD_UUID_CACHE.get(worldName);
            if (cachedWorldId != null && previousPos.worldId().equals(cachedWorldId)) {
                // Player hasn't moved block coordinates; bucket contents remain identical
                return;
            }
        }

        UUID worldId = WORLD_UUID_CACHE.computeIfAbsent(worldName, WORLD_ID_FUNCTION);
        if (previousPos != null && previousPos.worldId().equals(worldId) && previousPos.x() == bx && previousPos.y() == by && previousPos.z() == bz) {
            return;
        }

        lastKnownPositions.put(uuid, new DisplayPos(worldId, bx, by, bz));

        Vector3d pos = new Vector3d(x, y, z);
        List<DisplayPlane> previouslyNearby = nearbyBoardsCache.get(uuid);
        double queryRadius = (previouslyNearby != null && !previouslyNearby.isEmpty()) ? (activationRadius + 4.0) : activationRadius;

        List<DisplayPlane> candidateBoards = spatialIndex.nearbyBoards(pos, queryRadius, worldName);
        if (candidateBoards == null || candidateBoards.isEmpty()) {
            nearbyBoardsCache.remove(uuid);
            return;
        }

        List<DisplayPlane> nearby = new ArrayList<>(candidateBoards.size());
        for (DisplayPlane p : candidateBoards) {
            if (p.worldName() == null || !p.worldName().equalsIgnoreCase(worldName)) {
                continue;
            }
            boolean wasNearby = previouslyNearby != null && previouslyNearby.contains(p);
            double effectiveRadius = wasNearby ? (activationRadius + 4.0) : activationRadius;
            double d = p.center() != null ? pos.distance(p.center().x(), p.center().y(), p.center().z()) : 0.0;
            if (d <= effectiveRadius) {
                nearby.add(p);
            }
        }

        if (nearby.isEmpty()) {
            nearbyBoardsCache.remove(uuid);
        } else {
            nearbyBoardsCache.put(uuid, nearby);
        }
    }

    /**
     * Updates proximity cache for a collection of player snapshots in a single pass.
     */
    public void updateWithSnapshots(Collection<PlayerSnapshot> snapshots) {
        if (snapshots == null || snapshots.isEmpty()) {
            return;
        }
        for (PlayerSnapshot snap : snapshots) {
            if (snap != null && snap.worldName() != null) {
                updatePlayerProximity(snap.id(), snap.worldName(), snap.x(), snap.y(), snap.z());
            }
        }
    }

    public List<DisplayPlane> getNearbyBoards(UUID playerId) {
        List<DisplayPlane> cached = nearbyBoardsCache.get(playerId);
        return cached != null ? Collections.unmodifiableList(cached) : Collections.emptyList();
    }

    public List<DisplayPlane> getNearbyBoardsSnapshot(UUID playerId) {
        if (playerId == null) {
            return null;
        }
        List<DisplayPlane> cached = nearbyBoardsCache.get(playerId);
        return (cached != null && !cached.isEmpty()) ? new ArrayList<>(cached) : null;
    }

    public boolean isNearAnyBoard(UUID playerId) {
        List<DisplayPlane> cached = nearbyBoardsCache.get(playerId);
        return cached != null && !cached.isEmpty();
    }

    public void onPlayerQuit(UUID playerId) {
        lastKnownPositions.remove(playerId);
        nearbyBoardsCache.remove(playerId);
    }

    public void clear() {
        lastKnownPositions.clear();
        nearbyBoardsCache.clear();
    }
}