package me.goosbanny.goosboards;

import com.github.retrooper.packetevents.PacketEvents;
import me.goosbanny.goosboards.api.ClickType;
import me.goosbanny.goosboards.config.ConfigManager;
import me.goosbanny.goosboards.core.scheduler.SchedulerFactory;
import me.goosbanny.goosboards.core.scheduler.UniversalScheduler;
import me.goosbanny.goosboards.integration.PlaceholderHook;
import me.goosbanny.goosboards.protocol.packet.ClickPacketListener;
import me.goosbanny.goosboards.interaction.impl.DefaultInteractionRouter;
import me.goosbanny.goosboards.interaction.economy.EconomyGuard;
import me.goosbanny.goosboards.interaction.InteractionRateLimiter;
import me.goosbanny.goosboards.interaction.InteractionRouter;
import me.goosbanny.goosboards.interaction.economy.impl.VaultEconomyGuard;
import me.goosbanny.goosboards.listener.BoardLifecycleListener;
import me.goosbanny.goosboards.media.DiskMediaCache;
import me.goosbanny.goosboards.command.BoardSelectionListener;
import me.goosbanny.goosboards.command.BoardSelectionManager;
import me.goosbanny.goosboards.config.ConfigReloadManager;
import me.goosbanny.goosboards.core.logging.DebugLogger;
import me.goosbanny.goosboards.command.GoosBoardCommand;
import me.goosbanny.goosboards.core.logging.MessageService;
import me.goosbanny.goosboards.core.metrics.MetricsCollector;
import me.goosbanny.goosboards.protocol.QuarantinedIdAllocator;
import me.goosbanny.goosboards.protocol.TextDisplayEntityTracker;
import me.goosbanny.goosboards.protocol.VirtualEntityTracker;
import me.goosbanny.goosboards.raycast.spatial.impl.ChunkBucketSpatialIndex;
import me.goosbanny.goosboards.display.DisplayPlane;
import me.goosbanny.goosboards.raycast.DisplayRaycaster;
import me.goosbanny.goosboards.raycast.spatial.DisplaySpatialIndex;
import me.goosbanny.goosboards.raycast.MutableRaycastHit;
import me.goosbanny.goosboards.display.ProximityTracker;
import me.goosbanny.goosboards.raycast.Vector3d;
import me.goosbanny.goosboards.render.BoardRenderEngine;
import me.goosbanny.goosboards.render.MapIdAllocator;
import me.goosbanny.goosboards.render.buffer.RenderScratchpad;
import me.goosbanny.goosboards.render.cache.RenderStateCache;
import me.goosbanny.goosboards.render.cache.impl.RenderStateCacheImpl;
import me.goosbanny.goosboards.render.palette.impl.PaletteQuantizerImpl;
import me.goosbanny.goosboards.scene.BoardConfig;
import me.goosbanny.goosboards.scene.parser.BoardYamlParser;
import me.goosbanny.goosboards.scene.component.visual.GifComponent;
import me.goosbanny.goosboards.scene.component.visual.Head2DComponent;
import me.goosbanny.goosboards.scene.component.visual.ImageComponent;
import me.goosbanny.goosboards.scene.Rect;
import me.goosbanny.goosboards.scene.component.container.ScrollPaneComponent;
import me.goosbanny.goosboards.scene.component.UIComponent;
import me.goosbanny.goosboards.media.security.SafeMediaLoader;
import me.goosbanny.goosboards.media.security.impl.SafeMediaLoaderImpl;
import me.goosbanny.goosboards.storage.impl.H2StorageBackend;
import me.goosbanny.goosboards.storage.StorageManager;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Location;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import me.goosbanny.goosboards.api.GoosBoardsApi;
import me.goosbanny.goosboards.api.impl.GoosBoardsApiImpl;
import me.goosbanny.goosboards.interaction.PlayerInteractionListener;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;
import java.util.logging.Level;

public final class GoosBoards extends JavaPlugin {
    static {
        System.setProperty("java.awt.headless", "true");
    }

