package me.goosbanny.goosboards.render;

import me.goosbanny.goosboards.core.scheduler.UniversalScheduler;
import me.goosbanny.goosboards.interaction.impl.DefaultInteractionRouter;
import me.goosbanny.goosboards.interaction.economy.EconomyGuard;
import me.goosbanny.goosboards.interaction.InteractionRateLimiter;
import me.goosbanny.goosboards.config.ConfigReloadManager;
import me.goosbanny.goosboards.core.metrics.MetricsCollector;
import me.goosbanny.goosboards.protocol.VirtualEntityTracker;
import me.goosbanny.goosboards.display.DisplayPlane;
import me.goosbanny.goosboards.raycast.RaycastResult;
import me.goosbanny.goosboards.raycast.Vector3d;
import me.goosbanny.goosboards.scene.BoardConfig;
import me.goosbanny.goosboards.scene.component.interactive.ButtonComponent;
import me.goosbanny.goosboards.scene.component.visual.ImageComponent;
import me.goosbanny.goosboards.scene.Rect;
import me.goosbanny.goosboards.scene.RenderContext;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BoardReloadSyncTest {

    @Test
    @DisplayName("syncDisplays updates existing DisplayRenderState with new DisplayPlane settings and resized canvas")
    void testSyncDisplaysUpdatesPlaneSettingsAndDimensions() {
        ConfigReloadManager mockReloadManager = mock(ConfigReloadManager.class);
        VirtualEntityTracker mockEntityTracker = mock(VirtualEntityTracker.class);
        MetricsCollector metricsCollector = new MetricsCollector();
        UniversalScheduler mockScheduler = mock(UniversalScheduler.class);

        AtomicInteger mapIdSeq = new AtomicInteger(100);
        MapIdAllocator mapIdAllocator = new MapIdAllocator() {
            @Override
            public int allocate() {
                return mapIdSeq.incrementAndGet();
            }

            @Override
            public boolean free(int id) {
                return true;
            }
        };

        BoardRenderEngine engine = new BoardRenderEngine(
                mockReloadManager,
                mockEntityTracker,
                metricsCollector,
                mockScheduler,
                mapIdAllocator
        );

        UUID displayId = UUID.randomUUID();

        // 1. Initial 2x2 plane without hoverGlow
        DisplayPlane initialPlane = new DisplayPlane(
                displayId,
                "world",
                new Vector3d(0, 64, 0),
                2.0,
                2.0,
                new Vector3d(1, 0, 0),
                new Vector3d(0, -1, 0),
                32.0,
                16.0,
                false,
                false,
                "aqua",
                "border"
        );

        when(mockReloadManager.getAllDisplayPlanes()).thenReturn(List.of(initialPlane));
        when(mockReloadManager.getBoardIdForDisplay(displayId)).thenReturn("test-board");

        engine.syncDisplays();

        int[] initialMapIds = engine.getMapIds(displayId);
        assertNotNull(initialMapIds);
        assertEquals(4, initialMapIds.length, "2x2 board must have 4 map IDs allocated");

        // 2. Updated 3x3 plane with hoverGlow enabled and new glow settings
        DisplayPlane reloadedPlane = new DisplayPlane(
                displayId,
                "world",
                new Vector3d(0, 64, 0),
                3.0,
                3.0,
                new Vector3d(1, 0, 0),
                new Vector3d(0, -1, 0),
                48.0,
                16.0,
                true,
                true,
                "gold",
                "all"
        );

        when(mockReloadManager.getAllDisplayPlanes()).thenReturn(List.of(reloadedPlane));

        engine.syncDisplays();

        int[] reloadedMapIds = engine.getMapIds(displayId);
        assertNotNull(reloadedMapIds);
        assertEquals(9, reloadedMapIds.length, "3x3 board after reload must have 9 map IDs allocated");
    }

    @Test
    @DisplayName("DefaultInteractionRouter tracks hover across buttons and images and clears on reload")
    void testInteractionRouterHoverTrackingAndClear() {
        UniversalScheduler mockScheduler = mock(UniversalScheduler.class);
        InteractionRateLimiter mockRateLimiter = mock(InteractionRateLimiter.class);
        when(mockRateLimiter.tryConsumeHover(any())).thenReturn(true);
        EconomyGuard mockEconomyGuard = mock(EconomyGuard.class);

        UUID displayId = UUID.randomUUID();
        UUID playerId = UUID.randomUUID();

        ButtonComponent button = new ButtonComponent("action-btn", true);
        button.setBounds(new Rect(10, 10, 100, 40));

        ImageComponent image = new ImageComponent("header-logo", false);
        image.setBounds(new Rect(150, 10, 50, 50));

        BoardConfig.SceneDefinition scene = new BoardConfig.SceneDefinition("default", List.of(button, image));

        DefaultInteractionRouter router = new DefaultInteractionRouter(
                mockScheduler,
                mockRateLimiter,
                mockEconomyGuard,
                (player, dId) -> scene
        );

        // Hover over image
        RaycastResult.Hit imageHit = new RaycastResult.Hit(displayId, 160, 20, 5.0);
        Player mockPlayer = mock(Player.class);
        when(mockPlayer.getUniqueId()).thenReturn(playerId);

        router.handleHover(mockPlayer, imageHit);
        assertTrue(router.isHovered(playerId, displayId, "header-logo"), "ImageComponent hover must be tracked");

        // Hover over button
        RaycastResult.Hit buttonHit = new RaycastResult.Hit(displayId, 50, 20, 5.0);
        router.handleHover(mockPlayer, buttonHit);
        assertTrue(router.isHovered(playerId, displayId, "action-btn"), "ButtonComponent hover must be tracked");
        assertFalse(router.isHovered(playerId, displayId, "header-logo"), "Previous hover must be replaced");

        // Clear hover states (as on reload)
        router.clearHoverStates();
        assertNull(router.getHoveredButton(playerId, displayId), "clearHoverStates must purge all tracked hovers");
    }

    @Test
    @DisplayName("ButtonComponent selects hover-image when hovered in RenderContext")
    void testButtonImageHoverSwap() {
        ButtonComponent button = new ButtonComponent("btn-swap", true);
        button.setImageName("normal_icon.png");
        button.setHoverImageName("hover_icon.png");
        button.setBounds(new Rect(0, 0, 100, 40));

        RenderContext normalCtx = new RenderContext(null, Collections.emptyMap(), "other-comp", Collections.emptySet(), false);
        RenderContext hoverCtx = new RenderContext(null, Collections.emptyMap(), "btn-swap", Collections.singleton("btn-swap"), true);

        assertFalse(normalCtx.isHovered(button.getId()));
        assertTrue(hoverCtx.isHovered(button.getId()));

        assertEquals("normal_icon.png", button.getImageName());
        assertEquals("hover_icon.png", button.getHoverImageName());
    }
}