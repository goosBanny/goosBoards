package me.goosbanny.goosboards.protocol;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;

import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.LongSupplier;
import java.util.logging.Logger;

public class QuarantinedIdAllocator {
    private static final Logger LOGGER = Logger.getLogger(QuarantinedIdAllocator.class.getName());
    public static final int INITIAL_COUNTER_DEFAULT = Integer.MAX_VALUE - 100_000;
    public static final int LOWER_BOUND_GUARD = 1_000_000_000;
    public static final int QUARANTINE_CAPACITY = 50_000;
    public static final long QUARANTINE_DURATION_MS = 30_000L;

    private final AtomicInteger counter;
    private final LinkedBlockingDeque<QuarantineEntry> quarantine;
    private final IntOpenHashSet quarantineSet;
    private final LongSupplier clock;

    private record QuarantineEntry(int id, long expiresAt) {}

    public QuarantinedIdAllocator() {
        this(System::currentTimeMillis, INITIAL_COUNTER_DEFAULT);
    }

    public QuarantinedIdAllocator(LongSupplier clock) {
        this(clock, INITIAL_COUNTER_DEFAULT);
    }

    public QuarantinedIdAllocator(LongSupplier clock, int initialCounter) {
        this.clock = clock;
        this.counter = new AtomicInteger(initialCounter);
        this.quarantine = new LinkedBlockingDeque<>(QUARANTINE_CAPACITY);
        this.quarantineSet = new IntOpenHashSet(QUARANTINE_CAPACITY);
    }

    public int allocate() {
        synchronized (quarantine) {
            QuarantineEntry entry = quarantine.peekFirst();
            if (entry != null && clock.getAsLong() >= entry.expiresAt()) {
                quarantine.pollFirst();
                quarantineSet.remove(entry.id());
                return entry.id();
            }
        }

        int current, next;
        do {
            current = counter.get();
            if (current <= LOWER_BOUND_GUARD) {
                throw new IllegalStateException("Virtual entity ID space exhausted (current=" + current + ")");
            }
            next = current - 1;
        } while (!counter.compareAndSet(current, next));
        return current;
    }

    public void free(int id) {
        long expiresAt = clock.getAsLong() + QUARANTINE_DURATION_MS;
        synchronized (quarantine) {
            if (!quarantineSet.add(id)) {
                return; // Guard against double-free recycling in O(1)
            }
            boolean offered = quarantine.offer(new QuarantineEntry(id, expiresAt));
            if (!offered) {
                quarantineSet.remove(id);
                LOGGER.fine(() -> "Quarantine queue full, discarded ID: " + id);
            }
        }
    }

    public int getQueueSize() {
        return quarantine.size();
    }
}
