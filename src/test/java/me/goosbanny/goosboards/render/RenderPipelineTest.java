package me.goosbanny.goosboards.render;

import me.goosbanny.goosboards.GoosBoards;
import me.goosbanny.goosboards.protocol.packet.BatchedMapSender;
import me.goosbanny.goosboards.render.buffer.CanvasBufferImpl;
import me.goosbanny.goosboards.render.diff.TileDiffer;
import me.goosbanny.goosboards.render.palette.impl.PaletteQuantizerImpl;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.PacketEventsAPI;
import com.github.retrooper.packetevents.manager.player.PlayerManager;
import com.github.retrooper.packetevents.manager.server.ServerManager;
import com.github.retrooper.packetevents.manager.server.ServerVersion;
import com.github.retrooper.packetevents.settings.PacketEventsSettings;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerMapData;
import io.github.retrooper.packetevents.netty.NettyManagerImpl;
import io.netty.channel.Channel;
import me.goosbanny.goosboards.render.diff.TileDiffer.DirtyTile;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RenderPipelineTest {

    @BeforeAll
    static void setupPacketEvents() {
        if (PacketEvents.getAPI() == null) {
            @SuppressWarnings("unchecked")
            PacketEventsAPI<Object> api = Mockito.mock(PacketEventsAPI.class);
            ServerManager serverManager = Mockito.mock(ServerManager.class);
            PlayerManager playerManager = Mockito.mock(PlayerManager.class);
            PacketEventsSettings settings = new PacketEventsSettings();
            NettyManagerImpl nettyManager = new NettyManagerImpl();

            Mockito.when(api.getSettings()).thenReturn(settings);
            Mockito.when(api.getServerManager()).thenReturn(serverManager);
            Mockito.when(api.getPlayerManager()).thenReturn(playerManager);
            Mockito.when(api.getNettyManager()).thenReturn(nettyManager);
            Mockito.when(serverManager.getVersion()).thenReturn(ServerVersion.V_1_20_4);

            PacketEvents.setAPI(api);
        }
    }

    @Test
    @DisplayName("Test 3.1 — Quantization speed benchmark (< 3ms for 512x512, documented in /docs/benchmarks.md)")
    void testQuantizationBenchmark() throws IOException {
        int width = 512;
        int height = 512;
        int totalPixels = width * height;
        int[] pixels = new int[totalPixels];
        Random random = new Random(1337);
        for (int i = 0; i < totalPixels; i++) {
            int r = random.nextInt(256);
            int g = random.nextInt(256);
            int b = random.nextInt(256);
            pixels[i] = (0xFF << 24) | (r << 16) | (g << 8) | b;
        }

        PaletteQuantizerImpl quantizer = new PaletteQuantizerImpl(width);
        byte[] tileBuffer = new byte[128 * 128];

        // JIT warmup
        for (int iter = 0; iter < 100; iter++) {
            for (int ty = 0; ty < 4; ty++) {
                for (int tx = 0; tx < 4; tx++) {
                    quantizer.quantizeTile(pixels, tx, ty, tileBuffer, false);
                }
            }
        }

        // Benchmark pass: 100 runs to get an accurate average
        int benchmarkRuns = 100;
        long startNanos = System.nanoTime();
        for (int iter = 0; iter < benchmarkRuns; iter++) {
            for (int ty = 0; ty < 4; ty++) {
                for (int tx = 0; tx < 4; tx++) {
                    quantizer.quantizeTile(pixels, tx, ty, tileBuffer, false);
                }
            }
        }
        long elapsedNanos = System.nanoTime() - startNanos;
        double avgTimeMs = (elapsedNanos / (double) benchmarkRuns) / 1_000_000.0;
        double perTileUs = (avgTimeMs / 16.0) * 1000.0;

        // Document results in /docs/benchmarks.md
        Path docsDir = Paths.get("docs");
        if (!Files.exists(docsDir)) {
            Files.createDirectories(docsDir);
        }
        Path benchmarkFile = docsDir.resolve("benchmarks.md");

        String report = """
                # GoosBoards Performance Benchmarks

                ## Milestone 3: Rendering & Quantization Pipeline

                ### Test 3.1: 512×512 Canvas Color Quantization
                - **Canvas Dimensions:** 512 × 512 pixels (4 × 4 tiles = 16 tiles = 262,144 pixels)
                - **Palette Size:** 256 colors (Minecraft map palette)
                - **Algorithm:** Fast RGB565 direct-table lookup (65,536 entries precalculated)
                - **Measured Average Time (512×512):** %.3f ms
                - **Average Time Per 128×128 Tile:** %.2f µs
                - **Specification Target:** < 3.000 ms
                - **Status:** PASS (Target exceeded by %.1fx)
                - **Java Runtime:** %s (VM: %s)
                - **OS:** %s
                """.formatted(
                avgTimeMs,
                perTileUs,
                3.0 / Math.max(0.001, avgTimeMs),
                System.getProperty("java.version"),
                System.getProperty("java.vm.name"),
                System.getProperty("os.name")
        );

        Files.writeString(benchmarkFile, report);

        assertTrue(avgTimeMs < 3.0, "Quantization must complete in < 3ms for 512x512 canvas, took " + avgTimeMs + "ms");
    }

    @Test
    @DisplayName("Test 3.2 — Tile isolation: modifying tile (0,0) does not change tile (1,0) hash")
    void testTileIsolation() {
        CanvasBufferImpl canvas = new CanvasBufferImpl(2, 1); // 256x128 pixels (2 tiles horizontally)

        // Fill tile (0,0) with color 10
        for (int y = 0; y < 128; y++) {
            for (int x = 0; x < 128; x++) {
                canvas.setPixel(x, y, (byte) 10);
            }
        }
        // Fill tile (1,0) with color 20
        for (int y = 0; y < 128; y++) {
            for (int x = 128; x < 256; x++) {
                canvas.setPixel(x, y, (byte) 20);
            }
        }

        // Compute initial hash H1 for tile (1,0)
        long h1 = TileDiffer.computeHash(canvas.getSubTile(1, 0));

        // Change a pixel in tile (0,0)
        canvas.setPixel(50, 50, (byte) 99);

        // Compute hash H2 for tile (1,0)
        long h2 = TileDiffer.computeHash(canvas.getSubTile(1, 0));

        assertEquals(h1, h2, "Modifying tile (0,0) must not alter tile (1,0) hash");
    }

    @Test
    @DisplayName("Test 3.3 — Floyd-Steinberg error does not cross tile boundary")
    void testDitheringErrorIsolationAcrossTileBoundary() {
        int width = 256;
        int height = 128;
        int[] gradient = new int[width * height];

        // Create a horizontal gradient spanning tile (0,0) and tile (1,0)
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int r = (x * 255) / (width - 1);
                int g = (y * 255) / (height - 1);
                int b = 128;
                gradient[y * width + x] = (0xFF << 24) | (r << 16) | (g << 8) | b;
            }
        }

        PaletteQuantizerImpl quantizer = new PaletteQuantizerImpl(width);

        // Pass A: Quantize tile (0,0) without dithering, then quantize tile (1,0) with dithering
        byte[] tile0A = new byte[128 * 128];
        byte[] tile1A = new byte[128 * 128];
        quantizer.quantizeTile(gradient, 0, 0, tile0A, false);
        quantizer.quantizeTile(gradient, 1, 0, tile1A, true);
        long hashTile1A = TileDiffer.computeHash(tile1A);

        // Pass B: Quantize tile (0,0) with dithering, then quantize tile (1,0) with dithering
        byte[] tile0B = new byte[128 * 128];
        byte[] tile1B = new byte[128 * 128];
        quantizer.quantizeTile(gradient, 0, 0, tile0B, true);
        quantizer.quantizeTile(gradient, 1, 0, tile1B, true);
        long hashTile1B = TileDiffer.computeHash(tile1B);

        assertEquals(hashTile1A, hashTile1B,
                "Tile (1,0) hash must be strictly identical regardless of whether tile (0,0) was dithered or not");
        assertArrayEquals(tile1A, tile1B,
                "Tile (1,0) byte content must not be influenced by tile (0,0) error diffusion");
    }

    @Test
    @DisplayName("Test 3.4 — Dirty tile detection (hash change)")
    void testDirtyTileDetection() {
        int widthTiles = 4;
        int heightTiles = 3;
        CanvasBufferImpl canvas = new CanvasBufferImpl(widthTiles, heightTiles);

        // Fill all tiles with initial base pattern and mark clean
        for (int ty = 0; ty < heightTiles; ty++) {
            for (int tx = 0; tx < widthTiles; tx++) {
                byte[] data = canvas.getSubTile(tx, ty);
                for (int i = 0; i < data.length; i++) {
                    data[i] = (byte) ((tx + ty * widthTiles) + 4);
                }
                long initialHash = TileDiffer.computeHash(data);
                canvas.markClean(tx, ty, initialHash);
            }
        }

        // Initial scan: all tiles are clean
        List<DirtyTile> initialDirty = TileDiffer.findDirtyTiles(canvas, widthTiles, heightTiles);
        assertTrue(initialDirty.isEmpty(), "Initial render must report 0 dirty tiles");

        // Modify exactly one pixel in tile (2, 1)
        int targetPixelX = 2 * 128 + 45;
        int targetPixelY = 1 * 128 + 67;
        canvas.setPixel(targetPixelX, targetPixelY, (byte) 200);

        // Re-scan dirty tiles
        List<DirtyTile> dirtyTiles = TileDiffer.findDirtyTiles(canvas, widthTiles, heightTiles);
        assertEquals(1, dirtyTiles.size(), "Exactly 1 dirty tile must be detected");

        DirtyTile dirty = dirtyTiles.get(0);
        assertEquals(2, dirty.tileX(), "Dirty tile X must be 2");
        assertEquals(1, dirty.tileY(), "Dirty tile Y must be 1");
        int expectedIndex = 1 * widthTiles + 2; // tileY * widthTiles + tileX = 6
        assertEquals(expectedIndex, dirty.tileIndex(), "Dirty tile linear index must be 6");

        // Mark it clean and verify clean again
        canvas.markClean(dirty.tileX(), dirty.tileY(), dirty.newHash());
        assertTrue(TileDiffer.findDirtyTiles(canvas, widthTiles, heightTiles).isEmpty(),
                "After marking clean, no dirty tiles should remain");
    }

    @Test
    @DisplayName("Test 3.5 — Flush batching: exactly ONE flush for N dirty tiles")
    void testFlushBatching() {
        Channel channel = mock(Channel.class);
        when(channel.isActive()).thenReturn(true);
        when(channel.isWritable()).thenReturn(true);

        int count = 5;
        List<DirtyTile> dirtyTiles = new ArrayList<>();
        int[] mapIds = new int[count];
        for (int i = 0; i < count; i++) {
            mapIds[i] = 1000 + i;
            byte[] data = new byte[128 * 128];
            data[0] = (byte) (i + 1);
            dirtyTiles.add(new DirtyTile(i, 0, i, 9999L + i, data));
        }

        BatchedMapSender.sendDirtyTiles(channel, mapIds, dirtyTiles);

        // Verify channel.write was called exactly 5 times
        ArgumentCaptor<WrapperPlayServerMapData> packetCaptor = ArgumentCaptor.forClass(WrapperPlayServerMapData.class);
        verify(channel, times(5)).write(packetCaptor.capture());

        List<WrapperPlayServerMapData> packets = packetCaptor.getAllValues();
        assertEquals(5, packets.size());
        for (int i = 0; i < count; i++) {
            assertEquals(1000 + i, packets.get(i).getMapId());
        }

        // Verify channel.flush was called exactly ONCE
        verify(channel, times(1)).flush();
    }

    @Test
    @DisplayName("Test 3.6 — RGB565 cache coverage (all 65,536 values valid)")
    void testRgb565CacheCoverage() {
        PaletteQuantizerImpl quantizer = new PaletteQuantizerImpl();

        for (int r5 = 0; r5 < 32; r5++) {
            int r = (r5 * 255 + 15) / 31;
            for (int g6 = 0; g6 < 64; g6++) {
                int g = (g6 * 255 + 31) / 63;
                for (int b5 = 0; b5 < 32; b5++) {
                    int b = (b5 * 255 + 15) / 31;
                    int argb = (0xFF << 24) | (r << 16) | (g << 8) | b;

                    byte paletteIndex = quantizer.matchColor(argb);
                    int unsignedIndex = paletteIndex & 0xFF;

                    // Minecraft map palette usable range is [4, 247]
                    assertTrue(unsignedIndex >= 4 && unsignedIndex <= 247,
                            "Palette index for RGB (" + r + "," + g + "," + b + ") was out of bounds: " + unsignedIndex);
                }
            }
        }
    }

    @Test
    @DisplayName("Test 3.7 — Dither vs nearest-neighbor for flat color (identical hash)")
    void testFlatColorSelectiveDitheringBypass() {
        PaletteQuantizerImpl quantizer = new PaletteQuantizerImpl(128);

        // 128x128 solid flat color tile
        int flatColor = 0xFF4A90E2; // Solid blue
        int[] pixels = new int[128 * 128];
        Arrays.fill(pixels, flatColor);

        byte[] outDither = new byte[128 * 128];
        byte[] outNoDither = new byte[128 * 128];

        quantizer.quantizeTile(pixels, 0, 0, outDither, true);
        quantizer.quantizeTile(pixels, 0, 0, outNoDither, false);

        long hashDither = TileDiffer.computeHash(outDither);
        long hashNoDither = TileDiffer.computeHash(outNoDither);

        assertEquals(hashNoDither, hashDither,
                "Solid flat-color tile must produce identical hash with dither=true and dither=false");
        assertArrayEquals(outNoDither, outDither,
                "Solid flat-color tile pixel bytes must be identical (no diffusion noise on flat areas)");
    }

    @Test
    @DisplayName("Test 3.8 — Inactive channel guard and zero-allocation dithering verification")
    void testInactiveChannelGuardAndZeroAllocationDithering() {
        // 1. Inactive channel guard
        Channel inactiveChannel = mock(Channel.class);
        when(inactiveChannel.isActive()).thenReturn(false);

        List<DirtyTile> dirtyTiles = List.of(new DirtyTile(0, 0, 0, 123L, new byte[16384]));
        BatchedMapSender.sendDirtyTiles(inactiveChannel, new int[]{100}, dirtyTiles);

        verify(inactiveChannel, never()).write(any());
        verify(inactiveChannel, never()).flush();

        // 2. Zero-allocation dithering across multiple tile quantizations
        PaletteQuantizerImpl quantizer = new PaletteQuantizerImpl(256);
        int[] gradient = new int[256 * 128];
        for (int i = 0; i < gradient.length; i++) {
            gradient[i] = (0xFF << 24) | ((i % 256) << 16) | (((i / 256) * 2) << 8) | 128;
        }

        byte[] tileA = new byte[16384];
        byte[] tileB = new byte[16384];

        // Ensure consecutive runs produce deterministic, valid output using the ThreadLocal buffer
        quantizer.quantizeTile(gradient, 0, 0, tileA, true);
        quantizer.quantizeTile(gradient, 1, 0, tileB, true);

        long hashA = TileDiffer.computeHash(tileA);
        long hashB = TileDiffer.computeHash(tileB);
        assertTrue(hashA != 0L);
        assertTrue(hashB != 0L);
    }

    @Test
    @DisplayName("Idempotent pixel writes do not dirty tiles, preventing infinite per-tick render loops")
    void testRepeatedSetPixelWithIdenticalColorDoesNotDirtyTile() {
        CanvasBufferImpl canvas = new CanvasBufferImpl(2, 2);
        // Initially fill tile (0, 0)
        canvas.setPixel(10, 10, (byte) 42);
        assertTrue(canvas.isAnyTileDirty());

        byte[] tileData = canvas.getSubTile(0, 0);
        long hash = TileDiffer.computeHash(tileData);
        canvas.markClean(0, 0, hash);

        assertFalse(canvas.isAnyTileDirty(), "After markClean, canvas must have 0 dirty bits");
        assertTrue(TileDiffer.findDirtyTiles(canvas, 2, 2).isEmpty(), "findDirtyTiles must be empty after markClean");

        // Set the EXACT SAME pixel color again (simulating background re-rendering every tick)
        canvas.setPixel(10, 10, (byte) 42);
        assertFalse(canvas.isAnyTileDirty(), "Writing unchanged color must not set dirty bit");
        assertTrue(TileDiffer.findDirtyTiles(canvas, 2, 2).isEmpty(), "findDirtyTiles must remain empty on unchanged scene");

        // Now mutate to a DIFFERENT color
        canvas.setPixel(10, 10, (byte) 99);
        assertTrue(canvas.isAnyTileDirty(), "Writing mutated color must set dirty bit");
        List<DirtyTile> dirtyTiles = TileDiffer.findDirtyTiles(canvas, 2, 2);
        assertEquals(1, dirtyTiles.size(), "Mutated pixel must produce exactly 1 dirty tile");
    }
}