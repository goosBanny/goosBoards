package me.goosbanny.goosboards.render.cache;

import java.util.Optional;

/**
 * Shared and contextual render cache interface per PROJECT.md §5.
 */
public interface RenderStateCache {

    /**
     * Retrieves a cached tile for the given board, scene, resolved state, and tile coordinates.
     *
     * @param boardId           the board ID
     * @param sceneId           the scene ID
     * @param resolvedStateHash state hash (0L if context-independent, or XXHash64 of resolved values)
     * @param tileX             the X tile coordinate
     * @param tileY             the Y tile coordinate
     * @return Optional containing the cached tile bytes if present
     */
    Optional<byte[]> getCachedTile(String boardId, String sceneId, long resolvedStateHash, int tileX, int tileY);

    /**
     * Stores a rendered tile into the cache.
     *
     * @param boardId           the board ID
     * @param sceneId           the scene ID
     * @param resolvedStateHash state hash
     * @param tileX             the X tile coordinate
     * @param tileY             the Y tile coordinate
     * @param tileBytes         the 128x128 palette-indexed tile byte array
     */
    void putCachedTile(String boardId, String sceneId, long resolvedStateHash, int tileX, int tileY, byte[] tileBytes);

    /**
     * Invalidates all cached tiles for a specific board.
     */
    void invalidateBoard(String boardId);

    /**
     * Invalidates all cached tiles for a specific scene on a board.
     */
    void invalidateScene(String boardId, String sceneId);

    /**
     * Invalidates all entries across all boards and scenes.
     */
    void invalidateAll();
}

