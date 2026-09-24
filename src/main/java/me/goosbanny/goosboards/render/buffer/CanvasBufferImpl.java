package me.goosbanny.goosboards.render.buffer;

import java.util.Arrays;

/**
 * High-performance double-buffered CanvasBuffer managing an array of
 * CanvasTiles.
 * Utilizes a primitive bitmask (long) for dirty tile tracking, eliminating all
 * collection
 * allocations during steady-state rendering passes.
 */
public class CanvasBufferImpl implements CanvasBuffer {
    public static final int TILE_SIZE = 128;
    public static final int TILE_PIXELS = TILE_SIZE * TILE_SIZE;

    private final int widthTiles;
    private final int heightTiles;
    private final int totalTiles;
    private final CanvasTile[] tiles;

    // Primitive bitmask for up to 64 tiles (e.g. 8x8 display).
    // Concurrency invariant: Modifying operations (draw/clear/markClean) on a
    // CanvasBuffer
    // instance are confined to a single render thread per tick. When per-viewer
    // rendering is active,
    // each viewer owns an isolated CanvasBufferImpl instance. Bitmask mutations are
    // non-atomic and
    // not thread-safe across concurrent writers.
    private long dirtyMask = 0L;
    // Fallback bitmask array for displays exceeding 64 tiles
    private final long[] dirtyMasks;

    public CanvasBufferImpl(int widthTiles, int heightTiles) {
        if (widthTiles <= 0 || heightTiles <= 0) {
            throw new IllegalArgumentException("Tile dimensions must be positive: " + widthTiles + "x" + heightTiles);
        }
        this.widthTiles = widthTiles;
        this.heightTiles = heightTiles;
        this.totalTiles = widthTiles * heightTiles;
        this.tiles = new CanvasTile[totalTiles];
        for (int i = 0; i < totalTiles; i++) {
            this.tiles[i] = new CanvasTile();
        }
        this.dirtyMasks = totalTiles > 64 ? new long[(totalTiles + 63) / 64] : null;
    }

    @Override
    public int getWidth() {
        return widthTiles * TILE_SIZE;
    }

    @Override
    public int getHeight() {
        return heightTiles * TILE_SIZE;
    }

    public int getWidthTiles() {
        return widthTiles;
    }

    public int getHeightTiles() {
        return heightTiles;
    }

    public int getTotalTiles() {
        return totalTiles;
    }

    @Override
    public void setPixel(int x, int y, byte colorIndex) {
        if (x < 0 || x >= getWidth() || y < 0 || y >= getHeight()) {
            return;
        }
        int tileX = x / TILE_SIZE;
        int tileY = y / TILE_SIZE;
        int localX = x % TILE_SIZE;
        int localY = y % TILE_SIZE;
        int index = tileY * widthTiles + tileX;

        CanvasTile tile = tiles[index];
        if (tile.setPixel(localX, localY, colorIndex)) {
            markTileDirtyBit(index);
        }
    }

    public byte getPixel(int x, int y) {
        if (x < 0 || x >= getWidth() || y < 0 || y >= getHeight()) {
            return 0;
        }
        int tileX = x / TILE_SIZE;
        int tileY = y / TILE_SIZE;
        int localX = x % TILE_SIZE;
        int localY = y % TILE_SIZE;
        return tiles[tileY * widthTiles + tileX].getPixel(localX, localY);
    }

    @Override
    public byte[] getSubTile(int tileX, int tileY) {
        if (tileX < 0 || tileX >= widthTiles || tileY < 0 || tileY >= heightTiles) {
            throw new IndexOutOfBoundsException("Tile index out of bounds: (" + tileX + ", " + tileY + ")");
        }
        return tiles[tileY * widthTiles + tileX].getBackBuffer();
    }

    @Override
    public byte[] getFrontTile(int tileX, int tileY) {
        if (tileX < 0 || tileX >= widthTiles || tileY < 0 || tileY >= heightTiles) {
            throw new IndexOutOfBoundsException("Tile index out of bounds: (" + tileX + ", " + tileY + ")");
        }
        return tiles[tileY * widthTiles + tileX].getFrontBuffer();
    }

    @Override
    public CanvasTile getCanvasTile(int tileX, int tileY) {
        if (tileX < 0 || tileX >= widthTiles || tileY < 0 || tileY >= heightTiles) {
            return null;
        }
        return tiles[tileY * widthTiles + tileX];
    }

    public CanvasTile getCanvasTile(int tileIndex) {
        if (tileIndex < 0 || tileIndex >= totalTiles) {
            return null;
        }
        return tiles[tileIndex];
    }

    public void setSubTile(int tileX, int tileY, byte[] data) {
        if (tileX < 0 || tileX >= widthTiles || tileY < 0 || tileY >= heightTiles) {
            throw new IndexOutOfBoundsException("Tile index out of bounds: (" + tileX + ", " + tileY + ")");
        }
        int index = tileY * widthTiles + tileX;
        tiles[index].setSubTileData(data);
        markTileDirtyBit(index);
    }

    @Override
    public boolean isTileDirty(int tileX, int tileY, long contentHash) {
        if (tileX < 0 || tileX >= widthTiles || tileY < 0 || tileY >= heightTiles) {
            return false;
        }
        int index = tileY * widthTiles + tileX;
        return tiles[index].getLastContentHash() != contentHash;
    }

    @Override
    public void markClean(int tileX, int tileY, long newContentHash) {
        if (tileX < 0 || tileX >= widthTiles || tileY < 0 || tileY >= heightTiles) {
            return;
        }
        int index = tileY * widthTiles + tileX;
        CanvasTile tile = tiles[index];
        tile.setLastContentHash(newContentHash);
        tile.swap();
        clearTileDirtyBit(index);
    }

