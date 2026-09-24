package me.goosbanny.goosboards.interaction;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

/**
 * Token-bucket rate limiter tracking click and hover interactions per player.
 */
public class InteractionRateLimiter {

    private final int maxClicksPerSecond;
    private final int maxHoversPerSecond;
    private final LongSupplier nanoTimeSupplier;

    private final Cache<UUID, TokenBucket> clickBuckets = Caffeine.newBuilder()
            .expireAfterAccess(5, TimeUnit.MINUTES)
            .maximumSize(2000)
            .build();
    private final Cache<UUID, TokenBucket> hoverBuckets = Caffeine.newBuilder()
            .expireAfterAccess(5, TimeUnit.MINUTES)
            .maximumSize(2000)
            .build();

    public InteractionRateLimiter() {
        this(8, 20, System::nanoTime);
    }

    public InteractionRateLimiter(int maxClicksPerSecond, int maxHoversPerSecond) {
        this(maxClicksPerSecond, maxHoversPerSecond, System::nanoTime);
    }

    public InteractionRateLimiter(int maxClicksPerSecond, int maxHoversPerSecond, LongSupplier nanoTimeSupplier) {
        this.maxClicksPerSecond = Math.max(1, maxClicksPerSecond);
        this.maxHoversPerSecond = Math.max(1, maxHoversPerSecond);
        this.nanoTimeSupplier = Objects.requireNonNull(nanoTimeSupplier, "nanoTimeSupplier");
    }

    /**
     * Attempts to consume 1 click token for the given player.
     *
     * @param playerId the player UUID
     * @return true if permitted, false if rate limited
     */
    public boolean tryConsumeClick(UUID playerId) {
        return tryConsumeClick(playerId, 1);
    }

    /**
     * Attempts to consume N click tokens for the given player.
     *
     * @param playerId the player UUID
     * @param cost     the number of tokens to consume
     * @return true if permitted, false if rate limited
     */
    public boolean tryConsumeClick(UUID playerId, int cost) {
        if (playerId == null) {
            return false;
        }
        TokenBucket bucket = clickBuckets.get(
                playerId,
                id -> new TokenBucket(maxClicksPerSecond, maxClicksPerSecond, nanoTimeSupplier.getAsLong()));
        return bucket != null && bucket.tryConsume(cost, nanoTimeSupplier.getAsLong());
    }

    /**
     * Attempts to consume 1 hover token for the given player.
     *
     * @param playerId the player UUID
     * @return true if permitted, false if rate limited
     */
    public boolean tryConsumeHover(UUID playerId) {
        return tryConsumeHover(playerId, 1);
    }

    /**
     * Attempts to consume N hover tokens for the given player.
     *
     * @param playerId the player UUID
     * @param cost     the number of tokens to consume
     * @return true if permitted, false if rate limited
     */
    public boolean tryConsumeHover(UUID playerId, int cost) {
        if (playerId == null) {
            return false;
        }
        TokenBucket bucket = hoverBuckets.get(
                playerId,
                id -> new TokenBucket(maxHoversPerSecond, maxHoversPerSecond, nanoTimeSupplier.getAsLong()));
        return bucket != null && bucket.tryConsume(cost, nanoTimeSupplier.getAsLong());
    }

    /**
     * Generic consumption helper (defaults to click rate limit).
     */
    public boolean tryConsume(UUID playerId, int cost) {
        return tryConsumeClick(playerId, cost);
    }

    public void reset(UUID playerId) {
        if (playerId != null) {
            clickBuckets.invalidate(playerId);
            hoverBuckets.invalidate(playerId);
        }
    }

    public void clear() {
        clickBuckets.invalidateAll();
        hoverBuckets.invalidateAll();
    }

    /**
     * Internal zero-allocation thread-safe token bucket.
     */
    private static final class TokenBucket {
        private final double capacity;
        private final double refillRatePerSecond;
        private double tokens;
        private long lastRefillNanos;

        TokenBucket(double capacity, double refillRatePerSecond, long initialNanos) {
            this.capacity = capacity;
            this.refillRatePerSecond = refillRatePerSecond;
            this.tokens = capacity;
            this.lastRefillNanos = initialNanos;
        }

        synchronized boolean tryConsume(int cost, long nowNanos) {
            if (nowNanos > lastRefillNanos) {
                long elapsedNanos = nowNanos - lastRefillNanos;
                double refill = (elapsedNanos / 1_000_000_000.0) * refillRatePerSecond;
                tokens = Math.min(capacity, tokens + refill);
                lastRefillNanos = nowNanos;
            }

            if (tokens < cost) {
                return false;
            }

            tokens -= cost;
            return true;
        }
    }
}
