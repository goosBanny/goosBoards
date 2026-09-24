package me.goosbanny.goosboards.interaction;

import me.goosbanny.goosboards.GoosBoards;
import me.goosbanny.goosboards.api.ClickType;
import me.goosbanny.goosboards.config.ConfigReloadManager;
import me.goosbanny.goosboards.display.DisplayPlane;
import me.goosbanny.goosboards.display.ProximityTracker;
import me.goosbanny.goosboards.protocol.packet.ClickPacketListener;
import me.goosbanny.goosboards.raycast.DisplayRaycaster;
import me.goosbanny.goosboards.raycast.MutableRaycastHit;
import me.goosbanny.goosboards.raycast.Vector3d;
import me.goosbanny.goosboards.raycast.spatial.DisplaySpatialIndex;
import me.goosbanny.goosboards.scene.BoardConfig;
import me.goosbanny.goosboards.scene.Rect;
import me.goosbanny.goosboards.scene.component.UIComponent;
import me.goosbanny.goosboards.scene.component.container.ScrollPaneComponent;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerAnimationType;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.util.Vector;

import java.util.List;
import java.util.UUID;

/**
 * Handles Bukkit-level player interaction events (arm swings, right-clicks, mouse-wheel scrolls).
 * Enforces broad-phase proximity filtering to guarantee zero server overhead when players are away from displays.
 */
public class PlayerInteractionListener implements Listener {

    private static final ThreadLocal<MutableRaycastHit> RAYCAST_SCRATCH =
            ThreadLocal.withInitial(MutableRaycastHit::new);

    private final ProximityTracker proximityTracker;
    private final DisplaySpatialIndex spatialIndex;
    private final ConfigReloadManager reloadManager;
    private final ClickPacketListener clickListener;

