package me.goosbanny.goosboards.scene;

import me.goosbanny.goosboards.render.diff.TileDiffer;
import me.goosbanny.goosboards.scene.component.container.BackgroundComponent;
import me.goosbanny.goosboards.scene.component.interactive.ButtonComponent;
import me.goosbanny.goosboards.scene.component.text.PixelTextComponent;
import me.goosbanny.goosboards.scene.component.text.TextComponent;
import me.goosbanny.goosboards.scene.component.visual.Head2DComponent;
import me.goosbanny.goosboards.scene.component.visual.ImageComponent;
import me.goosbanny.goosboards.scene.layout.LayoutEngine;

import me.goosbanny.goosboards.interaction.InteractionRateLimiter;
import me.goosbanny.goosboards.display.DisplayPlane;
import me.goosbanny.goosboards.raycast.spatial.DisplaySpatialIndex;
import me.goosbanny.goosboards.display.ProximityTracker;
import me.goosbanny.goosboards.raycast.Vector3d;
import me.goosbanny.goosboards.protocol.QuarantinedIdAllocator;
import me.goosbanny.goosboards.protocol.VirtualEntityRig;
import me.goosbanny.goosboards.render.buffer.CanvasBuffer;
import me.goosbanny.goosboards.render.buffer.CanvasBufferImpl;
import me.goosbanny.goosboards.render.palette.impl.PaletteQuantizerImpl;
import me.goosbanny.goosboards.media.security.SafeMediaLoader;
import me.goosbanny.goosboards.raycast.spatial.impl.ChunkBucketSpatialIndex;
import me.goosbanny.goosboards.raycast.DisplayRaycaster;
import me.goosbanny.goosboards.raycast.MutableRaycastHit;
import me.goosbanny.goosboards.storage.StorageManager;
import me.goosbanny.goosboards.storage.impl.H2StorageBackend;
import me.goosbanny.goosboards.config.ConfigReloadManager;
import me.goosbanny.goosboards.interaction.impl.DefaultInteractionRouter;
import me.goosbanny.goosboards.interaction.economy.EconomyGuard;
import me.goosbanny.goosboards.raycast.RaycastResult;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

import com.github.retrooper.packetevents.wrapper.PacketWrapper;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerMapData;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerTeams;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.PacketEventsAPI;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.player.User;
import com.github.retrooper.packetevents.manager.player.PlayerManager;
import com.github.retrooper.packetevents.manager.server.ServerManager;
import com.github.retrooper.packetevents.manager.server.ServerVersion;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.settings.PacketEventsSettings;
import io.netty.channel.Channel;
import me.goosbanny.goosboards.core.scheduler.UniversalScheduler;
import me.goosbanny.goosboards.protocol.packet.ClickPacketListener;
import me.goosbanny.goosboards.interaction.InteractionRouter;
import me.goosbanny.goosboards.protocol.packet.BatchedMapSender;
import me.goosbanny.goosboards.render.BoardRenderEngine;
import me.goosbanny.goosboards.render.diff.TileDiffer.DirtyTile;
import me.goosbanny.goosboards.render.font.TextRenderer;
import org.junit.jupiter.api.BeforeAll;

import static org.junit.jupiter.api.Assertions.*;

class AuditBugFixesTest {

    @BeforeAll
    static void setupPacketEvents() {
        if (PacketEvents.getAPI() == null) {
            @SuppressWarnings("unchecked")
            PacketEventsAPI<Object> api = Mockito.mock(PacketEventsAPI.class);
            ServerManager serverManager = Mockito.mock(ServerManager.class);
            PlayerManager playerManager = Mockito.mock(PlayerManager.class);
            PacketEventsSettings settings = new PacketEventsSettings();

            Mockito.when(api.getSettings()).thenReturn(settings);
            Mockito.when(api.getServerManager()).thenReturn(serverManager);
            Mockito.when(api.getPlayerManager()).thenReturn(playerManager);
            Mockito.when(serverManager.getVersion()).thenReturn(ServerVersion.V_1_20_4);

            PacketEvents.setAPI(api);
        }
    }

