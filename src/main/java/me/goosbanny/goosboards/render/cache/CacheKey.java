package me.goosbanny.goosboards.render.cache;

/**
 * Immutable cache key for rendered tile deduplication across viewers.
 *
 * @param boardId           the board identifier
 * @param sceneId           the scene identifier
 * @param resolvedStateHash hash of resolved placeholder values (0L if context-independent)
 * @param tileX             the X tile coordinate
 * @param tileY             the Y tile coordinate
 */
public record CacheKey(String boardId, String sceneId, long resolvedStateHash, int tileX, int tileY) {}
