package me.goosbanny.goosboards.protocol.packet;

import me.goosbanny.goosboards.GoosBoards;
import me.goosbanny.goosboards.interaction.InteractionRateLimiter;
import me.goosbanny.goosboards.interaction.InteractionRouter;

import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.packettype.PacketTypeCommon;
import com.github.retrooper.packetevents.protocol.player.DiggingAction;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerDigging;
import me.goosbanny.goosboards.api.ClickType;
import me.goosbanny.goosboards.core.logging.DebugLogger;
import me.goosbanny.goosboards.core.scheduler.UniversalScheduler;
import me.goosbanny.goosboards.display.DisplayPlane;
import me.goosbanny.goosboards.display.DistanceLodTracker;
import me.goosbanny.goosboards.raycast.DisplayRaycaster;
import me.goosbanny.goosboards.raycast.spatial.DisplaySpatialIndex;
import me.goosbanny.goosboards.raycast.MutableRaycastHit;
import me.goosbanny.goosboards.raycast.PlayerRaycastSnapshot;
import me.goosbanny.goosboards.display.ProximityTracker;
import me.goosbanny.goosboards.raycast.RaycastResult;
import me.goosbanny.goosboards.raycast.Vector3d;
import me.goosbanny.goosboards.render.BoardRenderEngine;
import me.goosbanny.goosboards.display.PlayerSnapshot;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import com.github.retrooper.packetevents.protocol.player.InteractionHand;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientUseItem;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Intercepts client input packets to detect clicks on virtual display planes.
 *
 * ⚠️ EMPTY-HAND RIGHT-CLICK CAVEAT (PROJECT.md §4.1a):
 * The Minecraft client does NOT send USE_ITEM packets if the player has an
 * empty main-hand
 * AND empty off-hand clicking at empty air beyond vanilla reach (~4.5 blocks).
 * This is an immutable vanilla client constraint. This behavior is documented
 * degradation:
 * right-click actions on boards beyond vanilla reach require the player to hold
 * any item.
 * Left-click actions (via PLAYER_DIGGING / ARM_ANIMATION) are reliable at full
 * interaction radius.
 */
public class ClickPacketListener extends PacketListenerAbstract {
    public static final double DEFAULT_MAX_ACTIVATION_RADIUS = 32.0;
    private static final long LEFT_CLICK_DEBOUNCE_MS = 100L;
    private static final long RIGHT_CLICK_DEBOUNCE_MS = 150L;
    private static final ThreadLocal<MutableRaycastHit> RAYCAST_SCRATCH = ThreadLocal
            .withInitial(MutableRaycastHit::new);

    private record LastLeftClick(PacketTypeCommon packetType, long timestamp) {
    }

    private final UniversalScheduler scheduler;
    private final DisplaySpatialIndex spatialIndex;
    private final ExecutorService raycastPool;
    private final InteractionRouter router;
    private final InteractionRateLimiter rateLimiter;
    private final double maxActivationRadius;
    private final Map<UUID, LastLeftClick> lastLeftClicks = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastRightClicks = new ConcurrentHashMap<>();

    public ClickPacketListener(UniversalScheduler scheduler, DisplaySpatialIndex spatialIndex,
            InteractionRouter router) {
        this(scheduler, spatialIndex, router, null, createDefaultRaycastPool(), DEFAULT_MAX_ACTIVATION_RADIUS);
    }

    public ClickPacketListener(UniversalScheduler scheduler, DisplaySpatialIndex spatialIndex, InteractionRouter router,
            InteractionRateLimiter rateLimiter) {
        this(scheduler, spatialIndex, router, rateLimiter, createDefaultRaycastPool(), DEFAULT_MAX_ACTIVATION_RADIUS);
    }

    public ClickPacketListener(
            UniversalScheduler scheduler,
            DisplaySpatialIndex spatialIndex,
            InteractionRouter router,
            ExecutorService raycastPool,
            double maxActivationRadius) {
        this(scheduler, spatialIndex, router, null, raycastPool, maxActivationRadius);
    }

    public ClickPacketListener(
            UniversalScheduler scheduler,
            DisplaySpatialIndex spatialIndex,
            InteractionRouter router,
            InteractionRateLimiter rateLimiter,
            ExecutorService raycastPool,
            double maxActivationRadius) {
        super(PacketListenerPriority.NORMAL);
        this.scheduler = scheduler;
        this.spatialIndex = spatialIndex;
        this.router = router;
        this.rateLimiter = rateLimiter;
        this.raycastPool = raycastPool;
        this.maxActivationRadius = maxActivationRadius > 0.0 ? maxActivationRadius : DEFAULT_MAX_ACTIVATION_RADIUS;
    }

    public static ExecutorService createDefaultRaycastPool() {
        return createRaycastPool(1);
    }

