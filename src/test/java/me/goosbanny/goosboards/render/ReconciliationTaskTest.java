package me.goosbanny.goosboards.render;

import me.goosbanny.goosboards.protocol.QuarantinedIdAllocator;
import me.goosbanny.goosboards.protocol.VirtualEntityRig;
import me.goosbanny.goosboards.protocol.VirtualEntityTracker;
import me.goosbanny.goosboards.display.DisplayPlane;
import me.goosbanny.goosboards.raycast.Vector3d;
import me.goosbanny.goosboards.display.PlayerSnapshot;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReconciliationTaskTest {

    private VirtualEntityTracker tracker;
    private ReconciliationTask reconciliationTask;
    private Player player;
    private UUID playerId;
    private UUID displayId;
    private DisplayPlane plane;
    private DisplayRenderState state;
    private Map<UUID, DisplayRenderState> displayStates;

    @BeforeEach
    void setUp() {
        QuarantinedIdAllocator allocator = new QuarantinedIdAllocator();
        tracker = Mockito.spy(new VirtualEntityTracker(allocator));
        reconciliationTask = new ReconciliationTask(tracker);

        player = Mockito.mock(Player.class);
        playerId = UUID.randomUUID();
        Mockito.when(player.getUniqueId()).thenReturn(playerId);
        Mockito.when(player.getName()).thenReturn("Tester");
        Mockito.when(player.isOnline()).thenReturn(true);

        displayId = UUID.randomUUID();
        plane = new DisplayPlane(displayId, "world", new Vector3d(0, 64, 0), 2, 2,
                new Vector3d(1, 0, 0), new Vector3d(0, -1, 0), 32.0, 16.0);

        state = new DisplayRenderState("test_board", plane, new int[]{100, 101, 102, 103});
        displayStates = new ConcurrentHashMap<>();
        displayStates.put(displayId, state);
    }

    @Test
    @DisplayName("Vanished board is detected and respawned when in range without rig")
    void testVanishedBoardRecovery() {
        PlayerSnapshot snap = new PlayerSnapshot(
                playerId, "Tester", "world", 5.0, 64.0, 5.0, 1.62, player
        );

        // Player is in range, but has no rig and not in viewer set
        assertEquals(0, state.getActiveViewerUuids().size());
        assertEquals(0, state.getPendingFullFlushUuids().size());

        int corrections = reconciliationTask.reconcile(displayStates, List.of(snap));

        assertEquals(1, corrections);
        assertTrue(state.getActiveViewerUuids().contains(playerId));
        assertTrue(state.getPendingFullFlushUuids().contains(playerId));
        Mockito.verify(tracker).spawnRig(Mockito.eq(player), Mockito.eq(plane), Mockito.any());
    }

    @Test
    @DisplayName("Ghost board is detected and despawned when out of range with rig")
    void testGhostBoardCleanup() {
        // Pre-spawn rig for player
        VirtualEntityRig rig = Mockito.mock(VirtualEntityRig.class);
        tracker.spawnRig(player, plane);
        state.getActiveViewerUuids().add(playerId);

        // Player is far away (100 blocks)
        PlayerSnapshot snap = new PlayerSnapshot(
                playerId, "Tester", "world", 100.0, 64.0, 100.0, 1.62, player
        );

        int corrections = reconciliationTask.reconcile(displayStates, List.of(snap));

        assertEquals(1, corrections);
        assertFalse(state.getActiveViewerUuids().contains(playerId));
        Mockito.verify(tracker).despawnRig(player, displayId);
    }

    @Test
    @DisplayName("Ghost board is despawned when player is in different world")
    void testGhostBoardWrongWorld() {
        tracker.spawnRig(player, plane);
        state.getActiveViewerUuids().add(playerId);

        PlayerSnapshot snap = new PlayerSnapshot(
                playerId, "Tester", "nether", 0.0, 64.0, 0.0, 1.62, player
        );

        int corrections = reconciliationTask.reconcile(displayStates, List.of(snap));

        assertEquals(1, corrections);
        assertFalse(state.getActiveViewerUuids().contains(playerId));
        Mockito.verify(tracker).despawnRig(player, displayId);
    }

    @Test
    @DisplayName("Viewer set drift is corrected when player has rig but missing from activeViewerUuids")
    void testViewerDriftCorrection() {
        tracker.spawnRig(player, plane);
        // Rig exists, but activeViewerUuids does NOT have playerId
        state.getActiveViewerUuids().clear();

        PlayerSnapshot snap = new PlayerSnapshot(
                playerId, "Tester", "world", 5.0, 64.0, 5.0, 1.62, player
        );

        int corrections = reconciliationTask.reconcile(displayStates, List.of(snap));

        assertEquals(1, corrections);
        assertTrue(state.getActiveViewerUuids().contains(playerId));
        assertTrue(state.getPendingFullFlushUuids().contains(playerId));
    }

    @Test
    @DisplayName("Zero corrections when player state is fully consistent")
    void testConsistentStateProducesNoCorrections() {
        tracker.spawnRig(player, plane);
        state.getActiveViewerUuids().add(playerId);

        PlayerSnapshot snap = new PlayerSnapshot(
                playerId, "Tester", "world", 5.0, 64.0, 5.0, 1.62, player
        );

        int corrections = reconciliationTask.reconcile(displayStates, List.of(snap));

        assertEquals(0, corrections);
    }

    @Test
    @DisplayName("Offline player with lingering rig or viewer entry is purged")
    void testOfflinePlayerCleanup() {
        tracker.spawnRig(player, plane);
        state.getActiveViewerUuids().add(playerId);

        // Snapshots list does NOT contain this player (they went offline)
        Player otherPlayer = Mockito.mock(Player.class);
        UUID otherId = UUID.randomUUID();
        Mockito.when(otherPlayer.getUniqueId()).thenReturn(otherId);
        Mockito.when(otherPlayer.isOnline()).thenReturn(true);
        PlayerSnapshot otherSnap = new PlayerSnapshot(otherId, "Other", "world", 50.0, 64.0, 50.0, 1.62, otherPlayer);

        int corrections = reconciliationTask.reconcile(displayStates, List.of(otherSnap));

        assertTrue(corrections >= 2, "Expected corrections for viewer cleanup and rig despawn");
        assertFalse(state.getActiveViewerUuids().contains(playerId));
        Mockito.verify(tracker).despawnAll(playerId);
    }
}