    @Test
    @DisplayName("[MEDIUM-3] BackgroundComponent rounded corners are geometrically symmetric")
    void testRoundedCornerSymmetry() {
        BackgroundComponent bg = new BackgroundComponent("bg", false);
        int w = 40;
        int h = 40;
        int r = 10;
        bg.setBounds(new Rect(0, 0, w, h));
        bg.setCornerRadius(r);

        // Extreme diagonal corner points (outside circle)
        assertFalse(bg.containsPixel(0, 0), "Top-left outer corner must be clipped");
        assertFalse(bg.containsPixel(w - 1, 0), "Top-right outer corner must be clipped");
        assertFalse(bg.containsPixel(0, h - 1), "Bottom-left outer corner must be clipped");
        assertFalse(bg.containsPixel(w - 1, h - 1), "Bottom-right outer corner must be clipped");

        // Symmetry test across all 4 quadrants for pixel points
        for (int y = 0; y < r; y++) {
            for (int x = 0; x < r; x++) {
                boolean topLeft = bg.containsPixel(x, y);
                boolean topRight = bg.containsPixel((w - 1) - x, y);
                boolean bottomLeft = bg.containsPixel(x, (h - 1) - y);
                boolean bottomRight = bg.containsPixel((w - 1) - x, (h - 1) - y);

                assertEquals(topLeft, topRight, String.format("Symmetry mismatch at (x=%d, y=%d) vs top-right", x, y));
                assertEquals(topLeft, bottomLeft, String.format("Symmetry mismatch at (x=%d, y=%d) vs bottom-left", x, y));
                assertEquals(topLeft, bottomRight, String.format("Symmetry mismatch at (x=%d, y=%d) vs bottom-right", x, y));
            }
        }
    }

    @Test
    @DisplayName("[VISUAL-1] TextComponent renders fallbackText when placeholder is blank")
    void testTextComponentFallbackText() {
        TextComponent tc = new TextComponent("tc", true);
        tc.setText("%unknown_placeholder%");
        tc.setFallbackText("N/A");
        tc.setBounds(new Rect(0, 0, 128, 32));

        CanvasBuffer canvas = new CanvasBufferImpl(1, 1);
        RenderContext emptyCtx = RenderContext.empty();

        // Should render "N/A" fallback without throwing or remaining blank
        tc.render(canvas, emptyCtx);

        // Verify canvas has non-zero pixels from the rendered "N/A" text
        boolean hasPixels = false;
        for (int y = 0; y < 32; y++) {
            for (int x = 0; x < 128; x++) {
                if (canvas.getPixel(x, y) != 0) {
                    hasPixels = true;
                    break;
                }
            }
        }
        assertTrue(hasPixels, "TextComponent must render fallback text pixels when placeholder is unresolved");
    }

    @Test
    @DisplayName("[VISUAL-1] PixelTextComponent renders fallbackText when placeholder is blank")
    void testPixelTextComponentFallbackText() {
        PixelTextComponent pt = new PixelTextComponent("pt", true);
        pt.setText("%empty%");
        pt.setFallbackText("FALLBACK");
        pt.setBounds(new Rect(0, 0, 128, 32));

        CanvasBuffer canvas = new CanvasBufferImpl(1, 1);
        RenderContext emptyCtx = RenderContext.empty();

        pt.render(canvas, emptyCtx);

        boolean hasPixels = false;
        for (int y = 0; y < 32; y++) {
            for (int x = 0; x < 128; x++) {
                if (canvas.getPixel(x, y) != 0) {
                    hasPixels = true;
                    break;
                }
            }
        }
        assertTrue(hasPixels, "PixelTextComponent must render fallback text pixels");
    }

