package me.goosbanny.goosboards.protocol;

import me.goosbanny.goosboards.core.logging.DebugLogger;
import me.goosbanny.goosboards.display.DisplayPlane;
import org.bukkit.entity.Player;


import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;

public class VirtualEntityTracker {
    private final QuarantinedIdAllocator allocator;
    private final BiFunction<Player, DisplayPlane, VirtualEntityRig> rigFactory;
    private final Map<UUID, Map<UUID, VirtualEntityRig>> activeRigs = new ConcurrentHashMap<>();

    public VirtualEntityTracker(QuarantinedIdAllocator allocator) {
        this(allocator, (player, plane) -> new VirtualEntityRig(player, plane, allocator));
    }

    public VirtualEntityTracker(QuarantinedIdAllocator allocator, BiFunction<Player, DisplayPlane, VirtualEntityRig> rigFactory) {
        this.allocator = allocator;
        this.rigFactory = rigFactory;
    }

    public VirtualEntityRig spawnRig(Player player, DisplayPlane plane) {
        return spawnRig(player, plane, null);
    }

    public VirtualEntityRig spawnRig(Player player, DisplayPlane plane, int[] mapIds) {
        DebugLogger.log("RigTracker", "spawnRig for player %s on display %s (customMapIds=%s)",
                player.getName(), plane.id(), (mapIds != null ? mapIds.length + " IDs" : "null"));
        VirtualEntityRig rig = (mapIds != null)
                ? new VirtualEntityRig(player, plane, allocator, null, mapIds)
                : rigFactory.apply(player, plane);
        Map<UUID, VirtualEntityRig> playerRigs = activeRigs.computeIfAbsent(player.getUniqueId(), k -> new ConcurrentHashMap<>());
        playerRigs.compute(plane.id(), (key, oldRig) -> {
            if (oldRig != null) {
                oldRig.despawn();
            }
            rig.spawn();
            return rig;
        });
        return rig;
    }

    public void despawnRig(Player player, UUID displayId) {
        if (player != null) {
            DebugLogger.log("RigTracker", "despawnRig for player %s on display %s", player.getName(), displayId);
            despawnRig(player.getUniqueId(), displayId);
        }
    }

    public void despawnRig(UUID playerId, UUID displayId) {
        if (playerId == null || displayId == null) return;
        activeRigs.computeIfPresent(playerId, (pId, playerRigs) -> {
            VirtualEntityRig rig = playerRigs.remove(displayId);
            if (rig != null) {
                rig.despawn();
            }
            return playerRigs.isEmpty() ? null : playerRigs;
        });
    }

    public void despawnAll(Player player) {
        if (player != null) {
            DebugLogger.log("RigTracker", "despawnAll for player %s", player.getName());
            despawnAll(player.getUniqueId());
        }
    }

    public void despawnAll(UUID playerId) {
        if (playerId == null) return;
        Map<UUID, VirtualEntityRig> playerRigs = activeRigs.remove(playerId);
        if (playerRigs != null) {
            for (VirtualEntityRig rig : playerRigs.values()) {
                rig.despawn();
            }
            playerRigs.clear();
        }
    }

    public void despawnAllForDisplay(UUID displayId) {
        DebugLogger.log("RigTracker", "despawnAllForDisplay for display %s", displayId);
        for (Map<UUID, VirtualEntityRig> playerRigs : activeRigs.values()) {
            VirtualEntityRig rig = playerRigs.remove(displayId);
            if (rig != null) {
                rig.despawn();
            }
        }
    }

    public void despawnAll() {
        for (Map<UUID, VirtualEntityRig> playerRigs : activeRigs.values()) {
            for (VirtualEntityRig rig : playerRigs.values()) {
                rig.despawn();
            }
            playerRigs.clear();
        }
        activeRigs.clear();
    }

    public Map<UUID, Map<UUID, VirtualEntityRig>> getActiveRigs() {
        return Collections.unmodifiableMap(activeRigs);
    }

    public VirtualEntityRig getRig(Player player, UUID displayId) {
        return player != null ? getRig(player.getUniqueId(), displayId) : null;
    }

    public VirtualEntityRig getRig(UUID playerId, UUID displayId) {
        if (playerId == null || displayId == null) return null;
        Map<UUID, VirtualEntityRig> playerRigs = activeRigs.get(playerId);
        return playerRigs != null ? playerRigs.get(displayId) : null;
    }
}