    public static ExecutorService createRaycastPool(int threads) {
        int poolSize = Math.max(1, threads);
        return new ThreadPoolExecutor(
                poolSize,
                poolSize,
                60L,
                TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(500),
                r -> {
                    Thread t = new Thread(r, "GoosBoards-Raycast-Worker");
                    t.setDaemon(true);
                    return t;
                },
                new ThreadPoolExecutor.CallerRunsPolicy());
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        ClickType clickType;
        boolean isInteractEntity = false;

        if (event.getPacketType() == PacketType.Play.Client.PLAYER_DIGGING) {
            WrapperPlayClientPlayerDigging digging = new WrapperPlayClientPlayerDigging(event);
            if (digging.getAction() != DiggingAction.START_DIGGING) {
                return;
            }
            clickType = ClickType.LEFT_CLICK;
        } else if (event.getPacketType() == PacketType.Play.Client.ANIMATION) {
            clickType = ClickType.LEFT_CLICK;
        } else if (event.getPacketType() == PacketType.Play.Client.USE_ITEM) {
            WrapperPlayClientUseItem useItem = new WrapperPlayClientUseItem(event);
            if (useItem.getHand() == InteractionHand.OFF_HAND) {
                return;
            }
            clickType = ClickType.RIGHT_CLICK;
        } else if (event.getPacketType() == PacketType.Play.Client.INTERACT_ENTITY) {
            WrapperPlayClientInteractEntity interact = new WrapperPlayClientInteractEntity(event);
            if (interact.getHand() == InteractionHand.OFF_HAND) {
                return;
            }
            if (interact.getAction() == WrapperPlayClientInteractEntity.InteractAction.ATTACK) {
                clickType = ClickType.LEFT_CLICK;
            } else if (interact.getAction() == WrapperPlayClientInteractEntity.InteractAction.INTERACT_AT) {
                // Ignore INTERACT_AT companion packet to prevent double-firing with INTERACT
                return;
            } else {
                clickType = ClickType.RIGHT_CLICK;
            }
            isInteractEntity = true;
        } else {
            return;
        }

        UUID uuid = event.getUser().getUUID();

        // Upstream Netty rate limiting: drop excess bursts before any region scheduling
        // or raycast queuing
        if (rateLimiter != null && !rateLimiter.tryConsumeClick(uuid)) {
            return;
        }

        // Debounce left-clicks: suppress companion packets (e.g. ANIMATION after
        // DIGGING) for the same click
        if (clickType == ClickType.LEFT_CLICK) {
            long now = System.currentTimeMillis();
            LastLeftClick last = lastLeftClicks.get(uuid);
            if (last != null && (now - last.timestamp()) < LEFT_CLICK_DEBOUNCE_MS) {
                if (last.packetType() != event.getPacketType()) {
                    return;
                }
            }
            lastLeftClicks.put(uuid, new LastLeftClick(event.getPacketType(), now));
        } else if (clickType == ClickType.RIGHT_CLICK) {
            long now = System.currentTimeMillis();
            Long last = lastRightClicks.get(uuid);
            if (last != null && (now - last) < RIGHT_CLICK_DEBOUNCE_MS) {
                return;
            }
            lastRightClicks.put(uuid, now);
        }

        // Fast-gate on Netty thread: if player is nowhere near any board, drop
        // immediately without scheduling on region thread
        if (proximityTracker != null && !proximityTracker.isNearAnyBoard(uuid)) {
            return;
        }

        Player player = event.getPlayer() instanceof Player p ? p : Bukkit.getPlayer(uuid);
        if (player == null || !player.isOnline()) {
            return;
        }

        dispatchRaycast(player, clickType, isInteractEntity);
    }

    /**
     * Entry point for Bukkit event fallbacks (e.g. PlayerAnimationEvent for
     * left-click,
     * or PlayerInteractEvent for right-click on air / block).
     * Subject to the same debouncing and off-thread raycast dispatch.
     */
    public void handlePlayerClick(Player player, ClickType clickType) {
        if (player == null || !player.isOnline())
            return;
        UUID uuid = player.getUniqueId();

        long now = System.currentTimeMillis();
        if (clickType == ClickType.LEFT_CLICK) {
            LastLeftClick last = lastLeftClicks.get(uuid);
            if (last != null && (now - last.timestamp()) < LEFT_CLICK_DEBOUNCE_MS) {
                return;
            }
        } else if (clickType == ClickType.RIGHT_CLICK) {
            Long last = lastRightClicks.get(uuid);
            if (last != null && (now - last) < RIGHT_CLICK_DEBOUNCE_MS) {
                return;
            }
        }

        if (rateLimiter != null && !rateLimiter.tryConsumeClick(uuid)) {
            return;
        }

        if (clickType == ClickType.LEFT_CLICK) {
            lastLeftClicks.put(uuid, new LastLeftClick(null, now));
        } else if (clickType == ClickType.RIGHT_CLICK) {
            lastRightClicks.put(uuid, now);
        }

        if (proximityTracker != null && !proximityTracker.isNearAnyBoard(uuid)) {
            return;
        }

        dispatchRaycast(player, clickType, false);
    }

    private BoardRenderEngine renderEngine;

    public void setRenderEngine(BoardRenderEngine renderEngine) {
        this.renderEngine = renderEngine;
    }

