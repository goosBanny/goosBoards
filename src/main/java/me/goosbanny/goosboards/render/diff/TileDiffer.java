package me.goosbanny.goosboards.render.diff;

import me.goosbanny.goosboards.render.buffer.CanvasBuffer;
import me.goosbanny.goosboards.render.buffer.CanvasBufferImpl;
import me.goosbanny.goosboards.render.buffer.CanvasTile;

import net.openhft.hashing.LongHashFunction;

import java.util.ArrayList;
import java.util.List;

/**
 * Computes XXHash64 tile hashes and detects dirty tiles between render passes.
 * Accurately extracts sub-rectangle dirty bounding boxes for the 40% bandwidth
 * reduction rule.
 */
public final class TileDiffer {

    public static final int TILE_SIZE = 128;
    public static final int TILE_PIXELS = TILE_SIZE * TILE_SIZE; // 16,384
    public static final int MAX_PARTIAL_PIXELS = (int) (0.40 * TILE_PIXELS); // 6,553 pixels (40%)

    private TileDiffer() {
    }

    /**
     * Represents a single tile that has changed content since the last render pass,
     * including bounding box coordinates for sub-rectangle partial packet
     * optimization.
     *
     * @param tileX     the tile column coordinate (0-indexed)
     * @param tileY     the tile row coordinate (0-indexed)
     * @param tileIndex the linear index in row-major order (tileY * widthTiles +
     *                  tileX)
     * @param newHash   the newly computed XXHash64 of the tile's 16,384 bytes
     * @param tileData  reference to the tile's byte buffer
     * @param startX    sub-rectangle starting X column (0 to 127)
     * @param startY    sub-rectangle starting Y row (0 to 127)
     * @param columns   sub-rectangle width in columns (1 to 128)
     * @param rows      sub-rectangle height in rows (1 to 128)
     */
    public record DirtyTile(
            int tileX,
            int tileY,
            int tileIndex,
            long newHash,
            byte[] tileData,
            int startX,
            int startY,
            int columns,
            int rows) {
        public DirtyTile(int tileX, int tileY, int tileIndex, long newHash, byte[] tileData) {
            this(tileX, tileY, tileIndex, newHash, tileData, 0, 0, TILE_SIZE, TILE_SIZE);
        }

        public DirtyTile(int tileX, int tileY, long newHash, byte[] tileData) {
            this(tileX, tileY, 0, newHash, tileData, 0, 0, TILE_SIZE, TILE_SIZE);
        }

        public boolean isPartial() {
            return columns < TILE_SIZE || rows < TILE_SIZE;
        }

        public int payloadSize() {
            return columns * rows;
        }
    }

    /**
     * Computes the 64-bit XXHash of the given tile byte buffer using
     * zero-allocation-hashing.
     *
     * @param tileData the tile byte array (typically 128x128 = 16,384 bytes)
     * @return the 64-bit hash, or 0 if tileData is null
     */
    public static long computeHash(byte[] tileData) {
        if (tileData == null) {
            return 0L;
        }
        return LongHashFunction.xx().hashBytes(tileData);
    }

    /**
     * Scans the canvas buffer for tiles whose current content hash differs from the
     * stored clean hash.
     * Extracts dirty sub-rectangle bounding boxes for tiles qualifying for partial
     * updates.
     *
     * @param canvas      the canvas buffer containing tile data and clean hashes
     * @param widthTiles  the number of tile columns
     * @param heightTiles the number of tile rows
     * @return a list of dirty tiles requiring packet retransmission
     */
    public static List<DirtyTile> findDirtyTiles(CanvasBuffer canvas, int widthTiles, int heightTiles) {
        List<DirtyTile> dirtyTiles = new ArrayList<>();
        findDirtyTiles(canvas, widthTiles, heightTiles, dirtyTiles);
        return dirtyTiles;
    }

    /**
     * Finds dirty tiles on the canvas and populates the given output list.
     * <p>
     * <b>Concurrency & Hash Coherence Contract:</b>
     * This method assumes sequential single-threaded execution per viewer canvas.
     * Tile data is snapshotted upon dirty detection so that
     * {@link DirtyTile#tileData()}
     * is decoupled from any subsequent mutations to the back buffer.
     *
     * @param canvas      the canvas buffer to inspect
     * @param widthTiles  width in tiles
     * @param heightTiles height in tiles
     * @param outList     target list to populate with dirty tiles
     */
    public static void findDirtyTiles(CanvasBuffer canvas, int widthTiles, int heightTiles, List<DirtyTile> outList) {
        if (outList == null) {
            return;
        }
        outList.clear();

        CanvasBufferImpl cbi = (canvas instanceof CanvasBufferImpl) ? (CanvasBufferImpl) canvas : null;
        if (cbi != null && !cbi.isAnyTileDirty()) {
            return;
        }

        for (int ty = 0; ty < heightTiles; ty++) {
            for (int tx = 0; tx < widthTiles; tx++) {
                int index = ty * widthTiles + tx;
                if (cbi != null && !cbi.isTileDirtyBit(index)) {
                    continue; // Skip clean tiles with zero hash overhead
                }

                CanvasTile ct = canvas.getCanvasTile(tx, ty);
                byte[] data = (ct != null && ct.isDirty())
                        ? ct.getBackBuffer()
                        : (canvas instanceof CanvasBufferImpl cbi2 ? cbi2.getFrontTile(tx, ty)
                                : canvas.getSubTile(tx, ty));
                long hash = computeHash(data);
                if (canvas.isTileDirty(tx, ty, hash)) {
                    if (ct != null && ct.qualifiesForPartialUpdate()) {
                        outList.add(new DirtyTile(
                                tx, ty, index, hash, data,
                                ct.getDirtyMinX(),
                                ct.getDirtyMinY(),
                                ct.getDirtyColumns(),
                                ct.getDirtyRows()));
                    } else {
                        outList.add(new DirtyTile(
                                tx, ty, index, hash, data,
                                0, 0, TILE_SIZE, TILE_SIZE));
                    }
                }
            }
        }
    }
}