    public PlayerInteractionListener(
            ProximityTracker proximityTracker,
            DisplaySpatialIndex spatialIndex,
            ConfigReloadManager reloadManager,
            ClickPacketListener clickListener
    ) {
        this.proximityTracker = proximityTracker;
        this.spatialIndex = spatialIndex;
        this.reloadManager = reloadManager;
        this.clickListener = clickListener;
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onPlayerAnimation(PlayerAnimationEvent event) {
        if (event == null || event.getPlayer() == null) return;
        if (proximityTracker != null && !proximityTracker.isNearAnyBoard(event.getPlayer().getUniqueId())) {
            return;
        }
        if (event.getAnimationType() == PlayerAnimationType.ARM_SWING) {
            if (clickListener != null) {
                clickListener.handlePlayerClick(event.getPlayer(), ClickType.LEFT_CLICK);
            }
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event == null || event.getPlayer() == null) return;
        if (event.getHand() == EquipmentSlot.OFF_HAND) return;

        Action action = event.getAction();
        if (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK) {
            Player player = event.getPlayer();
            if (proximityTracker != null && proximityTracker.isNearAnyBoard(player.getUniqueId())) {
                if (isAimingAtBoard(player, 6.0)) {
                    event.setCancelled(true);
                }
            }
            if (clickListener != null) {
                clickListener.handlePlayerClick(player, ClickType.RIGHT_CLICK);
            }
        }
    }

    private boolean isAimingAtBoard(Player player, double maxDist) {
        if (spatialIndex == null || player == null) return false;
        Location eyeLoc = player.getEyeLocation();
        Vector3d eyePos = new Vector3d(eyeLoc.getX(), eyeLoc.getY(), eyeLoc.getZ());
        Vector dirVec = eyeLoc.getDirection();
        Vector3d dir = new Vector3d(dirVec.getX(), dirVec.getY(), dirVec.getZ()).normalize();

        List<DisplayPlane> nearby =
                spatialIndex.nearbyBoards(eyePos, maxDist, player.getWorld().getName());
        if (nearby == null || nearby.isEmpty()) return false;

        MutableRaycastHit hit = RAYCAST_SCRATCH.get();
        for (DisplayPlane plane : nearby) {
            DisplayRaycaster.intersect(plane, eyePos.x(), eyePos.y(), eyePos.z(), dir.x(), dir.y(), dir.z(), hit);
            if (hit.hit && hit.distance <= maxDist) {
                return true;
            }
        }
        return false;
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPlayerItemHeld(PlayerItemHeldEvent event) {
        if (event == null || event.getPlayer() == null) return;
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        // Fast-gate: if player is not near any board, ignore with zero allocation or raycasts
        if (proximityTracker == null || !proximityTracker.isNearAnyBoard(uuid)) {
            return;
        }

        if (spatialIndex == null || reloadManager == null || clickListener == null || clickListener.getRaycastPool() == null) {
            return;
        }

        int prev = event.getPreviousSlot();
        int next = event.getNewSlot();
        Location eyeLoc = player.getEyeLocation();
        Vector3d eyePos = new Vector3d(eyeLoc.getX(), eyeLoc.getY(), eyeLoc.getZ());
        Vector dirVec = eyeLoc.getDirection();
        Vector3d dir = new Vector3d(dirVec.getX(), dirVec.getY(), dirVec.getZ()).normalize();
        String worldName = player.getWorld().getName();

        // Offload broad phase, raycasting, and scroll math to async raycast pool (zero server thread impact)
        clickListener.getRaycastPool().submit(() -> {
            List<DisplayPlane> nearby = (proximityTracker != null)
                    ? proximityTracker.getNearbyBoardsSnapshot(uuid)
                    : spatialIndex.nearbyBoards(eyePos, 16.0, worldName);
            if (nearby == null || nearby.isEmpty()) {
                return;
            }

            MutableRaycastHit hit = RAYCAST_SCRATCH.get();
            double bestDist = Double.MAX_VALUE;
            UUID bestDisplayId = null;
            int hitX = 0;
            int hitY = 0;

            for (DisplayPlane plane : nearby) {
                DisplayRaycaster.intersect(
                        plane,
                        eyePos.x(), eyePos.y(), eyePos.z(),
                        dir.x(), dir.y(), dir.z(),
                        hit
                );
                if (hit.hit && hit.distance < bestDist) {
                    bestDist = hit.distance;
                    bestDisplayId = hit.displayId;
                    hitX = (int) Math.round(hit.pixelX);
                    hitY = (int) Math.round(hit.pixelY);
                }
            }

            if (bestDisplayId == null) {
                return;
            }

            String boardId = reloadManager.getBoardIdForDisplay(bestDisplayId);
            if (boardId == null) {
                return;
            }

            BoardConfig.SceneDefinition scene = reloadManager.getActiveScene(boardId, uuid);
            if (scene == null || scene.components() == null) {
                return;
            }

            ScrollPaneComponent pane = findTargetScrollPane(scene.components(), hitX, hitY, uuid);
            if (pane != null && pane.isMouseScroll()) {
                boolean isUp = (next < prev) ? (prev == 8 && next == 0) : (next - prev == 1 && !(prev == 0 && next == 8));
                int scrollDelta = (isUp ? 1 : -1) * pane.getMouseScrollAmount();
                if (pane.scroll(uuid, scrollDelta)) {
                    if (GoosBoards.getInstance() != null && GoosBoards.getInstance().getRenderEngine() != null) {
                        GoosBoards.getInstance().getRenderEngine().requestDirtyPass(boardId);
                    }
                }
            }
        });
    }

    private ScrollPaneComponent findTargetScrollPane(
            List<UIComponent> components,
            int pixelX,
            int pixelY,
            UUID viewerId
    ) {
        if (components == null) return null;
        for (int i = components.size() - 1; i >= 0; i--) {
            UIComponent comp = components.get(i);
            ScrollPaneComponent pane = searchScrollPane(comp, pixelX, pixelY, viewerId);
            if (pane != null) return pane;
        }
        return null;
    }

    private ScrollPaneComponent searchScrollPane(
            UIComponent comp,
            int pixelX,
            int pixelY,
            UUID viewerId
    ) {
        if (comp == null) return null;
        Rect b = comp.getBounds();
        if (b == null || !b.contains(pixelX, pixelY)) {
            return null;
        }

        if (comp instanceof ScrollPaneComponent scrollPane) {
            int localX = pixelX - b.x();
            int localY = pixelY - b.y();
            if (scrollPane.isInViewport(localX, localY)) {
                int contentX = localX + scrollPane.getScrollOffsetX();
                int contentY = localY + scrollPane.getEffectiveScrollOffsetY(viewerId);
                for (int i = scrollPane.getChildren().size() - 1; i >= 0; i--) {
                    UIComponent child = scrollPane.getChildren().get(i);
                    ScrollPaneComponent nested = searchScrollPane(child, contentX, contentY, viewerId);
                    if (nested != null) return nested;
                }
                return scrollPane;
            }
            return null;
        }

        for (int i = comp.getChildren().size() - 1; i >= 0; i--) {
            UIComponent child = comp.getChildren().get(i);
            ScrollPaneComponent nested = searchScrollPane(child, pixelX, pixelY, viewerId);
            if (nested != null) return nested;
        }
        return null;
    }
}