    private void dispatchRaycast(Player player, ClickType clickType, boolean isInteractEntity) {
        if (player == null || !player.isOnline()) {
            return;
        }
        UUID uuid = player.getUniqueId();

        // 1. Obtain spatial snapshot without scheduling on entity thread
        PlayerRaycastSnapshot snapshot = null;
        if (renderEngine != null) {
            PlayerSnapshot engineSnap = renderEngine.getPlayerSnapshot(uuid);
            if (engineSnap != null) {
                Vector3d eyePos = new Vector3d(engineSnap.x(), engineSnap.y() + engineSnap.eyeHeight(), engineSnap.z());
                Vector3d lookDir = new Vector3d(engineSnap.dirX(), engineSnap.dirY(), engineSnap.dirZ());
                snapshot = new PlayerRaycastSnapshot(uuid, null, engineSnap.worldName(), eyePos, lookDir, false);
            }
        }
        if (snapshot == null) {
            try {
                snapshot = PlayerRaycastSnapshot.from(player);
            } catch (Throwable ignored) {
            }
        }
        if (snapshot == null)
            return;

        // 2. Broad-phase spatial gate check (fast-path via ProximityTracker)
        final List<DisplayPlane> nearbyPlanes = (proximityTracker != null)
                ? proximityTracker.getNearbyBoardsSnapshot(snapshot.playerId())
                : null;
        final List<DisplayPlane> nearby = (nearbyPlanes != null)
                ? nearbyPlanes
                : spatialIndex.nearbyBoards(snapshot.eyePosition(), maxActivationRadius, snapshot.worldName());

        if (nearby == null || nearby.isEmpty()) {
            return;
        }

        final PlayerRaycastSnapshot finalSnapshot = snapshot;

        // 3. Offload raycast to async raycast worker pool directly
        raycastPool.submit(() -> {
            MutableRaycastHit scratch = RAYCAST_SCRATCH.get();
            double bestT = Double.MAX_VALUE;
            UUID bestDisplayId = null;
            int bestPixelX = 0;
            int bestPixelY = 0;

            double eyeX = finalSnapshot.eyePosition().x();
            double eyeY = finalSnapshot.eyePosition().y();
            double eyeZ = finalSnapshot.eyePosition().z();
            double dirX = finalSnapshot.lookDirection().x();
            double dirY = finalSnapshot.lookDirection().y();
            double dirZ = finalSnapshot.lookDirection().z();

            for (DisplayPlane plane : nearby) {
                if (DistanceLodTracker.isViewingBackFace(eyeX, eyeY, eyeZ, plane)) {
                    continue;
                }
                DisplayRaycaster.intersect(plane, eyeX, eyeY, eyeZ, dirX, dirY, dirZ, scratch);
                if (scratch.hit) {
                    if (isInteractEntity && scratch.distance >= 6.0) {
                        continue; // Opportunistic backup only for vanilla reach
                    }
                    if (scratch.distance < bestT) {
                        bestT = scratch.distance;
                        bestDisplayId = scratch.displayId;
                        bestPixelX = (int) Math.round(scratch.pixelX);
                        bestPixelY = (int) Math.round(scratch.pixelY);
                    }
                }
            }

            if (bestDisplayId != null) {
                if (router.hasInteractiveTarget(player, bestDisplayId, bestPixelX, bestPixelY)) {
                    DebugLogger.log("Interact", "Player %s %s hit display %s at pixel (%d, %d), dist=%.2fm",
                            player.getName(), clickType, bestDisplayId, bestPixelX, bestPixelY, bestT);
                    RaycastResult.Hit hit = new RaycastResult.Hit(bestDisplayId, bestPixelX, bestPixelY, bestT);
                    boolean checkRateLimit = (rateLimiter == null);
                    scheduler.runOnEntity(player,
                            ignored -> router.handleClick(player, hit, clickType, checkRateLimit));
                }
            }
        });
    }

    private ProximityTracker proximityTracker;

    public void setProximityTracker(ProximityTracker tracker) {
        this.proximityTracker = tracker;
    }

    public void onPlayerQuit(UUID playerId) {
        if (playerId != null) {
            lastLeftClicks.remove(playerId);
            lastRightClicks.remove(playerId);
        }
    }

    public void shutdown() {
        RAYCAST_SCRATCH.remove();
        if (raycastPool != null && !raycastPool.isShutdown()) {
            raycastPool.shutdown();
            try {
                if (!raycastPool.awaitTermination(1, TimeUnit.SECONDS)) {
                    raycastPool.shutdownNow();
                }
            } catch (InterruptedException e) {
                raycastPool.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    public static Vector3d getEyeOrigin(Player player) {
        Location eye = player.getEyeLocation();
        return new Vector3d(eye.getX(), eye.getY(), eye.getZ());
    }

    public static Vector3d getLookDirection(Player player) {
        Vector dir = player.getEyeLocation().getDirection();
        return new Vector3d(dir.getX(), dir.getY(), dir.getZ()).normalize();
    }

    public ExecutorService getRaycastPool() {
        return raycastPool;
    }
}