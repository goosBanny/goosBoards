package me.goosbanny.goosboards.core.metrics;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.DoubleSupplier;

/**
 * Thread-safe real-time performance metrics collector for boards, rendering, and network output.
 */
public class MetricsCollector {

    private static final int ROLLING_WINDOW_SIZE = 100;

    private final Map<String, BoardMetrics> boardMetricsMap = new ConcurrentHashMap<>();
    private final AtomicLong globalPacketsSent = new AtomicLong(0);
    private final ConcurrentLinkedDeque<Long> packetTimestamps = new ConcurrentLinkedDeque<>();
    private DoubleSupplier cacheHitRatioSupplier = () -> 0.0;

    public void setCacheHitRatioSupplier(DoubleSupplier supplier) {
        this.cacheHitRatioSupplier = supplier != null ? supplier : () -> 0.0;
    }

    public void recordRenderTime(String boardId, long durationNanos) {
        getBoardMetrics(boardId).recordRenderNanos(durationNanos);
    }

    public void recordPacketsSent(String boardId, int packetCount) {
        if (packetCount <= 0) return;
        getBoardMetrics(boardId).recordPackets(packetCount);
        globalPacketsSent.addAndGet(packetCount);

        long now = System.currentTimeMillis();
        for (int i = 0; i < packetCount; i++) {
            packetTimestamps.add(now);
        }
        purgeOldTimestamps(now);
    }

    public void setActiveViewers(String boardId, int count) {
        getBoardMetrics(boardId).setActiveViewers(count);
    }

    public int getActiveViewers(String boardId) {
        return getBoardMetrics(boardId).getActiveViewers();
    }

    public double getPacketsPerSecond() {
        long now = System.currentTimeMillis();
        purgeOldTimestamps(now);
        return packetTimestamps.size();
    }

    public double getCacheHitRatio() {
        return cacheHitRatioSupplier.getAsDouble();
    }

    public BoardMetrics getBoardMetrics(String boardId) {
        return boardMetricsMap.computeIfAbsent(boardId, k -> new BoardMetrics());
    }

    private void purgeOldTimestamps(long now) {
        long threshold = now - 1000L;
        while (!packetTimestamps.isEmpty()) {
            Long first = packetTimestamps.peekFirst();
            if (first != null && first < threshold) {
                packetTimestamps.pollFirst();
            } else {
                break;
            }
        }
    }

    public static class BoardMetrics {
        private final ConcurrentLinkedDeque<Long> renderDurationsNanos = new ConcurrentLinkedDeque<>();
        private final AtomicInteger activeViewers = new AtomicInteger(0);
        private final AtomicLong totalFrames = new AtomicLong(0);
        private final AtomicLong totalPackets = new AtomicLong(0);

        public void recordRenderNanos(long nanos) {
            renderDurationsNanos.addLast(nanos);
            totalFrames.incrementAndGet();
            while (renderDurationsNanos.size() > ROLLING_WINDOW_SIZE) {
                renderDurationsNanos.pollFirst();
            }
        }

        public void recordPackets(int count) {
            totalPackets.addAndGet(count);
        }

        public void setActiveViewers(int viewers) {
            this.activeViewers.set(Math.max(0, viewers));
        }

        public int getActiveViewers() {
            return activeViewers.get();
        }

        public long getTotalFrames() {
            return totalFrames.get();
        }

        public long getTotalPackets() {
            return totalPackets.get();
        }

        public double getMinRenderLatencyMs() {
            return renderDurationsNanos.stream()
                    .mapToLong(Long::longValue)
                    .min()
                    .orElse(0L) / 1_000_000.0;
        }

        public double getMaxRenderLatencyMs() {
            return renderDurationsNanos.stream()
                    .mapToLong(Long::longValue)
                    .max()
                    .orElse(0L) / 1_000_000.0;
        }

        public double getAvgRenderLatencyMs() {
            return renderDurationsNanos.stream()
                    .mapToLong(Long::longValue)
                    .average()
                    .orElse(0.0) / 1_000_000.0;
        }
    }
}
