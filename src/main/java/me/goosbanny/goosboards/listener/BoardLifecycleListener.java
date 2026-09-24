package me.goosbanny.goosboards.listener;

import me.goosbanny.goosboards.core.scheduler.UniversalScheduler;
import me.goosbanny.goosboards.protocol.packet.ClickPacketListener;
import me.goosbanny.goosboards.interaction.InteractionRouter;
import me.goosbanny.goosboards.interaction.InteractionRateLimiter;
import me.goosbanny.goosboards.command.BoardSelectionManager;
import me.goosbanny.goosboards.config.ConfigReloadManager;
import me.goosbanny.goosboards.protocol.TextDisplayEntityTracker;
import me.goosbanny.goosboards.protocol.VirtualEntityTracker;
import me.goosbanny.goosboards.display.ProximityTracker;
import me.goosbanny.goosboards.render.BoardRenderEngine;
import me.goosbanny.goosboards.scene.BoardConfig;
import me.goosbanny.goosboards.scene.BoardConfig.SceneDefinition;
import me.goosbanny.goosboards.scene.component.container.ScrollPaneComponent;
import me.goosbanny.goosboards.scene.component.UIComponent;
import me.goosbanny.goosboards.integration.placeholder.PlaceholderService;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.List;
import java.util.UUID;

/**
 * Robust lifecycle and visibility desync prevention listener.
 * Guarantees that destroy packets are sent to the client BEFORE tracking state eviction,
 * and schedules deferred sweeps by 2 ticks on join/teleport/respawn to allow local chunk loading.
 */
public class BoardLifecycleListener implements Listener {

    private final UniversalScheduler scheduler;
    private final VirtualEntityTracker entityTracker;
    private final TextDisplayEntityTracker textDisplayTracker;
    private final BoardRenderEngine renderEngine;
    private final ProximityTracker proximityTracker;
    private final ClickPacketListener clickListener;
    private final InteractionRateLimiter rateLimiter;
    private final InteractionRouter interactionRouter;
    private final BoardSelectionManager selectionManager;
    private final ConfigReloadManager reloadManager;