    private static GoosBoards instance;
    private UniversalScheduler scheduler;
    private ConfigManager configManager;
    private MessageService messageService;
    private StorageManager storageManager;
    private MetricsCollector metricsCollector;
    private QuarantinedIdAllocator idAllocator;
    private VirtualEntityTracker entityTracker;
    private TextDisplayEntityTracker textDisplayTracker;
    private ChunkBucketSpatialIndex spatialIndex;
    private ProximityTracker proximityTracker;
    private InteractionRateLimiter rateLimiter;
    private EconomyGuard economyGuard;
    private RenderStateCache renderStateCache;
    private SafeMediaLoader safeMediaLoader;
    private InteractionRouter interactionRouter;
    private ClickPacketListener clickListener;
    private ConfigReloadManager reloadManager;
    private BoardSelectionManager selectionManager;
    private GoosBoardCommand goosBoardCommand;
    private BoardSelectionListener selectionListener;
    private MapIdAllocator mapIdAllocator;
    private BoardRenderEngine renderEngine;
    private BoardLifecycleListener lifecycleListener;

    public static GoosBoards getInstance() {
        return instance;
    }

    @Override
    public void onLoad() {
        instance = this;
        System.setProperty("java.awt.headless", "true");
    }

    @Override
    public void onEnable() {
        if (getServer().getPluginManager().getPlugin("packetevents") == null) {
            getLogger().severe("PacketEvents plugin not found! Disabling GoosBoards.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // 1. Initialize configuration and localization
        this.configManager = new ConfigManager(this);
        this.configManager.init();

        DebugLogger.init(getLogger(), configManager.getConfig().getBoolean("debug.enabled", false));

        this.messageService = new MessageService(this);
        this.messageService.init();

        int maxSkins = configManager.getConfig().getInt("media-cache.max-cached-skins", DiskMediaCache.DEFAULT_MAX_SKINS);
        int maxImages = configManager.getConfig().getInt("media-cache.max-cached-images", DiskMediaCache.DEFAULT_MAX_IMAGES);
        int retentionDays = configManager.getConfig().getInt("media-cache.retention-days", DiskMediaCache.DEFAULT_RETENTION_DAYS);
        DiskMediaCache.init(getDataFolder(), maxSkins, maxImages, retentionDays);

        // 2. Initialize third-party integration guards
        PlaceholderHook.init();

        // 3. Initialize database storage subsystem asynchronously (H2 default, MySQL/MariaDB optional)
        try {
            this.storageManager = StorageManager.fromConfig(
                    getDataFolder(),
                    configManager.getConfig().getConfigurationSection("database"),
                    getLogger()
            );
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Failed to initialize configured storage, falling back to embedded H2: " + e.getMessage(), e);
            try {
                this.storageManager = new StorageManager(new H2StorageBackend(getDataFolder(), "goosboards_"), getLogger());
            } catch (Exception fatal) {
                getLogger().log(Level.SEVERE, "Fatal error initializing H2 storage", fatal);
            }
        }

        // 4. Initialize core infrastructure & metrics
        this.scheduler = SchedulerFactory.create(this);
        this.metricsCollector = new MetricsCollector();
        this.idAllocator = new QuarantinedIdAllocator();
        this.mapIdAllocator = new MapIdAllocator(1000L);
        this.entityTracker = new VirtualEntityTracker(idAllocator);
        this.textDisplayTracker = new TextDisplayEntityTracker(idAllocator);
        this.spatialIndex = new ChunkBucketSpatialIndex();
        this.rateLimiter = new InteractionRateLimiter(8, 20);
        this.renderStateCache = new RenderStateCacheImpl();
        this.safeMediaLoader = new SafeMediaLoaderImpl();

        ImageComponent.setImagesFolder(new File(getDataFolder(), "images"));
        ImageComponent.setSafeMediaLoader(this.safeMediaLoader);
        GifComponent.setImagesFolder(new File(getDataFolder(), "images"));
        GifComponent.setSafeMediaLoader(this.safeMediaLoader);
        int maxGifFps = configManager.getConfig().getInt("gif.max-fps", 20);
        int defaultGifFps = configManager.getConfig().getInt("gif.default-fps", 10);
        GifComponent.setGlobalSettings(maxGifFps, defaultGifFps);
        Head2DComponent.setSafeMediaLoader(this.safeMediaLoader);

        if (this.renderStateCache instanceof RenderStateCacheImpl cacheImpl) {
            this.metricsCollector.setCacheHitRatioSupplier(() -> cacheImpl.getHitRate() * 100.0);
        }

        // 5. Broad-phase proximity tracker (fed by BoardRenderEngine snapshots; eliminates standalone sweep task)
        int proximityInterval = configManager.getConfig().getInt("spatial-index.proximity-scan-interval-ticks", 2);
        this.proximityTracker = new ProximityTracker(spatialIndex, 32.0, scheduler);

        // 6. Initialize reload manager & stage first load with viewer rehydration
        this.reloadManager = new ConfigReloadManager(
                configManager,
                new BoardYamlParser(),
                renderStateCache,
                spatialIndex,
                metricsCollector,
                getLogger()
        );
        this.reloadManager.setVirtualEntityTracker(entityTracker);
        this.reloadManager.setTextDisplayTracker(textDisplayTracker);
        this.reloadManager.setProximityTracker(proximityTracker);
        this.reloadManager.setMessageService(messageService);

        this.renderEngine = new BoardRenderEngine(
                reloadManager,
                entityTracker,
                metricsCollector,
                scheduler,
                mapIdAllocator
        );
        this.renderEngine.setProximityTracker(proximityTracker, proximityInterval);
        this.reloadManager.setRenderEngine(renderEngine);
        this.reloadManager.reload();
        this.renderEngine.start();

        Consumer<String> redrawCallback = asset -> {
            if (this.renderEngine != null) {
                this.renderEngine.requestComponentRedraw(asset);
            }
        };
        ImageComponent.setAssetLoadedCallback(redrawCallback);
        Head2DComponent.setAssetLoadedCallback(redrawCallback);

        // 7. Resolve optional Vault economy
        Economy economy = null;
        if (getServer().getPluginManager().getPlugin("Vault") != null) {
            RegisteredServiceProvider<Economy> rsp =
                    getServer().getServicesManager().getRegistration(Economy.class);
            if (rsp != null) {
                economy = rsp.getProvider();
            }
        }
        this.economyGuard = new VaultEconomyGuard(economy);

        // 8. Interaction router and click listener with Folia snapshot discipline
        this.reloadManager.setStorageManager(this.storageManager);
        this.interactionRouter = new DefaultInteractionRouter(
                scheduler,
                rateLimiter,
                economyGuard,
                (player, displayId) -> {
                    String boardId = reloadManager.getBoardIdForDisplay(displayId);
                    if (boardId == null) return null;
                    return reloadManager.getActiveScene(boardId, player != null ? player.getUniqueId() : null);
                },
                reloadManager::getBoardIdForDisplay
        );
        this.interactionRouter.setSceneSwitchHandler((player, boardId, targetScene) -> {
            if (boardId != null) {
                boolean ok = reloadManager.setActiveScene(boardId, player != null ? player.getUniqueId() : null, targetScene);
                if (ok) {
                    DebugLogger.log("Interact",
                            "Switched scene on board '%s' for %s to '%s'",
                            boardId, player != null ? player.getName() : "all", targetScene);
                    return;
                }
            }
            for (Map.Entry<String, BoardConfig> entry : reloadManager.getActiveBoards().entrySet()) {
                if (entry.getValue().scenes().containsKey(targetScene)) {
                    reloadManager.setActiveScene(entry.getKey(), player != null ? player.getUniqueId() : null, targetScene);
                    DebugLogger.log("Interact",
                            "Switched scene on fallback board '%s' for %s to '%s'",
                            entry.getKey(), player != null ? player.getName() : "all", targetScene);
                    break;
                }
            }
        });
        this.renderEngine.setInteractionRouter(this.interactionRouter);
        this.reloadManager.setInteractionRouter(this.interactionRouter);
        int raycastThreads = configManager.getConfig().getInt("threading.raycast-pool-size", 1);
        ExecutorService raycastPool = ClickPacketListener.createRaycastPool(raycastThreads);
        this.clickListener = new ClickPacketListener(scheduler, spatialIndex, interactionRouter, rateLimiter, raycastPool, 32.0);
        this.clickListener.setProximityTracker(proximityTracker);
        this.clickListener.setRenderEngine(renderEngine);

        // 9. Register PacketEvents packet listener
        PacketEvents.getAPI().getEventManager().registerListener(clickListener);

        // 10. Register player interaction listener
        PlayerInteractionListener interactionListener = new PlayerInteractionListener(
                proximityTracker,
                spatialIndex,
                reloadManager,
                clickListener
        );
        getServer().getPluginManager().registerEvents(interactionListener, this);

        // 10b. Register public API service in Bukkit ServicesManager
        getServer().getServicesManager().register(
                GoosBoardsApi.class,
                new GoosBoardsApiImpl(reloadManager),
                this,
                ServicePriority.Normal
        );

        // 11. Register /goosboard (/gb) command
        this.selectionManager = new BoardSelectionManager();
        this.goosBoardCommand = new GoosBoardCommand(
                reloadManager,
                metricsCollector,
                messageService,
                selectionManager,
                spatialIndex,
                configManager.getBoardsFolder()
        );
        PluginCommand cmd = getCommand("goosboard");
        if (cmd != null) {
            cmd.setExecutor(goosBoardCommand);
            cmd.setTabCompleter(goosBoardCommand);
        }

        // 12. Register interactive board selection listener (dynamically registered only when sessions are active)
        this.renderEngine.setSelectionManager(this.selectionManager);
        this.selectionListener = new BoardSelectionListener(
                selectionManager, messageService, "gb"
        );
        this.selectionManager.setActiveStateListener(active -> {
            if (active) {
                this.selectionListener.register(this);
            } else {
                this.selectionListener.unregister();
            }
        });

        // 13. Register lifecycle and visibility listener
        this.lifecycleListener = new BoardLifecycleListener(
                scheduler,
                entityTracker,
                textDisplayTracker,
                renderEngine,
                proximityTracker,
                clickListener,
                rateLimiter,
                interactionRouter,
                selectionManager,
                reloadManager
        );
        getServer().getPluginManager().registerEvents(lifecycleListener, this);

        // 14. Periodic cleanup of expired idempotency transactions (every 30 minutes)
        if (this.storageManager != null && this.scheduler != null) {
            this.scheduler.scheduleAsyncRepeating(
                    () -> this.storageManager.purgeExpiredTransactionsAsync(System.currentTimeMillis()),
                    1200L,
                    36000L
            );
        }

        getLogger().info("GoosBoards enabled successfully.");
    }

    @Override
    public void onDisable() {
        if (PacketEvents.getAPI() == null) {
            getLogger().severe("PacketEvents is unavailable during shutdown — ghost entities may remain on clients. A full server restart is recommended to clean up.");
        }
        if (renderEngine != null) {
            renderEngine.stop();
        }
        if (clickListener != null) {
            try {
                if (PacketEvents.getAPI() != null && PacketEvents.getAPI().getEventManager() != null) {
                    PacketEvents.getAPI().getEventManager().unregisterListener(clickListener);
                }
            } catch (Throwable ignored) {
            }
            clickListener.shutdown();
        }
        if (selectionListener != null) {
            selectionListener.unregister();
        }
        RenderScratchpad.clearCurrentThread();
        DisplayRaycaster.clearThreadLocal();
        PaletteQuantizerImpl.clearThreadLocal();
        if (entityTracker != null) {
            entityTracker.despawnAll();
        }
        if (textDisplayTracker != null) {
            textDisplayTracker.despawnAll();
        }
        if (proximityTracker != null) {
            proximityTracker.clear();
        }
        if (storageManager != null) {
            storageManager.close();
        }
        instance = null;
        getLogger().info("GoosBoards disabled successfully.");
    }

    public BoardRenderEngine getRenderEngine() {
        return renderEngine;
    }

    public UniversalScheduler getUniversalScheduler() {
        return scheduler;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public MessageService getMessageService() {
        return messageService;
    }

    public StorageManager getStorageManager() {
        return storageManager;
    }

    public MetricsCollector getMetricsCollector() {
        return metricsCollector;
    }

    public ProximityTracker getProximityTracker() {
        return proximityTracker;
    }

    public ConfigReloadManager getReloadManager() {
        return reloadManager;
    }

    public GoosBoardCommand getGoosBoardCommand() {
        return goosBoardCommand;
    }

    public QuarantinedIdAllocator getIdAllocator() {
        return idAllocator;
    }

    public VirtualEntityTracker getEntityTracker() {
        return entityTracker;
    }

    public TextDisplayEntityTracker getTextDisplayTracker() {
        return textDisplayTracker;
    }

    public DisplaySpatialIndex getSpatialIndex() {
        return spatialIndex;
    }

    public InteractionRateLimiter getRateLimiter() {
        return rateLimiter;
    }

    public EconomyGuard getEconomyGuard() {
        return economyGuard;
    }

    public RenderStateCache getRenderStateCache() {
        return renderStateCache;
    }

    public SafeMediaLoader getSafeMediaLoader() {
        return safeMediaLoader;
    }

    public InteractionRouter getInteractionRouter() {
        return interactionRouter;
    }

    public void setInteractionRouter(InteractionRouter router) {
        if (router != null) {
            this.interactionRouter = router;
        }
    }

    public ClickPacketListener getClickListener() {
        return clickListener;
    }
}