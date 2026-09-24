package me.goosbanny.goosboards.render;

import me.goosbanny.goosboards.protocol.packet.BatchedMapSender;
import me.goosbanny.goosboards.render.buffer.CanvasBufferImpl;
import me.goosbanny.goosboards.render.diff.TileDiffer;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.PacketEventsAPI;
import com.github.retrooper.packetevents.injector.ChannelInjector;
import com.github.retrooper.packetevents.manager.player.PlayerManager;
import com.github.retrooper.packetevents.manager.protocol.ProtocolManager;
import com.github.retrooper.packetevents.manager.server.ServerManager;
import com.github.retrooper.packetevents.manager.server.ServerVersion;
import com.github.retrooper.packetevents.netty.NettyManager;
import com.github.retrooper.packetevents.settings.PacketEventsSettings;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerMapData;
import com.sun.management.ThreadMXBean;
import io.github.retrooper.packetevents.netty.NettyManagerImpl;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.embedded.EmbeddedChannel;
import me.goosbanny.goosboards.render.diff.TileDiffer.DirtyTile;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import me.goosbanny.goosboards.display.DisplayPlane;
import me.goosbanny.goosboards.display.ProximityTracker;
import me.goosbanny.goosboards.raycast.Vector3d;
import me.goosbanny.goosboards.raycast.spatial.impl.ChunkBucketSpatialIndex;

