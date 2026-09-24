package me.goosbanny.goosboards.api.event;

import me.goosbanny.goosboards.api.ClickType;

import me.goosbanny.goosboards.core.scheduler.UniversalScheduler;
import me.goosbanny.goosboards.interaction.impl.DefaultInteractionRouter;
import me.goosbanny.goosboards.interaction.economy.EconomyGuard;
import me.goosbanny.goosboards.interaction.InteractionRateLimiter;
import me.goosbanny.goosboards.raycast.RaycastResult;
import me.goosbanny.goosboards.scene.BoardConfig;
import me.goosbanny.goosboards.scene.component.interactive.ButtonComponent;
import me.goosbanny.goosboards.scene.Rect;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class BoardSceneChangeEventTest {

    private Server mockServer;
    private PluginManager mockPluginManager;
    private Player mockPlayer;
    private UniversalScheduler mockScheduler;
    private EconomyGuard mockEconomyGuard;
    private InteractionRateLimiter rateLimiter;

    private static void setBukkitServer(Server server) {
        try {
            Field field = Bukkit.class.getDeclaredField("server");
            field.setAccessible(true);
            field.set(null, server);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @BeforeEach
    void setUp() {
        mockServer = mock(Server.class);
        mockPluginManager = mock(PluginManager.class);
        when(mockServer.getPluginManager()).thenReturn(mockPluginManager);
        when(mockServer.getLogger()).thenReturn(Logger.getAnonymousLogger());
        setBukkitServer(mockServer);

        mockPlayer = mock(Player.class);
        when(mockPlayer.getUniqueId()).thenReturn(UUID.randomUUID());
        when(mockPlayer.getName()).thenReturn("TestPlayer");

        mockScheduler = mock(UniversalScheduler.class);
        doAnswer(invocation -> {
            Player p = invocation.getArgument(0);
            Consumer<Player> task = invocation.getArgument(1);
            task.accept(p);
            return null;
        }).when(mockScheduler).runOnEntity(any(Player.class), any());

        mockEconomyGuard = mock(EconomyGuard.class);
        rateLimiter = new InteractionRateLimiter(10, 20);
    }

    @AfterEach
    void tearDown() {
        setBukkitServer(null);
    }

    @Test
    @DisplayName("BoardSceneChangeEvent exposes player, button, source scene, and target scene")
    void testBoardSceneChangeEventFields() {
        BoardSceneChangeEvent event = new BoardSceneChangeEvent(
                mockPlayer,
                "btn-next",
                "scene-1",
                "scene-2"
        );

        assertEquals(mockPlayer, event.getPlayer());
        assertEquals("btn-next", event.getButtonId());
        assertEquals("scene-1", event.getFromScene());
        assertEquals("scene-2", event.getToScene());
        assertFalse(event.isCancelled());

        event.setCancelled(true);
        assertTrue(event.isCancelled());
    }

    @Test
    @DisplayName("Cancelling BoardSceneChangeEvent blocks scene transition handler")
    void testCancellationBlocksTransition() {
        AtomicBoolean sceneSwitched = new AtomicBoolean(false);

        ButtonComponent button = new ButtonComponent("nav-btn", false);
        button.setBounds(new Rect(0, 0, 50, 50));
        button.addOnClickAction("nav", Map.of(
                "type", "switch_scene",
                "scene", "target-scene"
        ));

        BoardConfig.SceneDefinition scene = new BoardConfig.SceneDefinition("current-scene", List.of(button));
        UUID displayId = UUID.randomUUID();

        // Listener cancels the scene change
        doAnswer(invocation -> {
            Object eventObj = invocation.getArgument(0);
            if (eventObj instanceof BoardSceneChangeEvent bsce) {
                bsce.setCancelled(true);
            }
            return null;
        }).when(mockPluginManager).callEvent(any());

        DefaultInteractionRouter router = new DefaultInteractionRouter(
                mockScheduler,
                rateLimiter,
                mockEconomyGuard,
                (p, d) -> scene
        );
        router.setSceneSwitchHandler((p, target) -> sceneSwitched.set(true));

        RaycastResult.Hit hit = new RaycastResult.Hit(displayId, 10, 10, 2.0);
        router.handleClick(mockPlayer, hit, ClickType.LEFT_CLICK);

        verify(mockPluginManager).callEvent(any(BoardSceneChangeEvent.class));
        assertFalse(sceneSwitched.get(), "Scene switch handler should not be called when BoardSceneChangeEvent is cancelled");
    }

    @Test
    @DisplayName("Uncancelled BoardSceneChangeEvent allows scene transition handler")
    void testUncancelledAllowsTransition() {
        AtomicBoolean sceneSwitched = new AtomicBoolean(false);

        ButtonComponent button = new ButtonComponent("nav-btn", false);
        button.setBounds(new Rect(0, 0, 50, 50));
        button.addOnClickAction("nav", Map.of(
                "type", "switch_scene",
                "scene", "target-scene"
        ));

        BoardConfig.SceneDefinition scene = new BoardConfig.SceneDefinition("current-scene", List.of(button));
        UUID displayId = UUID.randomUUID();

        DefaultInteractionRouter router = new DefaultInteractionRouter(
                mockScheduler,
                rateLimiter,
                mockEconomyGuard,
                (p, d) -> scene
        );
        router.setSceneSwitchHandler((p, target) -> {
            if ("target-scene".equals(target)) {
                sceneSwitched.set(true);
            }
        });

        RaycastResult.Hit hit = new RaycastResult.Hit(displayId, 10, 10, 2.0);
        router.handleClick(mockPlayer, hit, ClickType.LEFT_CLICK);

        verify(mockPluginManager).callEvent(any(BoardSceneChangeEvent.class));
        assertTrue(sceneSwitched.get(), "Scene switch handler should execute when event is uncancelled");
    }
}