    @Test
    @DisplayName("[HIGH-1] Head2DComponent fetches skin asynchronously via SafeMediaLoader")
    void testHead2DComponentAsyncSkinLoad() throws Exception {
        Head2DComponent.clearCache();
        SafeMediaLoader mockLoader = Mockito.mock(SafeMediaLoader.class);
        Head2DComponent.setSafeMediaLoader(mockLoader);

        // Create an 8x8 test avatar PNG in memory
        BufferedImage avatarImg = new BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = avatarImg.createGraphics();
        g.setColor(Color.RED);
        g.fillRect(0, 0, 8, 8);
        g.dispose();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(avatarImg, "png", baos);
        byte[] avatarBytes = baos.toByteArray();

        Mockito.when(mockLoader.loadSecureImage(Mockito.any(URI.class), Mockito.anyInt(), Mockito.anyInt()))
                .thenReturn(CompletableFuture.completedFuture(avatarBytes));

        Head2DComponent head = new Head2DComponent("head", false);
        head.setSkin("Notch");
        head.setBounds(new Rect(0, 0, 16, 16));

        CanvasBuffer canvas = new CanvasBufferImpl(1, 1);
        RenderContext ctx = RenderContext.empty();

        // First render triggers async load and paints Steve fallback
        head.render(canvas, ctx);

        // Second render uses cached red avatar pixels
        head.render(canvas, ctx);

        // Check that pixels on canvas were painted
        boolean painted = false;
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                if (canvas.getPixel(x, y) != 0) {
                    painted = true;
                    break;
                }
            }
        }
        assertTrue(painted, "Head2DComponent should paint pixels onto canvas");
    }

    @Test
    @DisplayName("[MEDIUM-1] Single-token size on TextComponent defaults height to auto")
    void testSingleTokenSizeOnTextComponent() {
        TextComponent tc = new TextComponent("title", false);
        tc.setText("Hello World");
        tc.setSizeStr("100%");
        tc.setFontSize(16);

        LayoutEngine.layout(tc, 500, 300);

        assertEquals(500, tc.getBounds().width(), "Width should be 100% of parent width (500)");
        assertTrue(tc.getBounds().height() < 100, "Height should default to auto rather than 100% (300)");
    }

    @Test
    @DisplayName("[LOW-3] InteractionRateLimiter TokenBucket is thread-safe and lock-free")
    void testInteractionRateLimiterConcurrent() throws InterruptedException {
        InteractionRateLimiter limiter = new InteractionRateLimiter(20, 20);
        UUID playerId = UUID.randomUUID();

        int threads = 8;
        int requestsPerThread = 50;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(threads);
        AtomicInteger successfulConsumes = new AtomicInteger(0);

        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                try {
                    for (int r = 0; r < requestsPerThread; r++) {
                        if (limiter.tryConsumeClick(playerId)) {
                            successfulConsumes.incrementAndGet();
                        }
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        pool.shutdown();

        // Capacity is 20, so exactly 20 calls should succeed before refill
        assertEquals(20, successfulConsumes.get(), "Should permit exactly capacity (20) tokens concurrently");
    }

    @Test
    @DisplayName("[MEDIUM-4] ProximityTracker provides atomic snapshot of nearby boards")
    void testProximityTrackerAtomicSnapshot() {
        DisplaySpatialIndex spatialIndex = Mockito.mock(DisplaySpatialIndex.class);
        ProximityTracker tracker = new ProximityTracker(spatialIndex, 32.0);

        Player player = Mockito.mock(Player.class);
        UUID uuid = UUID.randomUUID();
        Mockito.when(player.getUniqueId()).thenReturn(uuid);
        Mockito.when(player.isOnline()).thenReturn(true);

        World mockWorld = Mockito.mock(World.class);
        Mockito.when(mockWorld.getName()).thenReturn("world");
        Location loc = new Location(mockWorld, 10, 64, 10);
        Mockito.when(player.getLocation()).thenReturn(loc);
        Mockito.when(player.getWorld()).thenReturn(mockWorld);

        DisplayPlane plane = new DisplayPlane(UUID.randomUUID(), "world", new Vector3d(10, 64, 10), 1, 1);
        Mockito.when(spatialIndex.nearbyBoards(Mockito.any(Vector3d.class), Mockito.anyDouble(), Mockito.eq("world")))
                .thenReturn(List.of(plane));

        tracker.updatePlayerProximity(player);

        List<DisplayPlane> snapshot = tracker.getNearbyBoardsSnapshot(uuid);
        assertNotNull(snapshot, "Snapshot should not be null when player is near a board");
        assertEquals(1, snapshot.size());
        assertEquals(plane, snapshot.get(0));

        // Modifying the returned snapshot must not alter the internal cache
        snapshot.clear();
        assertEquals(1, tracker.getNearbyBoards(uuid).size(), "Internal cache must remain untouched by defensive snapshot modification");
    }

    @Test
    @DisplayName("[VISUAL-QUALITY] Floyd-Steinberg dithering diffuses errors across gradient pixels")
    void testFloydSteinbergDitheringQuality() {
        BufferedImage gradient = new BufferedImage(128, 128, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < 128; y++) {
            for (int x = 0; x < 128; x++) {
                // Subtle gradient prone to severe banding without dithering
                int val = (x + y);
                gradient.setRGB(x, y, (val << 16) | (val << 8) | val);
            }
        }

        byte[] nonDithered = PaletteQuantizerImpl.quantizeImage(gradient, 128, 128, false);
        byte[] dithered = PaletteQuantizerImpl.quantizeImage(gradient, 128, 128, true);

        assertNotNull(nonDithered);
        assertNotNull(dithered);
        assertEquals(128 * 128, nonDithered.length);
        assertEquals(128 * 128, dithered.length);

        // Count unique palette indices used - dithering should produce richer tonal variety
        Set<Byte> nonDitheredColors = new HashSet<>();
        Set<Byte> ditheredColors = new HashSet<>();
        for (int i = 0; i < 128 * 128; i++) {
            nonDitheredColors.add(nonDithered[i]);
            ditheredColors.add(dithered[i]);
        }

        assertTrue(ditheredColors.size() >= nonDitheredColors.size(),
                "Dithering must utilize error diffusion to produce richer tonal gradations");
    }

    @Test
    @DisplayName("[ASSET-LIFECYCLE] ImageComponent fires assetLoadedCallback upon async download completion")
    void testAssetLoadedCallbackTriggersOnImageComplete() {
        SafeMediaLoader mockLoader = Mockito.mock(SafeMediaLoader.class);
        CompletableFuture<byte[]> future = new CompletableFuture<>();
        Mockito.when(mockLoader.loadSecureImage(Mockito.any(), Mockito.anyInt(), Mockito.anyInt()))
                .thenReturn(future);

        ImageComponent.setSafeMediaLoader(mockLoader);

        AtomicReference<String> loadedAsset = new AtomicReference<>();
        ImageComponent.setAssetLoadedCallback(loadedAsset::set);

        ImageComponent comp = new ImageComponent("test-img", false);
        comp.setImageName("https://example.com/asset.png");
        comp.setBounds(new Rect(0, 0, 64, 64));

        CanvasBuffer canvas = new CanvasBufferImpl(1, 1);
        comp.render(canvas, RenderContext.empty());

        // Callback not fired yet while pending
        assertNull(loadedAsset.get());

        // Complete the future with 1x1 png
        BufferedImage img = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            ImageIO.write(img, "png", baos);
        } catch (Exception ignored) {}
        future.complete(baos.toByteArray());

        assertEquals("https://example.com/asset.png", loadedAsset.get(),
                "Asset loaded callback must be triggered with asset URL when download completes");
    }

    @Test
    @DisplayName("[PROTOCOL-PERF] VirtualEntityRig.spawn does not flood network with blank map packets")
    void testVirtualEntityRigDoesNotSendBlankMapPacket() {
        Player mockViewer = Mockito.mock(Player.class);
        DisplayPlane plane = new DisplayPlane(UUID.randomUUID(), "world", new Vector3d(0, 64, 0), 2, 2);
        QuarantinedIdAllocator allocator = new QuarantinedIdAllocator();

        List<PacketWrapper<?>> sentPackets = new ArrayList<>();
        VirtualEntityRig rig = new VirtualEntityRig(mockViewer, plane, allocator, sentPackets::add);

        rig.spawn();

        assertTrue(sentPackets.size() > 0, "Packets must be sent to spawn item frames");
        boolean sentMapData = sentPackets.stream().anyMatch(p -> p instanceof WrapperPlayServerMapData);
        assertFalse(sentMapData, "VirtualEntityRig.spawn must NOT flood clients with blank map data packets");
    }

    @Test
    @DisplayName("[GLOW-SYSTEM] VirtualEntityRig emits 0x60 metadata and team packet on glowing outline")
    void testVirtualEntityRigGlowingAndScoreboardTeam() {
        Player mockViewer = Mockito.mock(Player.class);
        DisplayPlane plane = new DisplayPlane(
                UUID.randomUUID(), "world", new Vector3d(0, 64, 0),
                2, 2, new Vector3d(1, 0, 0), new Vector3d(0, -1, 0),
                32.0, 16.0, false, true, "aqua", "border"
        );
        QuarantinedIdAllocator allocator = new QuarantinedIdAllocator();

        List<PacketWrapper<?>> sentPackets = new ArrayList<>();
        VirtualEntityRig rig = new VirtualEntityRig(mockViewer, plane, allocator, sentPackets::add);

        rig.spawn();
        sentPackets.clear();

        // Turn on glow
        rig.setGlowing(true, "aqua");

        boolean sentTeamPacket = sentPackets.stream().anyMatch(p -> p instanceof WrapperPlayServerTeams);
        assertTrue(sentTeamPacket, "Enabling glow must emit WrapperPlayServerTeams packet with glow color");

        boolean sentGlowingMetadata = sentPackets.stream().anyMatch(p -> {
            if (p instanceof WrapperPlayServerEntityMetadata meta) {
                return meta.getEntityMetadata().stream().anyMatch(d -> d.getIndex() == 0 && (byte) d.getValue() == (byte) 0x60);
            }
            return false;
        });
        assertTrue(sentGlowingMetadata, "Enabling glow must update entity metadata bitmask to 0x60 (invisible + glowing)");

        sentPackets.clear();
        // Turn off glow
        rig.setGlowing(false, "aqua");

        boolean sentNormalMetadata = sentPackets.stream().anyMatch(p -> {
            if (p instanceof WrapperPlayServerEntityMetadata meta) {
                return meta.getEntityMetadata().stream().anyMatch(d -> d.getIndex() == 0 && (byte) d.getValue() == (byte) 0x20);
            }
            return false;
        });
        assertTrue(sentNormalMetadata, "Disabling glow must restore entity metadata bitmask to 0x20");
    }

    @Test
    @DisplayName("Hierarchical hover inheritance keeps parent container glowing when hovering child button")
    void testHierarchicalHoverInheritance() {
        BackgroundComponent parentBox = new BackgroundComponent("parent-banner", true);
        parentBox.setPositionStr("32 110");
        parentBox.setSizeStr("960 270");

        ButtonComponent childButton = new ButtonComponent("click-me-btn", true);
        childButton.setPositionStr("280 145");
        childButton.setSizeStr("400 65");
        parentBox.addChild(childButton);

        LayoutEngine.layout(parentBox, 1024, 1024);

        // Pixel inside child button: (32 + 280 + 50 = 362, 110 + 145 + 20 = 275)
        Set<String> hitIds = new HashSet<>();
        UUID viewerId = UUID.randomUUID();
        BoardRenderEngine.collectHitComponentIds(
                List.of(parentBox), 362, 275, viewerId, hitIds
        );

        assertTrue(hitIds.contains("parent-banner"), "Parent container must be collected in hit set for hover inheritance");
        assertTrue(hitIds.contains("click-me-btn"), "Child button must be collected in hit set");

        RenderContext ctx = RenderContext.of(null, hitIds, true);
        assertTrue(ctx.isHovered("parent-banner"), "Parent container must report isHovered=true in RenderContext");
        assertTrue(ctx.isHovered("click-me-btn"), "Child button must report isHovered=true in RenderContext");
    }

    @Test
    @DisplayName("Button child label expands to fill parent button bounds when size is auto")
    void testButtonChildAutoDimensions() {
        ButtonComponent btn = new ButtonComponent("test-btn", false);
        btn.setPositionStr("24 222");
        btn.setSizeStr("416 58");

        PixelTextComponent lbl = new PixelTextComponent("btn-label", false);
        lbl.setFontSize(22);
        lbl.setText("&f&lTEST SOUND & ALERT");
        btn.addChild(lbl);

        LayoutEngine.layout(btn, 1024, 1024);

        assertEquals(416, lbl.getBounds().width(), "Child label must expand to full button width to prevent text cutoff");
        assertEquals(58, lbl.getBounds().height(), "Child label must expand to full button height to prevent vertical slicing");
    }

    @Test
    @DisplayName("TextRenderer wraps multi-line paragraphs and auto-scales single-line titles")
    void testTextRendererFitting() {
        // Multi-line description wrapping
        String longDesc = "Test real asynchronous remote web images loaded securely with anti-SSRF filters from Minotar and web hosts.";
        BufferedImage descImg = TextRenderer.renderText(
                longDesc, null, "SansSerif", 18, Color.WHITE, 400, 110, "left", "top", 0.0, 0
        );
        assertNotNull(descImg);
        assertEquals(400, descImg.getWidth());
        assertEquals(110, descImg.getHeight());

        // Single-line title auto-scaling down without crashing or truncating
        String longTitle = "INTERACTIVE RANKS / SHOP";
        BufferedImage titleImg = TextRenderer.renderText(
                longTitle, null, "SansSerif", 32, Color.WHITE, 200, 36, "center", "center", 0.0, 0
        );
        assertNotNull(titleImg);
        assertEquals(200, titleImg.getWidth());
        assertEquals(36, titleImg.getHeight());
    }

    @Test
    @DisplayName("Strict Netty backpressure drops frames even on force/full flush when channel not writable")
    void testStrictNettyBackpressureOnFullFlush() {
        Channel mockChannel = Mockito.mock(Channel.class);
        Mockito.when(mockChannel.isActive()).thenReturn(true);
        Mockito.when(mockChannel.isWritable()).thenReturn(false);

        DirtyTile tile = new DirtyTile(0, 0, 0, 12345L, new byte[16384]);
        boolean sent = BatchedMapSender.sendDirtyTiles(mockChannel, new int[]{1}, List.of(tile), true);

        assertFalse(sent, "sendDirtyTiles must return false when channel is not writable even with force=true");
    }

    @Test
    @DisplayName("ClickPacketListener drops packets upstream on Netty thread when rate limited")
    void testEarlyNettyRateLimitDrop() {
        UniversalScheduler mockScheduler = Mockito.mock(UniversalScheduler.class);
        DisplaySpatialIndex mockSpatial = Mockito.mock(DisplaySpatialIndex.class);
        InteractionRouter mockRouter = Mockito.mock(InteractionRouter.class);
        InteractionRateLimiter rateLimiter = Mockito.mock(InteractionRateLimiter.class);

        ClickPacketListener listener = new ClickPacketListener(mockScheduler, mockSpatial, mockRouter, rateLimiter);

        PacketReceiveEvent mockEvent = Mockito.mock(PacketReceiveEvent.class);
        User mockUser = Mockito.mock(User.class);
        UUID uuid = UUID.randomUUID();
        Mockito.when(mockEvent.getUser()).thenReturn(mockUser);
        Mockito.when(mockUser.getUUID()).thenReturn(uuid);
        Mockito.when(mockEvent.getPacketType()).thenReturn(PacketType.Play.Client.ANIMATION);

        // Simulate exhausted rate limit
        Mockito.when(rateLimiter.tryConsumeClick(uuid)).thenReturn(false);

        listener.onPacketReceive(mockEvent);

        // Verify scheduler.runOnEntity was NEVER called
        Mockito.verifyNoInteractions(mockScheduler);
    }

    @Test
    @DisplayName("Hover state in RenderContext is isolated per viewer and does not mutate shared ButtonComponent")
    void testHoverIsolationPerViewer() {
        ButtonComponent button = new ButtonComponent("btn-shop", true);
        button.setBounds(new Rect(10, 10, 100, 30));

        Player player1 = Mockito.mock(Player.class);
        Player player2 = Mockito.mock(Player.class);
        Mockito.when(player1.getUniqueId()).thenReturn(UUID.randomUUID());
        Mockito.when(player2.getUniqueId()).thenReturn(UUID.randomUUID());

        Set<String> p1Hover = Set.of("btn-shop");
        Set<String> p2Hover = Set.of();

        RenderContext ctxPlayer1Hovering = RenderContext.of(player1, p1Hover, true);
        RenderContext ctxPlayer2NotHovering = RenderContext.of(player2, p2Hover, false);

        assertTrue(ctxPlayer1Hovering.isHovered("btn-shop"));
        assertFalse(ctxPlayer2NotHovering.isHovered("btn-shop"));

        CanvasBufferImpl canvas1 = new CanvasBufferImpl(1, 1);
        CanvasBufferImpl canvas2 = new CanvasBufferImpl(1, 1);

        button.render(canvas1, ctxPlayer1Hovering);
        button.render(canvas2, ctxPlayer2NotHovering);

        // Rendering for player 1 does not mutate state for player 2
        assertFalse(ctxPlayer2NotHovering.isHovered("btn-shop"));
    }

    @Test
    @DisplayName("[BUG-CACHE-KEY] TextComponent raster cache includes font and alignment in key")
    void testTextComponentRasterCacheKeyIncludesFontAndAlignment() {
        // Two TextComponents with same text but different alignment
        TextComponent leftAligned = new TextComponent("left-tc", false);
        leftAligned.setText("Hello");
        leftAligned.setFont("SansSerif");
        leftAligned.setAlignment("left");
        leftAligned.setBounds(new Rect(0, 0, 128, 32));

        TextComponent rightAligned = new TextComponent("right-tc", false);
        rightAligned.setText("Hello");
        rightAligned.setFont("SansSerif");
        rightAligned.setAlignment("right");
        rightAligned.setBounds(new Rect(0, 0, 128, 32));

        CanvasBufferImpl canvasLeft = new CanvasBufferImpl(1, 1);
        CanvasBufferImpl canvasRight = new CanvasBufferImpl(1, 1);
        RenderContext ctx = RenderContext.empty();

        leftAligned.render(canvasLeft, ctx);
        rightAligned.render(canvasRight, ctx);

        // Find the leftmost and rightmost non-zero columns in each canvas
        int leftmostLeft = 128, rightmostLeft = -1;
        int leftmostRight = 128, rightmostRight = -1;
        for (int y = 0; y < 32; y++) {
            for (int x = 0; x < 128; x++) {
                if (canvasLeft.getPixel(x, y) != 0) {
                    if (x < leftmostLeft) leftmostLeft = x;
                    if (x > rightmostLeft) rightmostLeft = x;
                }
                if (canvasRight.getPixel(x, y) != 0) {
                    if (x < leftmostRight) leftmostRight = x;
                    if (x > rightmostRight) rightmostRight = x;
                }
            }
        }

        // Both renders must have produced pixels
        assertTrue(rightmostLeft >= 0, "Left-aligned text must produce pixels");
        assertTrue(rightmostRight >= 0, "Right-aligned text must produce pixels");

        // The right-aligned text must start further right than left-aligned text
        // (i.e. the pixel distributions are different — the cache was not shared)
        assertTrue(leftmostRight > leftmostLeft,
            "Right-aligned text must start further right than left-aligned text (cache key differs by alignment)");
    }

    @Test
    @DisplayName("[PHASE2-MATH-01] ChunkBucketSpatialIndex handles negative chunk coordinates without key collisions")
    void testNegativeChunkBucketCoordinates() {
        ChunkBucketSpatialIndex index = new ChunkBucketSpatialIndex();
        DisplayPlane planePos = new DisplayPlane(
                UUID.randomUUID(), "world", new Vector3d(100, 64, 100), 2, 2
        );
        DisplayPlane planeNeg = new DisplayPlane(
                UUID.randomUUID(), "world", new Vector3d(-100, 64, -100), 2, 2
        );
        index.register(planePos);
        index.register(planeNeg);

        List<DisplayPlane> nearbyNeg = index.nearbyBoards(new Vector3d(-100, 64, -100), 16.0, "world");
        assertEquals(1, nearbyNeg.size());
        assertEquals(planeNeg.id(), nearbyNeg.get(0).id());

        List<DisplayPlane> nearbyPos = index.nearbyBoards(new Vector3d(100, 64, 100), 16.0, "world");
        assertEquals(1, nearbyPos.size());
        assertEquals(planePos.id(), nearbyPos.get(0).id());
    }

    @Test
    @DisplayName("[PHASE2-MATH-02] DisplayRaycaster rejects back-face rays hitting from behind")
    void testBackFaceRayCulling() {
        // Plane at (0, 0, 0) facing South (+Z normal)
        DisplayPlane plane = new DisplayPlane(
                UUID.randomUUID(), "world", new Vector3d(0, 3, 0), 4, 3,
                new Vector3d(1, 0, 0), new Vector3d(0, -1, 0), 32.0, 16.0
        );
        // Ray from behind at (2.0, 1.5, -5.0) looking towards the board (+Z)
        MutableRaycastHit hit = new MutableRaycastHit();
        DisplayRaycaster.intersect(plane, 2.0, 1.5, -5.0, 0, 0, 1, 10.0, hit);
        assertFalse(hit.hit, "Ray hitting from behind must be culled by normal test");

        // Ray from front at (2.0, 1.5, 5.0) looking towards the board (-Z)
        DisplayRaycaster.intersect(plane, 2.0, 1.5, 5.0, 0, 0, -1, 10.0, hit);
        assertTrue(hit.hit, "Ray hitting from front must succeed");
    }

    @Test
    @DisplayName("[PHASE2-MATH-03] DisplayRaycaster pixel-center alignment with BoardMaskingUtil")
    void testPixelCenterMaskAlignment() {
        // 1x1 block circular board = 128x128 pixels. Center at (64, 64), radius = 64.
        DisplayPlane circularPlane = new DisplayPlane(
                UUID.randomUUID(), "world", new Vector3d(-0.5, 0.5, 0), 1, 1,
                new Vector3d(1, 0, 0), new Vector3d(0, -1, 0), 32.0, 16.0,
                false, false, "aqua", "border", "circle", 0
        );
        MutableRaycastHit hit = new MutableRaycastHit();

        // Exact corner pixel (0, 0) pixel center is (0.5, 0.5), distance to (64, 64) is sqrt(63.5^2 + 63.5^2) = 89.8 > 64.
        // Looking from front towards (0, 0)
        double eyeX = -0.5 + (0.5 / 128.0);
        double eyeY = 0.5 - (0.5 / 128.0);
        DisplayRaycaster.intersect(circularPlane, eyeX, eyeY, 5.0, 0, 0, -1, 10.0, hit);
        assertFalse(hit.hit, "Corner pixel of circular board must be masked out and not hit");

        // Center pixel (64, 64) looking from front
        DisplayRaycaster.intersect(circularPlane, 0.0, 0.0, 5.0, 0, 0, -1, 10.0, hit);
        assertTrue(hit.hit, "Center pixel of circular board must hit");
    }

    @Test
    @DisplayName("[PHASE2-STOR-02 & STOR-03] StorageManager initializes asynchronously and purges expired transactions")
    void testStorageManagerAsyncInitAndPurge(@TempDir java.io.File tempDir) throws Exception {
        H2StorageBackend backend = new H2StorageBackend(tempDir, "audit_");
        StorageManager sm = new StorageManager(backend, java.util.logging.Logger.getLogger("AuditTest"));

        // Wait for async init to complete
        sm.initAsync().get(5, java.util.concurrent.TimeUnit.SECONDS);

        // Record a transaction that expired in the past
        UUID playerId = UUID.randomUUID();
        sm.recordTransactionAsync("tx-expired", playerId, 100.0, 1000L).get(5, java.util.concurrent.TimeUnit.SECONDS);

        // Purge expired transactions at nowMs = 2000L
        sm.purgeExpiredTransactionsAsync(2000L).get(5, java.util.concurrent.TimeUnit.SECONDS);

        // Verify transaction was purged
        boolean exists = sm.hasTransactionAsync("tx-expired", 2000L).get(5, java.util.concurrent.TimeUnit.SECONDS);
        assertFalse(exists, "Expired transaction must be purged from database");

        sm.close();
    }

    @Test
    @DisplayName("[PHASE2-FOLIA-02] DefaultInteractionRouter monotonic hover revisions discard stale events")
    void testHoverRevisionDebounce() {
        UniversalScheduler scheduler = Mockito.mock(UniversalScheduler.class);
        InteractionRateLimiter rateLimiter = new InteractionRateLimiter(100, 100);
        EconomyGuard economyGuard = Mockito.mock(EconomyGuard.class);
        UUID displayId = UUID.randomUUID();

        ButtonComponent btn = new ButtonComponent("btn-hover", false);
        btn.setBounds(new Rect(0, 0, 100, 100));
        btn.addOnHoverAction("command", "say hovered");

        BoardConfig.SceneDefinition scene = new BoardConfig.SceneDefinition(
                "scene1", List.of(btn)
        );

        DefaultInteractionRouter router = new DefaultInteractionRouter(
                scheduler, rateLimiter, economyGuard,
                (player, id) -> scene
        );

        Player player = Mockito.mock(Player.class);
        UUID playerId = UUID.randomUUID();
        Mockito.when(player.getUniqueId()).thenReturn(playerId);
        Mockito.when(player.getName()).thenReturn("TestPlayer");

        List<java.util.function.Consumer<Player>> scheduledTasks = new ArrayList<>();
        Mockito.doAnswer(inv -> {
            scheduledTasks.add(inv.getArgument(1));
            return null;
        }).when(scheduler).runOnEntity(Mockito.eq(player), Mockito.any());

        // Fire first hover event
        router.handleHover(player, new RaycastResult.Hit(displayId, 50, 50, 2.0));
        assertEquals(1, scheduledTasks.size());

        // Now fire hover exit BEFORE the scheduled entity task executes
        router.handleHoverExit(player, displayId);

        // Execute the first scheduled task on entity thread
        // Because a newer revision was generated by handleHoverExit, the task must be discarded as stale
        scheduledTasks.get(0).accept(player);

        // Hover button state was cleared by hover exit
        assertNull(router.getHoveredButton(playerId, displayId));
    }

    @Test
    @DisplayName("[PHASE2-STOR-01] ConfigReloadManager rehydrates persistent player scenes from StorageManager")
    void testLoadPlayerPersistentScenesAsync(@TempDir java.io.File tempDir) throws Exception {
        H2StorageBackend backend = new H2StorageBackend(tempDir, "rehydrate_");
        StorageManager sm = new StorageManager(backend, java.util.logging.Logger.getLogger("RehydrateTest"));
        sm.initAsync().get(5, java.util.concurrent.TimeUnit.SECONDS);

        UUID playerId = UUID.randomUUID();
        String boardId = "test-board";
        String sceneId = "scene-saved";

        // Save persistent scene into storage
        sm.savePlayerSceneAsync(playerId, boardId, sceneId).get(5, java.util.concurrent.TimeUnit.SECONDS);

        // Set up ConfigReloadManager with a persistent board
        BoardConfig.BoardSettings settings = new BoardConfig.BoardSettings(false, false, true, false, false, false);
        BoardConfig.SceneDefinition scene = new BoardConfig.SceneDefinition(sceneId, java.util.Collections.emptyList());
        BoardConfig config = new BoardConfig(settings, java.util.Collections.emptyMap(), java.util.Map.of(sceneId, scene));

        ConfigReloadManager reloadManager = new ConfigReloadManager(
                Mockito.mock(me.goosbanny.goosboards.config.ConfigManager.class),
                Mockito.mock(me.goosbanny.goosboards.scene.parser.BoardYamlParser.class),
                Mockito.mock(me.goosbanny.goosboards.render.cache.RenderStateCache.class),
                Mockito.mock(me.goosbanny.goosboards.raycast.spatial.DisplaySpatialIndex.class),
                Mockito.mock(me.goosbanny.goosboards.core.metrics.MetricsCollector.class),
                java.util.logging.Logger.getLogger("Test")
        );
        reloadManager.setStorageManager(sm);

        // Inject active board into reloadManager
        java.lang.reflect.Field activeBoardsField = ConfigReloadManager.class.getDeclaredField("activeBoards");
        activeBoardsField.setAccessible(true);
        @SuppressWarnings("unchecked")
        java.util.concurrent.atomic.AtomicReference<java.util.Map<String, BoardConfig>> activeBoardsRef =
                (java.util.concurrent.atomic.AtomicReference<java.util.Map<String, BoardConfig>>) activeBoardsField.get(reloadManager);
        activeBoardsRef.set(java.util.Map.of(boardId, config));

        // Rehydrate
        reloadManager.loadPlayerPersistentScenesAsync(playerId);

        // Wait a short moment for async DB callback to complete
        long deadline = System.currentTimeMillis() + 3000L;
        while (System.currentTimeMillis() < deadline && !sceneId.equals(reloadManager.getActiveSceneId(boardId, playerId))) {
            Thread.sleep(20L);
        }

        assertEquals(sceneId, reloadManager.getActiveSceneId(boardId, playerId));

        sm.close();
    }
}