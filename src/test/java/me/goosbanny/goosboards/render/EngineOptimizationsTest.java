package me.goosbanny.goosboards.render;

import me.goosbanny.goosboards.core.scheduler.UniversalScheduler;
import me.goosbanny.goosboards.protocol.packet.BatchedMapSender;
import me.goosbanny.goosboards.render.buffer.CanvasBufferImpl;
import me.goosbanny.goosboards.render.diff.TileDiffer;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.PacketEventsAPI;
import com.github.retrooper.packetevents.manager.player.PlayerManager;
import com.github.retrooper.packetevents.manager.server.ServerManager;
import com.github.retrooper.packetevents.manager.server.ServerVersion;
import com.github.retrooper.packetevents.settings.PacketEventsSettings;
import io.github.retrooper.packetevents.impl.netty.NettyManagerImpl;
import io.netty.channel.Channel;
import me.goosbanny.goosboards.interaction.impl.DefaultInteractionRouter;
import me.goosbanny.goosboards.interaction.economy.EconomyGuard;
import me.goosbanny.goosboards.interaction.InteractionRateLimiter;
import me.goosbanny.goosboards.protocol.QuarantinedIdAllocator;
import me.goosbanny.goosboards.protocol.VirtualEntityRig;
import me.goosbanny.goosboards.display.DisplayPlane;
import me.goosbanny.goosboards.raycast.Vector3d;
import me.goosbanny.goosboards.render.diff.TileDiffer.DirtyTile;
import me.goosbanny.goosboards.scene.BoardConfig;
import me.goosbanny.goosboards.scene.component.interactive.ButtonComponent;
import me.goosbanny.goosboards.scene.component.visual.ImageComponent;
import me.goosbanny.goosboards.scene.component.text.PixelTextComponent;
import me.goosbanny.goosboards.scene.Rect;
import me.goosbanny.goosboards.scene.RenderContext;
import me.goosbanny.goosboards.scene.component.text.TextComponent;
import me.goosbanny.goosboards.scene.component.UIComponent;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EngineOptimizationsTest {

    @BeforeAll
    static void setupPacketEvents() {
        if (PacketEvents.getAPI() == null || PacketEvents.getAPI().getNettyManager() == null) {
            PacketEventsAPI<?> api = mock(PacketEventsAPI.class);
            ServerManager serverManager = mock(ServerManager.class);
            PlayerManager playerManager = mock(PlayerManager.class);
            NettyManagerImpl nettyManager = new NettyManagerImpl();
            when(api.getSettings()).thenReturn(new PacketEventsSettings());
            when(api.getServerManager()).thenReturn(serverManager);
            when(api.getPlayerManager()).thenReturn(playerManager);
            when(api.getNettyManager()).thenReturn(nettyManager);
            when(serverManager.getVersion()).thenReturn(ServerVersion.V_1_20_4);
            PacketEvents.setAPI(api);
        }
    }

    @Test
    @DisplayName("Localized capture refresh: markRegionDirty marks ONLY intersecting sub-tiles dirty")
    void testLocalizedCaptureRefresh() {
        // 4x4 tiles display (512x512 pixels, 16 tiles total)
        CanvasBufferImpl canvas = new CanvasBufferImpl(4, 4);
        assertEquals(16, canvas.getTotalTiles());

        // Mark all clean first
        for (int ty = 0; ty < 4; ty++) {
            for (int tx = 0; tx < 4; tx++) {
                canvas.markClean(tx, ty, 1000L);
            }
        }
        assertFalse(canvas.isAnyTileDirty(), "Canvas should start fully clean");

        // Invalidate region (10, 10, 50, 50) located entirely inside tile (0, 0)
        canvas.markRegionDirty(10, 10, 50, 50);

        assertTrue(canvas.isAnyTileDirty(), "Canvas must now have dirty tiles");
        assertTrue(canvas.isTileDirtyBit(0), "Tile (0, 0) dirty bit must be set");
        assertTrue(canvas.isTileDirty(0, 0, 1000L), "Tile (0, 0) content hash must be invalidated");

        // All other 15 tiles must remain clean
        for (int i = 1; i < 16; i++) {
            assertFalse(canvas.isTileDirtyBit(i), "Tile index " + i + " must remain clean");
        }

        // Clean tile (0, 0)
        canvas.markClean(0, 0, 2000L);
        assertFalse(canvas.isAnyTileDirty());

        // Invalidate a 30x30 region spanning tile boundary: x=120 to 150, y=120 to 150
        // Intersects tile (0,0), (1,0), (0,1), and (1,1)
        canvas.markRegionDirty(120, 120, 30, 30);

        assertTrue(canvas.isTileDirtyBit(0 * 4 + 0), "Tile (0,0) must be dirty");
        assertTrue(canvas.isTileDirtyBit(0 * 4 + 1), "Tile (1,0) must be dirty");
        assertTrue(canvas.isTileDirtyBit(1 * 4 + 0), "Tile (0,1) must be dirty");
        assertTrue(canvas.isTileDirtyBit(1 * 4 + 1), "Tile (1,1) must be dirty");

        // Tiles outside this 2x2 cluster must remain untouched
        assertFalse(canvas.isTileDirtyBit(0 * 4 + 2), "Tile (2,0) must remain clean");
        assertFalse(canvas.isTileDirtyBit(2 * 4 + 2), "Tile (2,2) must remain clean");
        assertFalse(canvas.isTileDirtyBit(3 * 4 + 3), "Tile (3,3) must remain clean");
    }

    @Test
    @DisplayName("Netty burst capping: sendDirtyTiles respects maxTilesPerPlayerPerTick limit")
    void testNettyBurstCapping() {
        BatchedMapSender.setMaxTilesPerPlayerPerTick(4);
        assertEquals(4, BatchedMapSender.getMaxTilesPerPlayerPerTick());

        Channel channel = mock(Channel.class);
        when(channel.isActive()).thenReturn(true);
        when(channel.isWritable()).thenReturn(true);

        List<DirtyTile> tiles = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            tiles.add(new DirtyTile(i % 4, i / 4, i, 100L + i, new byte[128 * 128]));
        }
        int[] mapIds = new int[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10};

        // Normal send: should only send 4 tiles due to burst cap
        boolean sent = BatchedMapSender.sendDirtyTiles(channel, mapIds, tiles, false);
        assertTrue(sent);
        verify(channel, times(4)).write(any());
        verify(channel, times(1)).flush();

        // Forced send: should bypass burst cap and send all 10 tiles
        reset(channel);
        when(channel.isActive()).thenReturn(true);
        when(channel.isWritable()).thenReturn(true);

        boolean forceSent = BatchedMapSender.sendDirtyTiles(channel, mapIds, tiles, true);
        assertTrue(forceSent);
        verify(channel, times(10)).write(any());
        verify(channel, times(1)).flush();

        // Restore default
        BatchedMapSender.setMaxTilesPerPlayerPerTick(BatchedMapSender.DEFAULT_MAX_TILES_PER_TICK);
    }

    @Test
    @DisplayName("ImageComponent fallback and getters/setters operate correctly")
    void testImageComponentFallback() {
        ImageComponent img = new ImageComponent("avatar-card", false);
        assertEquals("", img.getFallback());

        img.setFallback("https://crafatar.com/avatars/test-uuid?size=100&overlay");
        assertEquals("https://crafatar.com/avatars/test-uuid?size=100&overlay", img.getFallback());

        img.setImageName("https://minotar.net/avatar/test-uuid/100.png");
        assertEquals("https://minotar.net/avatar/test-uuid/100.png", img.getImageName());
    }

    @Test
    @DisplayName("DefaultInteractionRouter: hasInteractiveTarget returns true ONLY for buttons with click actions")
    void testHasInteractiveTargetOptimization() {
        UUID displayId = UUID.randomUUID();

        // Setup scene with one interactive button and one passive text component
        ButtonComponent button = new ButtonComponent("play-btn", false);
        button.setBounds(new Rect(10, 10, 100, 40));
        button.addOnClickAction("action1", Map.of("type", "command", "command", "say hi"));

        ButtonComponent emptyButton = new ButtonComponent("inert-btn", false);
        emptyButton.setBounds(new Rect(10, 60, 100, 40));

        TextComponent label = new TextComponent("title-lbl", false);
        label.setBounds(new Rect(10, 110, 200, 30));

        BoardConfig.SceneDefinition scene = new BoardConfig.SceneDefinition(
                "main",
                List.of(button, emptyButton, label)
        );

        DefaultInteractionRouter router = new DefaultInteractionRouter(
                mock(UniversalScheduler.class),
                new InteractionRateLimiter(8, 20),
                mock(EconomyGuard.class),
                (player, dId) -> displayId.equals(dId) ? scene : null
        );

        // Click over interactive button -> true
        assertTrue(router.hasInteractiveTarget(null, displayId, 20, 20),
                "Clicking interactive button must return true");

        // Click over button with no actions -> false
        assertFalse(router.hasInteractiveTarget(null, displayId, 20, 70),
                "Clicking empty button must return false");

        // Click over passive text label -> false
        assertFalse(router.hasInteractiveTarget(null, displayId, 20, 120),
                "Clicking passive text component must return false");

        // Click over empty background -> false
        assertFalse(router.hasInteractiveTarget(null, displayId, 300, 300),
                "Clicking empty area must return false");

        // Click on invalid displayId -> false
        assertFalse(router.hasInteractiveTarget(null, UUID.randomUUID(), 20, 20),
                "Clicking unknown display must return false");
    }

    @Test
    @DisplayName("CanvasBufferImpl: blitRaster copies raster accurately and marks touched tiles dirty")
    void testCanvasBufferBlitRaster() {
        CanvasBufferImpl canvas = new CanvasBufferImpl(2, 2); // 256x256
        assertEquals(256, canvas.getWidth());
        assertEquals(256, canvas.getHeight());

        // Create a 50x50 raster with color 42
        int rw = 50;
        int rh = 50;
        byte[] raster = new byte[rw * rh];
        Arrays.fill(raster, (byte) 42);

        // Blit across tile boundary at (100, 100) -> spans tile (0,0), (1,0), (0,1), (1,1)
        canvas.blitRaster(100, 100, rw, rh, raster);

        // Verify pixels in tile (0,0)
        assertEquals((byte) 42, canvas.getPixel(100, 100));
        assertEquals((byte) 42, canvas.getPixel(127, 127));

        // Verify pixels in tile (1,0)
        assertEquals((byte) 42, canvas.getPixel(128, 100));

        // Verify pixels in tile (0,1)
        assertEquals((byte) 42, canvas.getPixel(100, 128));

        // Verify pixels in tile (1,1)
        assertEquals((byte) 42, canvas.getPixel(128, 128));

        // Outside blit area should be untouched (0)
        assertEquals((byte) 0, canvas.getPixel(50, 50));

        // All 4 tiles should have dirty bits set
        assertTrue(canvas.isTileDirtyBit(0));
        assertTrue(canvas.isTileDirtyBit(1));
        assertTrue(canvas.isTileDirtyBit(2));
        assertTrue(canvas.isTileDirtyBit(3));
    }

    @Test
    @DisplayName("VirtualEntityRig: isNativeEntityGlowEnabled is false for rounded/circular boards")
    void testVirtualEntityRigNativeGlowGating() {
        Player mockViewer = mock(Player.class);
        QuarantinedIdAllocator allocator = new QuarantinedIdAllocator();

        // Rounded board -> false
        DisplayPlane roundedPlane = new DisplayPlane(
                UUID.randomUUID(), "world", new Vector3d(0, 64, 0),
                2, 2, new Vector3d(1, 0, 0), new Vector3d(0, -1, 0),
                32.0, 16.0, true, true, "red", "white", "border", "rounded", 16
        );
        VirtualEntityRig roundedRig = new VirtualEntityRig(mockViewer, roundedPlane, allocator, p -> {});
        assertFalse(roundedRig.isNativeEntityGlowEnabled(),
                "Rounded boards must disable native Minecraft entity glow to avoid rectangular corner leaking");

        // Circular board -> false
        DisplayPlane circlePlane = new DisplayPlane(
                UUID.randomUUID(), "world", new Vector3d(0, 64, 0),
                2, 2, new Vector3d(1, 0, 0), new Vector3d(0, -1, 0),
                32.0, 16.0, true, true, "red", "white", "border", "circle", 0
        );
        VirtualEntityRig circleRig = new VirtualEntityRig(mockViewer, circlePlane, allocator, p -> {});
        assertFalse(circleRig.isNativeEntityGlowEnabled(),
                "Circular boards must disable native Minecraft entity glow");

        // Rectangular board -> true
        DisplayPlane rectPlane = new DisplayPlane(
                UUID.randomUUID(), "world", new Vector3d(0, 64, 0),
                2, 2, new Vector3d(1, 0, 0), new Vector3d(0, -1, 0),
                32.0, 16.0, true, true, "red", "white", "border", "rectangle", 0
        );
        VirtualEntityRig rectRig = new VirtualEntityRig(mockViewer, rectPlane, allocator, p -> {});
        assertTrue(rectRig.isNativeEntityGlowEnabled(),
                "Rectangular boards allow native entity glow");
    }

    @Test
    @DisplayName("PixelTextComponent: renders to canvas buffer accurately via blitRaster")
    void testPixelTextComponentRender() {
        CanvasBufferImpl canvas = new CanvasBufferImpl(1, 1);
        PixelTextComponent comp = new PixelTextComponent("lbl", false);
        comp.setBounds(new Rect(10, 10, 80, 20));
        comp.setText("Hello");

        comp.render(canvas, RenderContext.empty());
        // Verify that some pixels inside bounds were drawn
        boolean foundDrawnPixel = false;
        for (int y = 10; y < 30; y++) {
            for (int x = 10; x < 90; x++) {
                if (canvas.getPixel(x, y) != 0) {
                    foundDrawnPixel = true;
                    break;
                }
            }
        }
        assertTrue(foundDrawnPixel, "PixelTextComponent must render non-zero pixels onto canvas");
    }
}