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

class BoardClickEventTest {

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
        UUID playerId = UUID.randomUUID();
        when(mockPlayer.getUniqueId()).thenReturn(playerId);
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
    @DisplayName("BoardClickEvent exposes accurate player, board, coordinates, and click type")
    void testBoardClickEventFields() {
        BoardClickEvent event = new BoardClickEvent(
                mockPlayer,
                "main-scene",
                "spawn-display",
                120,
                85,
                ClickType.LEFT_CLICK
        );

        assertEquals(mockPlayer, event.getPlayer());
        assertEquals("main-scene", event.getSceneId());
        assertEquals("spawn-display", event.getDisplayId());
        assertEquals(120, event.getPixelX());
        assertEquals(85, event.getPixelY());
        assertEquals(ClickType.LEFT_CLICK, event.getClickType());
        assertFalse(event.isCancelled());

        event.setCancelled(true);
        assertTrue(event.isCancelled());
    }

    @Test
    @DisplayName("Cancelling BoardClickEvent aborts button action execution")
    void testEventCancellationAbortsAction() {
        AtomicBoolean actionExecuted = new AtomicBoolean(false);

        ButtonComponent button = new ButtonComponent("btn-test", false);
        button.setBounds(new Rect(10, 10, 50, 50));
        button.addOnClickAction("test-cmd", Map.of(
                "type", "command",
                "command", "say test"
        ));

        doAnswer(invocation -> {
            actionExecuted.set(true);
            return true;
        }).when(mockPlayer).performCommand(anyString());

        BoardConfig.SceneDefinition scene = new BoardConfig.SceneDefinition("test-scene", List.of(button));
        UUID displayId = UUID.randomUUID();

        // Register event listener that cancels the click
        doAnswer(invocation -> {
            Object eventObj = invocation.getArgument(0);
            if (eventObj instanceof BoardClickEvent bce) {
                bce.setCancelled(true);
            }
            return null;
        }).when(mockPluginManager).callEvent(any());

        DefaultInteractionRouter router = new DefaultInteractionRouter(
                mockScheduler,
                rateLimiter,
                mockEconomyGuard,
                (p, d) -> scene
        );

        RaycastResult.Hit hit = new RaycastResult.Hit(displayId, 25, 25, 2.0);
        router.handleClick(mockPlayer, hit, ClickType.LEFT_CLICK);

        // Verify that event was called
        verify(mockPluginManager).callEvent(any(BoardClickEvent.class));
        // Action must not have executed because event was cancelled
        assertFalse(actionExecuted.get(), "Button action should not execute when BoardClickEvent is cancelled");
    }

    @Test
    @DisplayName("Non-cancelled BoardClickEvent allows button action execution")
    void testNonCancelledEventExecutesAction() {
        AtomicBoolean actionExecuted = new AtomicBoolean(false);

        ButtonComponent button = new ButtonComponent("btn-test", false);
        button.setBounds(new Rect(10, 10, 50, 50));
        button.addOnClickAction("test-cmd", Map.of(
                "type", "command",
                "command", "say allowed"
        ));

        doAnswer(invocation -> {
            actionExecuted.set(true);
            return true;
        }).when(mockPlayer).performCommand("say allowed");

        BoardConfig.SceneDefinition scene = new BoardConfig.SceneDefinition("test-scene", List.of(button));
        UUID displayId = UUID.randomUUID();

        DefaultInteractionRouter router = new DefaultInteractionRouter(
                mockScheduler,
                rateLimiter,
                mockEconomyGuard,
                (p, d) -> scene
        );

        RaycastResult.Hit hit = new RaycastResult.Hit(displayId, 25, 25, 2.0);
        router.handleClick(mockPlayer, hit, ClickType.LEFT_CLICK);

        verify(mockPluginManager).callEvent(any(BoardClickEvent.class));
        assertTrue(actionExecuted.get(), "Button action should execute when BoardClickEvent is not cancelled");
    }
}