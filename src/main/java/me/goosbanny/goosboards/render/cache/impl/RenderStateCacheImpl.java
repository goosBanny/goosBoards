package me.goosbanny.goosboards.render.cache.impl;

import me.goosbanny.goosboards.render.cache.CacheKey;
import me.goosbanny.goosboards.render.cache.RenderStateCache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

/**
 * Caffeine-backed implementation of RenderStateCache with memory-weighted eviction
 * and time-to-idle expiration.
 */
public class RenderStateCacheImpl implements RenderStateCache {

    public static final long DEFAULT_MAX_MEMORY_BYTES = 128L * 1024L * 1024L; // 128 MB
    public static final Duration DEFAULT_EXPIRE_AFTER_ACCESS = Duration.ofMinutes(10);

    private final Cache<CacheKey, byte[]> cache;
    private final long maxMemoryBytes;
    private final Duration expireAfterAccess;

    public RenderStateCacheImpl() {
        this(DEFAULT_MAX_MEMORY_BYTES, DEFAULT_EXPIRE_AFTER_ACCESS);
    }

    public RenderStateCacheImpl(long maxMemoryBytes, Duration expireAfterAccess) {
        this.maxMemoryBytes = Math.max(1024L, maxMemoryBytes);
        this.expireAfterAccess = Objects.requireNonNullElse(expireAfterAccess, DEFAULT_EXPIRE_AFTER_ACCESS);
        this.cache = Caffeine.newBuilder()
                .maximumWeight(this.maxMemoryBytes)
                .weigher((CacheKey key, byte[] value) -> value != null ? value.length : 0)
                .expireAfterAccess(this.expireAfterAccess)
                .recordStats()
                .build();
    }

    public double getHitRate() {
        return cache.stats().hitRate();
    }


    @Override
    public Optional<byte[]> getCachedTile(String boardId, String sceneId, long resolvedStateHash, int tileX, int tileY) {
        CacheKey key = new CacheKey(boardId, sceneId, resolvedStateHash, tileX, tileY);
        byte[] bytes = cache.getIfPresent(key);
        return Optional.ofNullable(bytes);
    }

    @Override
    public void putCachedTile(String boardId, String sceneId, long resolvedStateHash, int tileX, int tileY, byte[] tileBytes) {
        if (tileBytes != null) {
            CacheKey key = new CacheKey(boardId, sceneId, resolvedStateHash, tileX, tileY);
            cache.put(key, tileBytes);
        }
    }

    public void invalidateBoard(String boardId) {
        if (boardId != null) {
            cache.asMap().keySet().removeIf(k -> boardId.equals(k.boardId()));
        }
    }

    public void invalidateScene(String boardId, String sceneId) {
        if (boardId != null && sceneId != null) {
            cache.asMap().keySet().removeIf(k -> boardId.equals(k.boardId()) && sceneId.equals(k.sceneId()));
        }
    }

    public void invalidateAll() {
        cache.invalidateAll();
    }

    public void cleanUp() {
        cache.cleanUp();
    }

    public long estimatedSize() {
        return cache.estimatedSize();
    }

    public long getMaxMemoryBytes() {
        return maxMemoryBytes;
    }

    public Duration getExpireAfterAccess() {
        return expireAfterAccess;
    }
}
