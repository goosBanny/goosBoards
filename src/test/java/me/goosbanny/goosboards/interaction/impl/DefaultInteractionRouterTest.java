package me.goosbanny.goosboards.interaction.impl;

import me.goosbanny.goosboards.interaction.InteractionRateLimiter;
import me.goosbanny.goosboards.interaction.InteractionRouter;
import me.goosbanny.goosboards.interaction.economy.ChargeResult;
import me.goosbanny.goosboards.interaction.economy.EconomyGuard;
import me.goosbanny.goosboards.scene.component.text.PixelTextComponent;
import me.goosbanny.goosboards.scene.component.text.TextComponent;

import me.goosbanny.goosboards.api.ClickType;
import me.goosbanny.goosboards.core.scheduler.UniversalScheduler;
import me.goosbanny.goosboards.raycast.RaycastResult;
import me.goosbanny.goosboards.scene.BoardConfig;
import me.goosbanny.goosboards.scene.component.interactive.ButtonComponent;
import me.goosbanny.goosboards.scene.Rect;
import me.goosbanny.goosboards.scene.component.container.ScrollPaneComponent;
import me.goosbanny.goosboards.scene.component.UIComponent;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class DefaultInteractionRouterTest {

    private UniversalScheduler mockScheduler;
    private InteractionRateLimiter rateLimiter;
    private EconomyGuard mockEconomyGuard;
    private Player mockPlayer;
    private UUID playerId;
    private UUID displayId;

    @BeforeEach
    void setUp() {
        mockScheduler = mock(UniversalScheduler.class);
        // Execute runOnEntity immediately for testing
        doAnswer(invocation -> {
            Player p = invocation.getArgument(0);
            Consumer<Player> task = invocation.getArgument(1);
            task.accept(p);
            return null;
        }).when(mockScheduler).runOnEntity(any(Player.class), any());

        rateLimiter = new InteractionRateLimiter(8, 20);
        mockEconomyGuard = mock(EconomyGuard.class);
        mockPlayer = mock(Player.class);
        playerId = UUID.randomUUID();
        displayId = UUID.randomUUID();
        when(mockPlayer.getUniqueId()).thenReturn(playerId);
        when(mockPlayer.getName()).thenReturn("Tester");
        when(mockPlayer.getLocation()).thenReturn(new Location(null, 0, 64, 0));
    }

    @Test
    @DisplayName("Click routes to Button and executes command on entity scheduler")
    void testClickRoutesToButtonAndExecutesCommand() {
        ButtonComponent button = new ButtonComponent("test-btn", false);
        button.setBounds(new Rect(10, 10, 100, 40));

        Map<String, Object> cmdAction = new HashMap<>();
        cmdAction.put("type", "command");
        cmdAction.put("command", "say hello %player_name%");
        cmdAction.put("execute-from-console", false);
        button.addOnClickAction("click-1", cmdAction);

        BoardConfig.SceneDefinition scene = new BoardConfig.SceneDefinition("main", List.of(button));
        DefaultInteractionRouter router = new DefaultInteractionRouter(
                mockScheduler,
                rateLimiter,
                mockEconomyGuard,
                (p, d) -> scene
        );

        RaycastResult.Hit hit = new RaycastResult.Hit(displayId, 50, 30, 2.0);
        router.handleClick(mockPlayer, hit, ClickType.LEFT_CLICK);

        // Click execution occurs directly on entity thread without double-dispatch scheduling
        verify(mockScheduler, never()).runOnEntity(eq(mockPlayer), any());
        verify(mockPlayer, times(1)).performCommand("say hello Tester");
    }

    @Test
    @DisplayName("Action with price charges economy; if denied, command does not execute")
    void testPaidActionDeniedByEconomyGuard() {
        ButtonComponent button = new ButtonComponent("paid-btn", false);
        button.setBounds(new Rect(0, 0, 100, 50));

        Map<String, Object> paidAction = new HashMap<>();
        paidAction.put("type", "command");
        paidAction.put("command", "give %player_name% diamond 1");
        paidAction.put("price", 150.0);
        button.addOnClickAction("buy-diamond", paidAction);

        when(mockEconomyGuard.tryCharge(eq(playerId), eq(150.0), anyString()))
                .thenReturn(new ChargeResult(false, "insufficient funds"));

        BoardConfig.SceneDefinition scene = new BoardConfig.SceneDefinition("shop", List.of(button));
        DefaultInteractionRouter router = new DefaultInteractionRouter(
                mockScheduler,
                rateLimiter,
                mockEconomyGuard,
                (p, d) -> scene
        );

        RaycastResult.Hit hit = new RaycastResult.Hit(displayId, 50, 25, 1.5);
        router.handleClick(mockPlayer, hit, ClickType.LEFT_CLICK);

        verify(mockEconomyGuard, times(1)).tryCharge(eq(playerId), eq(150.0), anyString());
        verify(mockPlayer, never()).performCommand(anyString());
        verify(mockPlayer, times(1)).sendMessage(contains("insufficient funds"));
    }

    @Test
    @DisplayName("Action with price charges economy; if accepted, command executes")
    void testPaidActionAcceptedByEconomyGuard() {
        ButtonComponent button = new ButtonComponent("paid-btn-success", false);
        button.setBounds(new Rect(0, 0, 100, 50));

        Map<String, Object> paidAction = new HashMap<>();
        paidAction.put("type", "command");
        paidAction.put("command", "give %player_name% diamond 1");
        paidAction.put("price", 150.0);
        button.addOnClickAction("buy-diamond", paidAction);

        when(mockEconomyGuard.tryCharge(eq(playerId), eq(150.0), anyString()))
                .thenReturn(new ChargeResult(true, "receipt-uuid-1234"));

        BoardConfig.SceneDefinition scene = new BoardConfig.SceneDefinition("shop", List.of(button));
        DefaultInteractionRouter router = new DefaultInteractionRouter(
                mockScheduler,
                rateLimiter,
                mockEconomyGuard,
                (p, d) -> scene
        );

        RaycastResult.Hit hit = new RaycastResult.Hit(displayId, 50, 25, 1.5);
        router.handleClick(mockPlayer, hit, ClickType.LEFT_CLICK);

        verify(mockEconomyGuard, times(1)).tryCharge(eq(playerId), eq(150.0), anyString());
        verify(mockPlayer, times(1)).performCommand("give Tester diamond 1");
    }

    @Test
    @DisplayName("Rate-limited clicks are rejected before scene lookup")
    void testRateLimitedClicksRejected() {
        InteractionRateLimiter strictLimiter = mock(InteractionRateLimiter.class);
        when(strictLimiter.tryConsumeClick(playerId)).thenReturn(false);

        DefaultInteractionRouter.SceneProvider mockProvider = mock(DefaultInteractionRouter.SceneProvider.class);

        DefaultInteractionRouter router = new DefaultInteractionRouter(
                mockScheduler,
                strictLimiter,
                mockEconomyGuard,
                mockProvider
        );

        RaycastResult.Hit hit = new RaycastResult.Hit(displayId, 50, 30, 2.0);
        router.handleClick(mockPlayer, hit, ClickType.LEFT_CLICK);

        verify(mockProvider, never()).getActiveScene(any(), any());
        verify(mockScheduler, never()).runOnEntity(any(), any());
    }

    @Test
    @DisplayName("Switch scene action triggers scene switch callback")
    void testSwitchSceneAction() {
        ButtonComponent button = new ButtonComponent("nav-btn", false);
        button.setBounds(new Rect(0, 0, 100, 50));

        Map<String, Object> switchAction = new HashMap<>();
        switchAction.put("type", "switch_scene");
        switchAction.put("scene", "shop-menu");
        button.addOnClickAction("switch", switchAction);

        BoardConfig.SceneDefinition scene = new BoardConfig.SceneDefinition("main", List.of(button));
        DefaultInteractionRouter router = new DefaultInteractionRouter(
                mockScheduler,
                rateLimiter,
                mockEconomyGuard,
                (p, d) -> scene
        );

        AtomicReference<String> switchedScene = new AtomicReference<>();
        router.setSceneSwitchHandler((p, target) -> switchedScene.set(target));

        RaycastResult.Hit hit = new RaycastResult.Hit(displayId, 25, 25, 1.0);
        router.handleClick(mockPlayer, hit, ClickType.LEFT_CLICK);

        assertEquals("shop-menu", switchedScene.get());
    }

    @Test
    @DisplayName("Hover triggers on-hover sound action once on enter")
    void testHoverTriggersHoverAction() {
        ButtonComponent button = new ButtonComponent("hover-btn", false);
        button.setBounds(new Rect(0, 0, 100, 50));

        Map<String, Object> hoverSound = new HashMap<>();
        hoverSound.put("type", "play_sound");
        hoverSound.put("sound", "minecraft:ui.button.click");
        hoverSound.put("volume", 0.5);
        hoverSound.put("pitch", 1.5);
        button.addOnHoverAction("sound", hoverSound);

        BoardConfig.SceneDefinition scene = new BoardConfig.SceneDefinition("main", List.of(button));
        DefaultInteractionRouter router = new DefaultInteractionRouter(
                mockScheduler,
                rateLimiter,
                mockEconomyGuard,
                (p, d) -> scene
        );

        RaycastResult.Hit hit = new RaycastResult.Hit(displayId, 25, 25, 1.0);
        router.handleHover(mockPlayer, hit);

        assertTrue(router.isHovered(mockPlayer.getUniqueId(), displayId, button.getId()));
        verify(mockPlayer, times(1)).playSound(any(Location.class), eq("minecraft:ui.button.click"), eq(0.5f), eq(1.5f));

        // Subsequent hover while still hovered should not re-trigger action
        router.handleHover(mockPlayer, hit);
        verify(mockPlayer, times(1)).playSound(any(Location.class), eq("minecraft:ui.button.click"), eq(0.5f), eq(1.5f));
    }

    @Test
    @DisplayName("Multi-player hover state is independent and un-hover allows re-trigger")
    void testMultiPlayerHoverIndependence() {
        ButtonComponent button = new ButtonComponent("shared-btn", false);
        button.setBounds(new Rect(0, 0, 50, 50));

        Map<String, Object> hoverSound = new HashMap<>();
        hoverSound.put("type", "play_sound");
        hoverSound.put("sound", "minecraft:ui.button.click");
        button.addOnHoverAction("sound", hoverSound);

        BoardConfig.SceneDefinition scene = new BoardConfig.SceneDefinition("main", List.of(button));
        DefaultInteractionRouter router = new DefaultInteractionRouter(
                mockScheduler,
                rateLimiter,
                mockEconomyGuard,
                (p, d) -> scene
        );

        Player player2 = mock(Player.class);
        UUID player2Id = UUID.randomUUID();
        when(player2.getUniqueId()).thenReturn(player2Id);
        when(player2.getName()).thenReturn("PlayerTwo");
        when(player2.getLocation()).thenReturn(new Location(null, 0, 64, 0));

        RaycastResult.Hit buttonHit = new RaycastResult.Hit(displayId, 20, 20, 1.0);
        RaycastResult.Hit emptyHit = new RaycastResult.Hit(displayId, 80, 80, 1.0);

        // Player 1 hovers button -> fires action
        router.handleHover(mockPlayer, buttonHit);
        verify(mockPlayer, times(1)).playSound(any(Location.class), eq("minecraft:ui.button.click"), anyFloat(), anyFloat());

        // Player 2 hovers button -> MUST fire action despite Player 1 having hovered it
        router.handleHover(player2, buttonHit);
        verify(player2, times(1)).playSound(any(Location.class), eq("minecraft:ui.button.click"), anyFloat(), anyFloat());

        // Player 1 moves to empty space (un-hovers)
        router.handleHover(mockPlayer, emptyHit);

        // Player 1 moves back onto button -> fires action again!
        router.handleHover(mockPlayer, buttonHit);
        verify(mockPlayer, times(2)).playSound(any(Location.class), eq("minecraft:ui.button.click"), anyFloat(), anyFloat());
    }

    @Test
    @DisplayName("Click inside ScrollPaneComponent respects player scroll offset")
    void testClickInsideScrolledPane() {
        ScrollPaneComponent pane = new ScrollPaneComponent("scroll-pane", false);
        pane.setBounds(new Rect(0, 0, 200, 200));
        pane.setScrollInnerWidth(200);
        pane.setScrollInnerHeight(600);
        pane.setMouseScroll(true);

        ButtonComponent btnScrolled = new ButtonComponent("scrolled-btn", false);
        btnScrolled.setBounds(new Rect(20, 250, 100, 40));
        Map<String, Object> clickSound = new HashMap<>();
        clickSound.put("type", "play_sound");
        clickSound.put("sound", "minecraft:ui.button.click");
        btnScrolled.addOnClickAction("sound", clickSound);
        pane.addChild(btnScrolled);

        BoardConfig.SceneDefinition scene = new BoardConfig.SceneDefinition("main", List.of(pane));
        DefaultInteractionRouter router = new DefaultInteractionRouter(
                mockScheduler,
                rateLimiter,
                mockEconomyGuard,
                (p, d) -> scene
        );

        // Before scroll: click at local (30, 60) hits empty space (btnScrolled is at content Y=250)
        RaycastResult.Hit hit = new RaycastResult.Hit(displayId, 30, 60, 1.0);
        router.handleClick(mockPlayer, hit, ClickType.LEFT_CLICK);
        verify(mockPlayer, never()).playSound(any(Location.class), anyString(), anyFloat(), anyFloat());

        // Player scrolls down by 200px (so btnScrolled at content Y=250 appears at local Y = 50..90)
        pane.scroll(playerId, 200);

        // Now click at local (30, 60) hits btnScrolled (content Y = 60 + 200 = 260)
        router.handleClick(mockPlayer, hit, ClickType.LEFT_CLICK);
        verify(mockPlayer, times(1)).playSound(any(Location.class), eq("minecraft:ui.button.click"), anyFloat(), anyFloat());
    }

    @Test
    @DisplayName("Clicking text component inside button triggers button actions exactly once")
    void testClickOnChildTextTriggersButton() {
        ButtonComponent btn = new ButtonComponent("test-btn", false);
        btn.setBounds(new Rect(10, 10, 100, 40));
        Map<String, Object> soundAction = new HashMap<>();
        soundAction.put("type", "play_sound");
        soundAction.put("sound", "minecraft:ui.button.click");
        btn.addOnClickAction("snd", soundAction);

        PixelTextComponent label =
                new PixelTextComponent("lbl", false);
        label.setBounds(new Rect(20, 20, 80, 20));
        label.setText("CLICK ME");
        btn.addChild(label);

        BoardConfig.SceneDefinition scene = new BoardConfig.SceneDefinition("main", List.of(btn));
        DefaultInteractionRouter router = new DefaultInteractionRouter(
                mockScheduler,
                rateLimiter,
                mockEconomyGuard,
                (p, d) -> scene
        );

        // Click directly on the child text label at (30, 25)
        RaycastResult.Hit hit = new RaycastResult.Hit(displayId, 30, 25, 1.0);
        router.handleClick(mockPlayer, hit, ClickType.LEFT_CLICK);

        // Verifies action executes exactly once (no double-click)
        verify(mockPlayer, times(1)).playSound(any(Location.class), eq("minecraft:ui.button.click"), anyFloat(), anyFloat());
    }

    @Test
    @DisplayName("Clicking sibling text component overlaid on button triggers button underneath")
    void testClickOnOverlaidTextTriggersButton() {
        ButtonComponent btn = new ButtonComponent("underlying-btn", false);
        btn.setBounds(new Rect(50, 50, 120, 50));
        Map<String, Object> soundAction = new HashMap<>();
        soundAction.put("type", "play_sound");
        soundAction.put("sound", "minecraft:entity.experience_orb.pickup");
        btn.addOnClickAction("snd", soundAction);

        TextComponent overlayText =
                new TextComponent("overlay-txt", false);
        overlayText.setBounds(new Rect(50, 50, 120, 50));
        overlayText.setText("Overlay Label");

        // Overlay text rendered after (on top of) button in scene component list
        BoardConfig.SceneDefinition scene = new BoardConfig.SceneDefinition("main", List.of(btn, overlayText));
        DefaultInteractionRouter router = new DefaultInteractionRouter(
                mockScheduler,
                rateLimiter,
                mockEconomyGuard,
                (p, d) -> scene
        );

        // Click directly on the overlay text at (70, 70)
        RaycastResult.Hit hit = new RaycastResult.Hit(displayId, 70, 70, 1.0);
        router.handleClick(mockPlayer, hit, ClickType.LEFT_CLICK);

        // Verifies button underneath was triggered exactly once
        verify(mockPlayer, times(1)).playSound(any(Location.class), eq("minecraft:entity.experience_orb.pickup"), anyFloat(), anyFloat());
    }

    @Test
    @DisplayName("Switch scene action routes boardId accurately to SceneSwitchListener")
    void testSwitchSceneWithBoardAwareListener() {
        ButtonComponent button = new ButtonComponent("nav-btn", false);
        button.setBounds(new Rect(0, 0, 100, 50));

        Map<String, Object> switchAction = new HashMap<>();
        switchAction.put("type", "switch_scene");
        switchAction.put("scene", "gallery");
        button.addOnClickAction("switch", switchAction);

        BoardConfig.SceneDefinition scene = new BoardConfig.SceneDefinition("main", List.of(button));
        DefaultInteractionRouter router = new DefaultInteractionRouter(
                mockScheduler,
                rateLimiter,
                mockEconomyGuard,
                (p, d) -> scene,
                dId -> "test-board"
        );

        AtomicReference<String> receivedBoard = new AtomicReference<>();
        AtomicReference<String> receivedScene = new AtomicReference<>();
        router.setSceneSwitchHandler((InteractionRouter.SceneSwitchListener) (p, bId, target) -> {
            receivedBoard.set(bId);
            receivedScene.set(target);
        });

        RaycastResult.Hit hit = new RaycastResult.Hit(displayId, 25, 25, 1.0);
        router.handleClick(mockPlayer, hit, ClickType.LEFT_CLICK);

        assertEquals("test-board", receivedBoard.get());
        assertEquals("gallery", receivedScene.get());
    }
}