import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ZeroAllocRenderPipelineAuditTest {

    static class StubChannel extends EmbeddedChannel {
        private final ChannelFuture succeeded;

        public StubChannel() {
            this.succeeded = newSucceededFuture();
        }

        @Override
        public boolean isActive() {
            return true;
        }

        @Override
        public boolean isWritable() {
            return true;
        }

        @Override
        public ChannelFuture write(Object msg) {
            return succeeded;
        }

        @Override
        public Channel flush() {
            return this;
        }
    }

    static class FakePacketEventsAPI extends PacketEventsAPI<Object> {
        private final ServerManager serverManager = mock(ServerManager.class);
        private final PlayerManager playerManager = mock(PlayerManager.class);
        private final NettyManager nettyManager = new NettyManagerImpl();
        private final PacketEventsSettings settings = new PacketEventsSettings();

        public FakePacketEventsAPI() {
            when(serverManager.getVersion()).thenReturn(ServerVersion.V_1_20_4);
        }

        @Override public boolean isLoaded() { return true; }
        @Override public void init() {}
        @Override public boolean isInitialized() { return true; }
        @Override public boolean isTerminated() { return false; }
        @Override public Object getPlugin() { return null; }
        @Override public ServerManager getServerManager() { return serverManager; }
        @Override public ProtocolManager getProtocolManager() { return null; }
        @Override public PlayerManager getPlayerManager() { return playerManager; }
        @Override public NettyManager getNettyManager() { return nettyManager; }
        @Override public ChannelInjector getInjector() { return null; }
        @Override public PacketEventsSettings getSettings() { return settings; }
    }

    @BeforeAll
    static void setupPacketEvents() {
        PacketEvents.setAPI(new FakePacketEventsAPI());
    }

    @Test
    @DisplayName("Acceptance Criterion 1: Animated 4x3 board at 20 FPS with 5 viewers allocates < 50 KB/sec in steady state")
    void testSteadyStateAllocationRateBelow50KBps() {
        int widthTiles = 4;
        int heightTiles = 3;
        int viewerCount = 5;
        int fps = 20;
        int durationSeconds = 60;
        int totalFrames = fps * durationSeconds; // 1,200 frames

        CanvasBufferImpl buffer = new CanvasBufferImpl(widthTiles, heightTiles);
        int[] mapIds = new int[widthTiles * heightTiles];
        for (int i = 0; i < mapIds.length; i++) {
            mapIds[i] = 1000 + i;
        }

        // Use un-instrumented Netty stub channels to eliminate Mockito Invocation tracking overhead
        List<Channel> channels = new ArrayList<>();
        for (int i = 0; i < viewerCount; i++) {
            channels.add(new StubChannel());
        }

        // Initial full render pass & clean
        for (int ty = 0; ty < heightTiles; ty++) {
            for (int tx = 0; tx < widthTiles; tx++) {
                buffer.markClean(tx, ty, buffer.getCanvasTile(tx, ty).computeHash());
            }
        }

        // Warmup JIT (200 frames)
        for (int f = 0; f < 200; f++) {
            simulateFrame(buffer, mapIds, channels, f);
        }

        ThreadMXBean threadBean = (ThreadMXBean) ManagementFactory.getThreadMXBean();
        boolean canMeasureAlloc = threadBean.isThreadAllocatedMemorySupported()
                && threadBean.isThreadAllocatedMemoryEnabled();

        if (canMeasureAlloc) {
            long threadId = Thread.currentThread().threadId();
            long allocBefore = threadBean.getThreadAllocatedBytes(threadId);

            // Execute 60-second steady-state simulation (1,200 frames)
            for (int f = 0; f < totalFrames; f++) {
                simulateFrame(buffer, mapIds, channels, f);
            }

            long allocAfter = threadBean.getThreadAllocatedBytes(threadId);
            long totalAllocBytes = allocAfter - allocBefore;
            double allocKBps = (totalAllocBytes / 1024.0) / (double) durationSeconds;

            System.out.printf("Zero-Alloc Steady State Audit: Total Alloc = %.2f KB over %ds (Rate: %.2f KB/sec)%n",
                    totalAllocBytes / 1024.0, durationSeconds, allocKBps);

            assertTrue(allocKBps < 50.0,
                    String.format("Acceptance Criterion 1: Steady-state allocation rate must be < 50 KB/sec, was %.2f KB/sec", allocKBps));
        }
    }

    private final List<DirtyTile> dirtyTilesScratch = new ArrayList<>(16);
    private final List<WrapperPlayServerMapData> packetsScratch = new ArrayList<>(16);

    private void simulateFrame(CanvasBufferImpl buffer, int[] mapIds, List<Channel> channels, int frameIndex) {
        // Mutate animated component area on tile (1, 1) - e.g. 16x16 button hover or GIF frame
        int startX = 128 + (frameIndex % 10);
        int startY = 128 + (frameIndex % 10);
        byte color = (byte) ((frameIndex % 50) + 1);

        for (int y = startY; y < startY + 16; y++) {
            for (int x = startX; x < startX + 16; x++) {
                buffer.setPixel(x, y, color);
            }
        }

        // Find dirty tiles into reusable scratch list
        TileDiffer.findDirtyTiles(buffer, buffer.getWidthTiles(), buffer.getHeightTiles(), dirtyTilesScratch);

        if (!dirtyTilesScratch.isEmpty()) {
            // Single-instance broadcast: pre-build once into scratch list and write to all 5 channels
            BatchedMapSender.prebuildPackets(mapIds, dirtyTilesScratch, packetsScratch);
            BatchedMapSender.broadcastDirtyPackets(channels, packetsScratch);

            // Mark tiles clean
            int dirtyCount = dirtyTilesScratch.size();
            for (int i = 0; i < dirtyCount; i++) {
                DirtyTile dt = dirtyTilesScratch.get(i);
                buffer.markClean(dt.tileX(), dt.tileY(), dt.newHash());
            }
        }
    }

    @Test
    @DisplayName("Acceptance Criterion 2: ProximityTracker allocates 0 bytes for stationary players across 1,000 checks")
    void testStationaryPlayerProximityAllocatesZeroBytes() {
        ChunkBucketSpatialIndex spatialIndex = new ChunkBucketSpatialIndex();
        DisplayPlane plane = new DisplayPlane(
                UUID.randomUUID(), "world", new Vector3d(0, 64, 0), 2, 2
        );
        spatialIndex.register(plane);

        ProximityTracker tracker = new ProximityTracker(spatialIndex, 32.0);
        UUID playerId = UUID.randomUUID();

        // Warm up and compile JIT (10,000 iterations)
        for (int i = 0; i < 10000; i++) {
            tracker.updatePlayerProximity(playerId, "world", 5.25, 64.0, 5.75);
        }

        ThreadMXBean threadBean = (ThreadMXBean) ManagementFactory.getThreadMXBean();
        if (threadBean.isThreadAllocatedMemorySupported() && threadBean.isThreadAllocatedMemoryEnabled()) {
            long threadId = Thread.currentThread().threadId();
            long before = threadBean.getThreadAllocatedBytes(threadId);

            for (int i = 0; i < 1000; i++) {
                tracker.updatePlayerProximity(playerId, "world", 5.25, 64.0, 5.75);
            }

            long after = threadBean.getThreadAllocatedBytes(threadId);
            long totalAllocated = after - before;
            System.out.println("testStationaryPlayerProximityAllocatesZeroBytes totalAllocated = " + totalAllocated);

            assertEquals(0L, totalAllocated,
                    "Stationary player proximity sweep must allocate exactly 0 bytes on the heap");
        }
    }

    @Test
    @DisplayName("Acceptance Criterion 3: 40% sub-rectangle rule sends <= 300 bytes for 16x16 button mutations")
    void testPartialSubRectangleBandwidthReduction() {
        CanvasBufferImpl buffer = new CanvasBufferImpl(1, 1);
        int mapId = 1001;

        // Clean initial state
        buffer.markClean(0, 0, buffer.getCanvasTile(0, 0).computeHash());

        // Mutate 16x16 button in tile (0, 0)
        for (int y = 20; y < 36; y++) {
            for (int x = 20; x < 36; x++) {
                buffer.setPixel(x, y, (byte) 55);
            }
        }

        List<DirtyTile> dirtyTiles = new ArrayList<>();
        TileDiffer.findDirtyTiles(buffer, 1, 1, dirtyTiles);

        assertEquals(1, dirtyTiles.size(), "Exactly 1 dirty tile must be detected");
        DirtyTile dt = dirtyTiles.get(0);
        assertTrue(dt.isPartial(), "16x16 mutation must qualify as partial sub-rectangle update");
        assertEquals(16, dt.columns());
        assertEquals(16, dt.rows());
        assertEquals(256, dt.payloadSize());

        WrapperPlayServerMapData packet = BatchedMapSender.buildDirtyTilePacket(mapId, dt);
        assertNotNull(packet);
        assertEquals(16, packet.getColumns());
        assertEquals(16, packet.getRows());
        assertEquals(20, packet.getX());
        assertEquals(20, packet.getZ());
        assertEquals(256, packet.getData().length, "Payload size must be exactly 256 bytes (< 300 bytes rule)");
    }
}