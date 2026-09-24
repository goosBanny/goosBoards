package me.goosbanny.goosboards.render;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

class MapIdAllocatorTest {

    @Test
    @DisplayName("Sequential allocation and double-free protection")
    void testAllocationAndDoubleFree() {
        MapIdAllocator allocator = new MapIdAllocator();

        int id1 = allocator.allocate();
        int id2 = allocator.allocate();
        assertEquals(10_000, id1);
        assertEquals(10_001, id2);

        // Free id1
        assertTrue(allocator.free(id1));
        // Double-free of id1 MUST be rejected
        assertFalse(allocator.free(id1));

        // Freeing unallocated id MUST be rejected
        assertFalse(allocator.free(999_999));

        // Next allocation reuses id1
        int id3 = allocator.allocate();
        assertEquals(id1, id3);

        // Next allocation continues sequence
        int id4 = allocator.allocate();
        assertEquals(10_002, id4);
    }

    @Test
    @DisplayName("Concurrent allocations produce strictly unique IDs")
    void testConcurrentAllocations() throws InterruptedException {
        MapIdAllocator allocator = new MapIdAllocator();
        int count = 1000;
        Set<Integer> ids = Collections.synchronizedSet(new HashSet<>());
        ExecutorService executor = Executors.newFixedThreadPool(16);
        CountDownLatch latch = new CountDownLatch(count);

        for (int i = 0; i < count; i++) {
            executor.submit(() -> {
                try {
                    ids.add(allocator.allocate());
                } finally {
                    latch.countDown();
                }
            });
        }

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        executor.shutdown();
        assertEquals(count, ids.size(), "All allocated map IDs must be unique");
    }

    @Test
    @DisplayName("Quarantined IDs are not reallocated until quarantine expires")
    void testQuarantineDuration() {
        AtomicLong mockTime = new AtomicLong(1000L);
        MapIdAllocator allocator = new MapIdAllocator(2000L, mockTime::get);

        int id1 = allocator.allocate();
        assertEquals(10_000, id1);

        assertTrue(allocator.free(id1));
        assertEquals(1, allocator.getQuarantineSize());

        // At time 1500 (before 2000ms expires at 3000), id1 must NOT be reallocated
        mockTime.set(1500L);
        int id2 = allocator.allocate();
        assertEquals(10_001, id2);

        // At time 3500 (after expiration), id1 is reclaimed
        mockTime.set(3500L);
        int id3 = allocator.allocate();
        assertEquals(id1, id3);
        assertEquals(0, allocator.getQuarantineSize());
    }
}
