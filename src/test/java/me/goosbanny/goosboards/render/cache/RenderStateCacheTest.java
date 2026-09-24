package me.goosbanny.goosboards.render.cache;

import me.goosbanny.goosboards.render.cache.impl.RenderStateCacheImpl;

import net.openhft.hashing.LongHashFunction;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

class RenderStateCacheTest {

    @Test
    @DisplayName("Test 5.5: Two players with identical resolved state share one render")
    void testTwoPlayersIdenticalStateShareRender() {
        RenderStateCacheImpl cache = new RenderStateCacheImpl();
        String boardId = "main-board";
        String sceneId = "hub";

        // Both players have identical resolved state (e.g., same rank, same placeholders)
        String stateA = "rank:VIP|balance:500";
        String stateB = "rank:VIP|balance:500";

        long hashA = LongHashFunction.xx().hashBytes(stateA.getBytes(StandardCharsets.UTF_8));
        long hashB = LongHashFunction.xx().hashBytes(stateB.getBytes(StandardCharsets.UTF_8));
        assertEquals(hashA, hashB, "Identical states must produce identical hashes");

        AtomicInteger renderCount = new AtomicInteger(0);
        Supplier<byte[]> renderFunction = () -> {
            renderCount.incrementAndGet();
            byte[] tile = new byte[128 * 128];
            tile[0] = 42; // simulated rendered pixel
            return tile;
        };

        // Player A: check cache, miss, render, store
        int tileX = 0, tileY = 0;
        Optional<byte[]> cachedA = cache.getCachedTile(boardId, sceneId, hashA, tileX, tileY);
        assertTrue(cachedA.isEmpty(), "Player A should miss cache initially");
        byte[] rendered = renderFunction.get();
        cache.putCachedTile(boardId, sceneId, hashA, tileX, tileY, rendered);

        // Player B: check cache with their resolvedStateHash -> HIT!
        Optional<byte[]> cachedB = cache.getCachedTile(boardId, sceneId, hashB, tileX, tileY);
        assertTrue(cachedB.isPresent(), "Player B should hit cache with same resolvedStateHash");
        assertArrayEquals(rendered, cachedB.get());

        // Assert render function was called exactly ONCE
        assertEquals(1, renderCount.get(), "Render function must only be called once");
    }

    @Test
    @DisplayName("Test 5.6: Different resolved state produces separate cache entries")
    void testDifferentResolvedStateProducesSeparateEntries() {
        RenderStateCacheImpl cache = new RenderStateCacheImpl();
        String boardId = "spawn-board";
        String sceneId = "profile";

        String stateAlice = "%player_name%=Alice";
        String stateBob = "%player_name%=Bob";

        long hashAlice = LongHashFunction.xx().hashBytes(stateAlice.getBytes(StandardCharsets.UTF_8));
        long hashBob = LongHashFunction.xx().hashBytes(stateBob.getBytes(StandardCharsets.UTF_8));
        assertNotEquals(hashAlice, hashBob, "Different states must produce distinct hashes");

        AtomicInteger renderCount = new AtomicInteger(0);

        // Render Alice
        Optional<byte[]> cachedAlice = cache.getCachedTile(boardId, sceneId, hashAlice, 0, 0);
        assertTrue(cachedAlice.isEmpty());
        renderCount.incrementAndGet();
        byte[] tileAlice = new byte[128 * 128];
        tileAlice[0] = 1;
        cache.putCachedTile(boardId, sceneId, hashAlice, 0, 0, tileAlice);

        // Render Bob
        Optional<byte[]> cachedBob = cache.getCachedTile(boardId, sceneId, hashBob, 0, 0);
        assertTrue(cachedBob.isEmpty(), "Bob must miss cache because resolvedStateHash differs");
        renderCount.incrementAndGet();
        byte[] tileBob = new byte[128 * 128];
        tileBob[0] = 2;
        cache.putCachedTile(boardId, sceneId, hashBob, 0, 0, tileBob);

        assertEquals(2, renderCount.get(), "Two separate render calls required for different states");
        assertEquals(1, cache.getCachedTile(boardId, sceneId, hashAlice, 0, 0).orElseThrow()[0]);
        assertEquals(2, cache.getCachedTile(boardId, sceneId, hashBob, 0, 0).orElseThrow()[0]);
    }

    @Test
    @DisplayName("Weight eviction honors maximum memory bytes limit")
    void testWeightEviction() {
        // Tile is 16384 bytes. Set limit to 30000 bytes (fits 1 tile, evicts on 2nd)
        long maxWeight = 30_000L;
        RenderStateCacheImpl cache = new RenderStateCacheImpl(maxWeight, Duration.ofMinutes(10));

        byte[] tile1 = new byte[16384];
        byte[] tile2 = new byte[16384];
        byte[] tile3 = new byte[16384];

        cache.putCachedTile("b", "s", 1L, 0, 0, tile1);
        cache.putCachedTile("b", "s", 2L, 0, 0, tile2);
        cache.putCachedTile("b", "s", 3L, 0, 0, tile3);

        // Force Caffeine maintenance
        cache.cleanUp();
        assertTrue(cache.estimatedSize() <= 2, "Cache size should not exceed weight capacity");
    }

    @Test
    @DisplayName("Invalidate board and scene methods work")
    void testInvalidation() {
        RenderStateCacheImpl cache = new RenderStateCacheImpl();
        byte[] tile = new byte[100];
        cache.putCachedTile("b1", "s1", 0L, 0, 0, tile);
        cache.putCachedTile("b1", "s2", 0L, 0, 0, tile);
        cache.putCachedTile("b2", "s1", 0L, 0, 0, tile);

        cache.invalidateScene("b1", "s1");
        assertTrue(cache.getCachedTile("b1", "s1", 0L, 0, 0).isEmpty());
        assertTrue(cache.getCachedTile("b1", "s2", 0L, 0, 0).isPresent());
        assertTrue(cache.getCachedTile("b2", "s1", 0L, 0, 0).isPresent());

        cache.invalidateBoard("b1");
        assertTrue(cache.getCachedTile("b1", "s2", 0L, 0, 0).isEmpty());
        assertTrue(cache.getCachedTile("b2", "s1", 0L, 0, 0).isPresent());

        cache.invalidateAll();
        assertTrue(cache.getCachedTile("b2", "s1", 0L, 0, 0).isEmpty());
    }
}
