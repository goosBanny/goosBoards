package me.goosbanny.goosboards.protocol;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuarantinedIdAllocatorTest {

    @Test
    @DisplayName("Test 1 — Happy path allocation")
    void testHappyPathAllocation() {
        QuarantinedIdAllocator allocator = new QuarantinedIdAllocator();
        int first = allocator.allocate();
        int second = allocator.allocate();

        assertEquals(Integer.MAX_VALUE - 100_000, first);
        assertEquals(Integer.MAX_VALUE - 100_001, second);
    }

    @Test
    @DisplayName("Test 2 — No collision across N allocations")
    void testNoCollisionAcross10kAllocations() {
        QuarantinedIdAllocator allocator = new QuarantinedIdAllocator();
        int n = 10_000;
        Set<Integer> set = new HashSet<>(n);

        for (int i = 0; i < n; i++) {
            int id = allocator.allocate();
            set.add(id);
            assertTrue(id >= 1_000_000_000, "ID should be >= 1_000_000_000");
            assertTrue(id <= Integer.MAX_VALUE - 100_000, "ID should be <= Integer.MAX_VALUE - 100_000");
        }

        assertEquals(n, set.size(), "All 10,000 IDs must be distinct");
    }

    @Test
    @DisplayName("Test 3 — Quarantine hold (ID not reused before 30s)")
    void testQuarantineHold() {
        AtomicLong clock = new AtomicLong(0L);
        QuarantinedIdAllocator allocator = new QuarantinedIdAllocator(clock::get);

        int idX = allocator.allocate();
        allocator.free(idX);

        // Advance to T=15s (< 30s quarantine threshold)
        clock.set(15_000L);

        int nextId = allocator.allocate();
        assertNotEquals(idX, nextId, "ID X must not be reused before 30 seconds quarantine expires");
    }

    @Test
    @DisplayName("Test 4 — Quarantine expiry (ID recycled after 30s)")
    void testQuarantineExpiry() {
        AtomicLong clock = new AtomicLong(0L);
        QuarantinedIdAllocator allocator = new QuarantinedIdAllocator(clock::get);

        int idX = allocator.allocate();
        allocator.free(idX);

        // Advance to T=31s (> 30s quarantine threshold)
        clock.set(31_000L);

        int recycledId = allocator.allocate();
        assertEquals(idX, recycledId, "ID X should be recycled after quarantine period expires");
    }

    @Test
    @DisplayName("Test 5 — Overflow fallback (queue full)")
    void testOverflowFallback() {
        AtomicLong clock = new AtomicLong(0L);
        QuarantinedIdAllocator allocator = new QuarantinedIdAllocator(clock::get);

        // Pre-fill quarantine queue to capacity (50,000) with non-expired entries
        for (int i = 0; i < QuarantinedIdAllocator.QUARANTINE_CAPACITY; i++) {
            allocator.free(i + 1);
        }
        assertEquals(QuarantinedIdAllocator.QUARANTINE_CAPACITY, allocator.getQueueSize());

        // Freeing another ID when full should return normally and discard
        int overflowId = 999_999;
        assertDoesNotThrow(() -> allocator.free(overflowId));
        assertEquals(QuarantinedIdAllocator.QUARANTINE_CAPACITY, allocator.getQueueSize());

        // Subsequent allocate with non-expired queue should allocate fresh decremented ID
        int allocated = allocator.allocate();
        assertEquals(Integer.MAX_VALUE - 100_000, allocated);
    }

    @Test
    @DisplayName("Test 6 — ID range lower bound guard")
    void testLowerBoundGuard() {
        AtomicLong clock = new AtomicLong(0L);
        QuarantinedIdAllocator allocator = new QuarantinedIdAllocator(clock::get, 1_000_000_001);

        int first = allocator.allocate();
        assertEquals(1_000_000_001, first);

        IllegalStateException ex = assertThrows(IllegalStateException.class, allocator::allocate);
        assertTrue(ex.getMessage().contains("Virtual entity ID space exhausted"));
    }

    @Test
    @DisplayName("Test 7 — Thread safety")
    void testThreadSafety() throws InterruptedException {
        QuarantinedIdAllocator allocator = new QuarantinedIdAllocator();
        int threadCount = 64;
        int perThread = 1_000;
        int totalExpected = threadCount * perThread;

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        ConcurrentHashMap<Integer, Boolean> ids = new ConcurrentHashMap<>(totalExpected);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        for (int t = 0; t < threadCount; t++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    for (int i = 0; i < perThread; i++) {
                        int id = allocator.allocate();
                        ids.put(id, Boolean.TRUE);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        boolean completed = doneLatch.await(15, TimeUnit.SECONDS);
        executor.shutdown();

        assertTrue(completed, "All threads should finish within timeout");
        assertEquals(totalExpected, ids.size(), "Zero duplicates across 64,000 allocations in 64 threads");
    }

    @Test
    @DisplayName("Test 8 — Double free protection")
    void testDoubleFreeProtection() {
        AtomicLong clock = new AtomicLong(0L);
        QuarantinedIdAllocator allocator = new QuarantinedIdAllocator(clock::get);

        int id = allocator.allocate();
        allocator.free(id);
        int sizeBefore = allocator.getQueueSize();
        allocator.free(id); // Duplicate free
        int sizeAfter = allocator.getQueueSize();

        assertEquals(sizeBefore, sizeAfter, "Double-free must not add duplicate entries to the quarantine queue");
    }
}