    public void markTileDirtyBit(int index) {
        if (index < 64) {
            dirtyMask |= (1L << index);
        } else if (dirtyMasks != null) {
            dirtyMasks[index / 64] |= (1L << (index % 64));
        }
    }

    public void clearTileDirtyBit(int index) {
        if (index < 64) {
            dirtyMask &= ~(1L << index);
        } else if (dirtyMasks != null) {
            dirtyMasks[index / 64] &= ~(1L << (index % 64));
        }
    }

    public boolean isTileDirtyBit(int index) {
        if (index < 64) {
            return (dirtyMask & (1L << index)) != 0;
        } else if (dirtyMasks != null) {
            return (dirtyMasks[index / 64] & (1L << (index % 64))) != 0;
        }
        return false;
    }

    public long getDirtyMask() {
        return dirtyMask;
    }

    public boolean isAnyTileDirty() {
        if (dirtyMask != 0L)
            return true;
        if (dirtyMasks != null) {
            for (long mask : dirtyMasks) {
                if (mask != 0L)
                    return true;
            }
        }
        return false;
    }

    /**
     * Clears all tiles to zero, resets last hashes, and marks all tiles dirty.
     */
    public void clear() {
        for (int i = 0; i < totalTiles; i++) {
            tiles[i].clear();
        }
        markAllTilesDirty();
    }

    /**
     * Marks specific rectangular pixel region dirty by invalidating the
     * intersecting tiles.
     */
    public void markRegionDirty(int x, int y, int width, int height) {
        if (width <= 0 || height <= 0)
            return;
        int startTileX = Math.max(0, x / TILE_SIZE);
        int endTileX = Math.min(widthTiles - 1, (x + width - 1) / TILE_SIZE);
        int startTileY = Math.max(0, y / TILE_SIZE);
        int endTileY = Math.min(heightTiles - 1, (y + height - 1) / TILE_SIZE);

        for (int ty = startTileY; ty <= endTileY; ty++) {
            for (int tx = startTileX; tx <= endTileX; tx++) {
                int index = ty * widthTiles + tx;
                tiles[index].markFullDirty();
                tiles[index].setLastContentHash(0L);
                markTileDirtyBit(index);
            }
        }
    }

    /**
     * Sets dirty tracking bits and forces full re-hash for all tiles on this
     * canvas.
     */
    public void markAllTilesDirty() {
        if (totalTiles <= 64) {
            this.dirtyMask = (totalTiles == 64) ? -1L : ((1L << totalTiles) - 1L);
        } else {
            this.dirtyMask = -1L;
            if (dirtyMasks != null) {
                Arrays.fill(dirtyMasks, -1L);
            }
        }
        for (int i = 0; i < totalTiles; i++) {
            tiles[i].markFullDirty();
            tiles[i].setLastContentHash(0L);
        }
    }

    /**
     * High-speed tile-aware raster blitter.
     * Direct buffer copy into intersecting CanvasTiles, skipping coordinate
     * division loops.
     */
    @Override
    public void blitRaster(int originX, int originY, int rasterW, int rasterH, byte[] raster) {
        if (raster == null || rasterW <= 0 || rasterH <= 0)
            return;
        int canvasW = getWidth();
        int canvasH = getHeight();
        if (originX >= canvasW || originY >= canvasH || originX + rasterW <= 0 || originY + rasterH <= 0) {
            return;
        }

        int startY = Math.max(0, originY);
        int endY = Math.min(canvasH, originY + rasterH);
        int startX = Math.max(0, originX);
        int endX = Math.min(canvasW, originX + rasterW);

        int startTileY = startY / TILE_SIZE;
        int endTileY = (endY - 1) / TILE_SIZE;
        int startTileX = startX / TILE_SIZE;
        int endTileX = (endX - 1) / TILE_SIZE;

        for (int ty = startTileY; ty <= endTileY; ty++) {
            int tileMinScreenY = ty * TILE_SIZE;
            int rowStartInTile = Math.max(0, startY - tileMinScreenY);
            int rowEndInTile = Math.min(TILE_SIZE, endY - tileMinScreenY);

            for (int tx = startTileX; tx <= endTileX; tx++) {
                int tileMinScreenX = tx * TILE_SIZE;
                int colStartInTile = Math.max(0, startX - tileMinScreenX);
                int colEndInTile = Math.min(TILE_SIZE, endX - tileMinScreenX);
                int sliceWidth = colEndInTile - colStartInTile;
                if (sliceWidth <= 0)
                    continue;

                int tileIndex = ty * widthTiles + tx;
                CanvasTile tile = tiles[tileIndex];
                byte[] back = tile.getBackBuffer();
                boolean modifiedTile = false;

                for (int localY = rowStartInTile; localY < rowEndInTile; localY++) {
                    int screenY = tileMinScreenY + localY;
                    int rasterY = screenY - originY;
                    int rasterRowOffset = rasterY * rasterW;
                    int screenXStart = tileMinScreenX + colStartInTile;
                    int rasterXStart = screenXStart - originX;
                    int tileRowOffset = localY * TILE_SIZE + colStartInTile;

                    for (int i = 0; i < sliceWidth; i++) {
                        byte p = raster[rasterRowOffset + rasterXStart + i];
                        if (p != 0) {
                            int dstIdx = tileRowOffset + i;
                            if (back[dstIdx] != p) {
                                back[dstIdx] = p;
                                modifiedTile = true;
                            }
                        }
                    }
                }

                if (modifiedTile) {
                    tile.markDirtyRegion(colStartInTile, rowStartInTile, colEndInTile - 1, rowEndInTile - 1);
                    markTileDirtyBit(tileIndex);
                }
            }
        }
    }
}
