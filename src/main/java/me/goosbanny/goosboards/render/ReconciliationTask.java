package me.goosbanny.goosboards.render;

import me.goosbanny.goosboards.core.logging.DebugLogger;
import me.goosbanny.goosboards.protocol.VirtualEntityTracker;
import me.goosbanny.goosboards.display.DisplayPlane;
import me.goosbanny.goosboards.raycast.Vector3d;
import me.goosbanny.goosboards.display.PlayerSnapshot;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Autonomous 3-second reconciliation loop guaranteeing convergence against any
 * event drops,
 * Folia region thread timing anomalies, or reload races.
 * <p>
 * Evaluates the ground-truth "should-be-visible" relationship between online
 * players
 * and active displays, correcting Vanished Boards, Ghost Boards, and Viewer Set
 * Drift.
 */
public class ReconciliationTask {

    private final VirtualEntityTracker entityTracker;

    public ReconciliationTask(VirtualEntityTracker entityTracker) {
        this.entityTracker = Objects.requireNonNull(entityTracker, "entityTracker");
    }

    /**
     * Executes the reconciliation pass over the current display states and player
     * snapshots.
     *
     * @param displayStates active display render states
     * @param snapshots     current online player snapshots
     * @return count of anomalies self-corrected during this pass
     */
    public int reconcile(Map<UUID, DisplayRenderState> displayStates, Collection<PlayerSnapshot> snapshots) {
        if (displayStates == null || displayStates.isEmpty() || snapshots == null || snapshots.isEmpty()) {
            return 0;
        }

        int corrections = 0;

        Set<UUID> onlinePlayerIds = new HashSet<>(snapshots.size());
        for (PlayerSnapshot snap : snapshots) {
            if (snap != null && snap.id() != null && snap.player() != null && snap.player().isOnline()) {
                onlinePlayerIds.add(snap.id());
            }
        }

        // Clean up any stale active viewer entries for players who are no longer online
        for (DisplayRenderState state : displayStates.values()) {
            for (UUID viewerId : new ArrayList<>(state.getActiveViewerUuids())) {
                if (!onlinePlayerIds.contains(viewerId)) {
                    state.removeViewer(viewerId);
                    corrections++;
                }
            }
        }

        // Clean up any ghost rigs for players who are no longer online
        for (UUID trackedPlayerId : new ArrayList<>(entityTracker.getActiveRigs().keySet())) {
            if (!onlinePlayerIds.contains(trackedPlayerId)) {
                DebugLogger.log("Reconciliation", "Purging stale rigs for offline player %s", trackedPlayerId);
                entityTracker.despawnAll(trackedPlayerId);
                corrections++;
            }
        }

        for (DisplayRenderState state : displayStates.values()) {
            DisplayPlane plane = state.getPlane();
            if (plane == null)
                continue;

            UUID displayId = plane.id();
            double maxDist = plane.activationRadius() > 0 ? plane.activationRadius() : 32.0;
            double maxDistSq = maxDist * maxDist;
            Vector3d center = plane.center();

            for (PlayerSnapshot snap : snapshots) {
                if (snap == null || snap.player() == null || !snap.player().isOnline()) {
                    continue;
                }

                UUID playerId = snap.id();
                boolean sameWorld = snap.worldName().equals(plane.worldName());
                boolean inRange = false;

                if (sameWorld) {
                    double dx = snap.x() - center.x();
                    double dy = snap.y() - center.y();
                    double dz = snap.z() - center.z();
                    double distSq = dx * dx + dy * dy + dz * dz;
                    inRange = distSq <= maxDistSq;
                }

                boolean hasRig = entityTracker.getRig(snap.player(), displayId) != null;
                boolean inViewerSet = state.getActiveViewerUuids().contains(playerId);

                if (inRange && !hasRig) {
                    // 1. Vanished Board: player should see it, but rig is missing
                    DebugLogger.log("Reconciliation",
                            "Vanished board detected for player %s on display %s -> respawning rig",
                            snap.name(), displayId);
                    entityTracker.spawnRig(snap.player(), plane, state.getMapIds());
                    state.getActiveViewerUuids().add(playerId);
                    state.getPendingFullFlushUuids().add(playerId);
                    corrections++;
                } else if (!inRange && hasRig) {
                    // 2. Ghost Board: player out of range or wrong world, but rig still spawned
                    DebugLogger.log("Reconciliation",
                            "Ghost board detected for player %s on display %s -> despawning rig",
                            snap.name(), displayId);
                    entityTracker.despawnRig(snap.player(), displayId);
                    state.removeViewer(playerId);
                    corrections++;
                } else if (inRange && hasRig && !inViewerSet) {
                    // 3. Viewer Set Drift: rig exists client-side but engine is not
                    // rendering/updating it
                    DebugLogger.log("Reconciliation",
                            "Viewer set drift detected for player %s on display %s -> restoring viewer state",
                            snap.name(), displayId);
                    state.getActiveViewerUuids().add(playerId);
                    state.getPendingFullFlushUuids().add(playerId);
                    corrections++;
                }
            }
        }

        return corrections;
    }
}