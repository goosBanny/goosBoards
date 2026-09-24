package me.goosbanny.goosboards.render;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.LongSupplier;

/**
 * Bounded, thread-safe allocator for Minecraft map IDs used in Item Frame
 * rendering.
 * <p>
 * Guards against double-free errors that could stomp client-side map buffers,
 * applies a time-based quarantine before ID recycling to ensure clients discard
 * old map contents, and bounds the recycling pool to prevent memory leaks
 * during rapid create/delete churn.
 */
public class MapIdAllocator {
    public static final int INITIAL_MAP_ID = 10_000;
    public static final int MAX_FREE_POOL = 50_000;
    public static final long DEFAULT_QUARANTINE_MS = 1_000L;

    private final AtomicInteger next = new AtomicInteger(INITIAL_MAP_ID);
    private final Set<Integer> allocatedIds = ConcurrentHashMap.newKeySet();
    private final LinkedBlockingDeque<Integer> freePool = new LinkedBlockingDeque<>(MAX_FREE_POOL);
    private final LinkedBlockingDeque<QuarantineEntry> quarantine = new LinkedBlockingDeque<>(MAX_FREE_POOL);
    private final long quarantineDurationMs;
    private final LongSupplier clock;

    private record QuarantineEntry(int id, long expiresAt) {
    }

    public MapIdAllocator() {
        this(0L, System::currentTimeMillis);
    }

    public MapIdAllocator(long quarantineDurationMs) {
        this(quarantineDurationMs, System::currentTimeMillis);
    }

    public MapIdAllocator(long quarantineDurationMs, LongSupplier clock) {
        this.quarantineDurationMs = Math.max(0L, quarantineDurationMs);
        this.clock = clock != null ? clock : System::currentTimeMillis;
    }

    /**
     * Allocate a fresh map ID. Reuses a previously freed ID if available and out of
     * quarantine.
     */
    public int allocate() {
        drainQuarantine();

        Integer reclaimed = freePool.pollFirst();
        if (reclaimed != null) {
            allocatedIds.add(reclaimed);
            return reclaimed;
        }
        int id = next.getAndIncrement();
        allocatedIds.add(id);
        return id;
    }

    /**
     * Return a map ID to the pool for reuse. Guarded against double-free.
     */
    public boolean free(int id) {
        if (!allocatedIds.remove(id)) {
            return false; // double-free guard
        }
        if (quarantineDurationMs <= 0) {
            freePool.offerLast(id); // bounded pool drops silently if saturated
        } else {
            quarantine.offerLast(new QuarantineEntry(id, clock.getAsLong() + quarantineDurationMs));
        }
        return true;
    }

    private void drainQuarantine() {
        if (quarantineDurationMs <= 0)
            return;
        long now = clock.getAsLong();
        QuarantineEntry entry;
        while ((entry = quarantine.peekFirst()) != null) {
            if (now >= entry.expiresAt()) {
                quarantine.pollFirst();
                freePool.offerLast(entry.id());
            } else {
                break;
            }
        }
    }

    public int getQuarantineSize() {
        return quarantine.size();
    }
}