    public BoardLifecycleListener(
            UniversalScheduler scheduler,
            VirtualEntityTracker entityTracker,
            TextDisplayEntityTracker textDisplayTracker,
            BoardRenderEngine renderEngine,
            ProximityTracker proximityTracker,
            ClickPacketListener clickListener,
            InteractionRateLimiter rateLimiter,
            InteractionRouter interactionRouter,
            BoardSelectionManager selectionManager,
            ConfigReloadManager reloadManager
    ) {
        this.scheduler = scheduler;
        this.entityTracker = entityTracker;
        this.textDisplayTracker = textDisplayTracker;
        this.renderEngine = renderEngine;
        this.proximityTracker = proximityTracker;
        this.clickListener = clickListener;
        this.rateLimiter = rateLimiter;
        this.interactionRouter = interactionRouter;
        this.selectionManager = selectionManager;
        this.reloadManager = reloadManager;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        if (event == null || event.getPlayer() == null) return;
        Player player = event.getPlayer();
        UUID id = player.getUniqueId();

        // 1. Dispatch entity destroy packets to Netty channel FIRST before evicting tracking data
        if (entityTracker != null) {
            entityTracker.despawnAll(player);
        }
        if (textDisplayTracker != null) {
            textDisplayTracker.despawnAll(player);
        }

        // 2. Clear engine and session tracking
        if (renderEngine != null) {
            renderEngine.onPlayerQuit(id);
        }
        if (proximityTracker != null) {
            proximityTracker.onPlayerQuit(id);
        }
        if (clickListener != null) {
            clickListener.onPlayerQuit(id);
        }
        if (rateLimiter != null) {
            rateLimiter.reset(id);
        }
        if (interactionRouter != null) {
            interactionRouter.onPlayerQuit(id);
        }
        if (selectionManager != null) {
            selectionManager.cancelSession(id);
        }
        PlaceholderService.clearViewer(id);
        if (reloadManager != null) {
            reloadManager.resetPlayerAllNonPersistentScenes(id);
            for (BoardConfig config : reloadManager.getActiveBoards().values()) {
                if (config.scenes() != null) {
                    for (SceneDefinition scene : config.scenes().values()) {
                        clearViewerScrolls(scene.components(), id);
                    }
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (event == null || event.getPlayer() == null) return;
        Player player = event.getPlayer();

        if (reloadManager != null) {
            reloadManager.resetPlayerAllNonPersistentScenes(player.getUniqueId());
            reloadManager.loadPlayerPersistentScenesAsync(player.getUniqueId());
        }

        // Despawn any stale rig
        if (entityTracker != null) {
            entityTracker.despawnAll(player);
        }

        // Schedule entity sweep delayed by 2 ticks (2L) to let client chunks finish loading
        if (scheduler != null) {
            scheduler.runOnEntityLater(player, this::triggerSweep, 2L);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        if (event == null || event.getPlayer() == null) return;
        Player player = event.getPlayer();
        UUID id = player.getUniqueId();

        if (event.getFrom().getWorld() != null && event.getTo() != null && !event.getFrom().getWorld().equals(event.getTo().getWorld())) {
            if (reloadManager != null) {
                reloadManager.resetPlayerAllNonPersistentScenes(id);
            }
        }

        if (renderEngine != null) {
            renderEngine.onPlayerQuit(id);
        }
        if (proximityTracker != null) {
            proximityTracker.onPlayerQuit(id);
        }
        if (clickListener != null) {
            clickListener.onPlayerQuit(id);
        }
        if (rateLimiter != null) {
            rateLimiter.reset(id);
        }
        if (interactionRouter != null) {
            interactionRouter.onPlayerQuit(id);
        }
        if (entityTracker != null) {
            entityTracker.despawnAll(player);
        }
        if (textDisplayTracker != null) {
            textDisplayTracker.despawnAll(player);
        }

        if (scheduler != null) {
            scheduler.runOnEntityLater(player, this::triggerSweep, 2L);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
        if (event == null || event.getPlayer() == null) return;
        Player player = event.getPlayer();
        UUID id = player.getUniqueId();

        if (reloadManager != null) {
            reloadManager.resetPlayerAllNonPersistentScenes(id);
        }

        if (renderEngine != null) {
            renderEngine.onPlayerQuit(id);
        }
        if (proximityTracker != null) {
            proximityTracker.onPlayerQuit(id);
        }
        if (clickListener != null) {
            clickListener.onPlayerQuit(id);
        }
        if (rateLimiter != null) {
            rateLimiter.reset(id);
        }
        if (interactionRouter != null) {
            interactionRouter.onPlayerQuit(id);
        }
        if (entityTracker != null) {
            entityTracker.despawnAll(player);
        }
        if (textDisplayTracker != null) {
            textDisplayTracker.despawnAll(player);
        }

        // Schedule delayed sweep on player's new world/region thread
        if (scheduler != null) {
            scheduler.runOnEntityLater(player, this::triggerSweep, 2L);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        if (event == null || event.getPlayer() == null) return;
        Player player = event.getPlayer();
        UUID id = player.getUniqueId();

        if (renderEngine != null) {
            renderEngine.onPlayerQuit(id);
            renderEngine.markRespawned(id);
        }
        if (proximityTracker != null) {
            proximityTracker.onPlayerQuit(id);
        }
        if (clickListener != null) {
            clickListener.onPlayerQuit(id);
        }
        if (rateLimiter != null) {
            rateLimiter.reset(id);
        }
        if (interactionRouter != null) {
            interactionRouter.onPlayerQuit(id);
        }
        if (entityTracker != null) {
            entityTracker.despawnAll(player);
        }
        if (textDisplayTracker != null) {
            textDisplayTracker.despawnAll(player);
        }

        // Schedule delayed sweep by 2 ticks
        if (scheduler != null) {
            scheduler.runOnEntityLater(player, this::triggerSweep, 2L);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerGameModeChange(PlayerGameModeChangeEvent event) {
        if (event == null || event.getPlayer() == null) return;
        Player player = event.getPlayer();
        UUID id = player.getUniqueId();
        if (event.getNewGameMode() == GameMode.SPECTATOR) {
            if (renderEngine != null) {
                renderEngine.onPlayerQuit(id);
            }
            if (entityTracker != null) {
                entityTracker.despawnAll(player);
            }
            if (textDisplayTracker != null) {
                textDisplayTracker.despawnAll(player);
            }
        } else if (player.getGameMode() == GameMode.SPECTATOR) {
            if (proximityTracker != null) {
                proximityTracker.updatePlayerProximity(player);
            }
        }
    }

    private void clearViewerScrolls(List<UIComponent> components, UUID viewerId) {
        if (components == null || viewerId == null) return;
        for (UIComponent comp : components) {
            if (comp instanceof ScrollPaneComponent pane) {
                pane.clearViewerScroll(viewerId);
            }
            clearViewerScrolls(comp.getChildren(), viewerId);
        }
    }

    private void triggerSweep(Player player) {
        if (player == null || !player.isOnline()) return;
        if (proximityTracker != null) {
            proximityTracker.updatePlayerProximity(player);
        }
    }
}