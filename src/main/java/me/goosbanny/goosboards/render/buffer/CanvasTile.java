package me.goosbanny.goosboards.render.buffer;

import net.openhft.hashing.LongHashFunction;

import java.util.Arrays;
import java.util.concurrent.locks.StampedLock;

/**
 * Persistent double-buffered canvas tile with zero heap allocations during render loops.
 * Holds permanently pinned front and back byte buffers (16,384 bytes each) and tracks
 * dirty sub-rectangle bounding boxes for partial map packet transmission.
 */
public final class CanvasTile {
    public static final int TILE_SIZE = 128;
    public static final int TILE_PIXELS = TILE_SIZE * TILE_SIZE; // 16,384
    public static final int MAX_PARTIAL_PIXELS = (int) (0.40 * TILE_PIXELS); // 6,553 pixels (40%)

    // Pinned permanent buffers allocated once on startup
    private final byte[] frontBuffer = new byte[TILE_PIXELS];
    private final byte[] backBuffer = new byte[TILE_PIXELS];
    private final StampedLock lock = new StampedLock();

    private volatile long lastContentHash = 0L;
    private volatile boolean dirty = false;

    // Local bounding box of dirty pixels [0, 127]
    private volatile int dirtyMinX = TILE_SIZE;
    private volatile int dirtyMinY = TILE_SIZE;
    private volatile int dirtyMaxX = -1;
    private volatile int dirtyMaxY = -1;

    public CanvasTile() {
    }

    /**
     * Sets a pixel color in the work (back) buffer and expands the dirty bounding box.
     *
     * @return true if the pixel color was modified; false if it was already equal to colorIndex
     */
    public boolean setPixel(int localX, int localY, byte colorIndex) {
        if (localX < 0 || localX >= TILE_SIZE || localY < 0 || localY >= TILE_SIZE) {
            return false;
        }
        int index = localY * TILE_SIZE + localX;
        if (backBuffer[index] != colorIndex) {
            backBuffer[index] = colorIndex;
            dirty = true;
            if (localX < dirtyMinX) dirtyMinX = localX;
            if (localX > dirtyMaxX) dirtyMaxX = localX;
            if (localY < dirtyMinY) dirtyMinY = localY;
            if (localY > dirtyMaxY) dirtyMaxY = localY;
            return true;
        }
        return false;
    }

    public byte getPixel(int localX, int localY) {
        if (localX < 0 || localX >= TILE_SIZE || localY < 0 || localY >= TILE_SIZE) {
            return 0;
        }
        return backBuffer[localY * TILE_SIZE + localX];
    }

    public void setSubTileData(byte[] data) {
        if (data != null) {
            System.arraycopy(data, 0, backBuffer, 0, Math.min(data.length, TILE_PIXELS));
            markFullDirty();
        }
    }

    /**
     * Marks the entire tile as dirty (e.g. initial render or scene transition).
     */
    public void markFullDirty() {
        this.dirty = true;
        this.dirtyMinX = 0;
        this.dirtyMinY = 0;
        this.dirtyMaxX = TILE_SIZE - 1;
        this.dirtyMaxY = TILE_SIZE - 1;
    }

    /**
     * Expands the dirty bounding box with component coordinates.
     */
    public void markDirtyRegion(int minX, int minY, int maxX, int maxY) {
        this.dirty = true;
        this.dirtyMinX = Math.max(0, Math.min(this.dirtyMinX, minX));
        this.dirtyMinY = Math.max(0, Math.min(this.dirtyMinY, minY));
        this.dirtyMaxX = Math.min(TILE_SIZE - 1, Math.max(this.dirtyMaxX, maxX));
        this.dirtyMaxY = Math.min(TILE_SIZE - 1, Math.max(this.dirtyMaxY, maxY));
    }

    /**
     * Computes the 64-bit XXHash of the back work buffer using zero-allocation-hashing.
     */
    public long computeHash() {
        return LongHashFunction.xx().hashBytes(backBuffer);
    }

    /**
     * Atomically swaps back buffer content into front buffer under StampedLock write lock
     * and resets the dirty tracking.
     */
    public void swap() {
        if (!dirty) {
            return;
        }
        long stamp = lock.writeLock();
        try {
            System.arraycopy(backBuffer, 0, frontBuffer, 0, TILE_PIXELS);
            this.dirty = false;
            this.dirtyMinX = TILE_SIZE;
            this.dirtyMinY = TILE_SIZE;
            this.dirtyMaxX = -1;
            this.dirtyMaxY = -1;
        } finally {
            lock.unlockWrite(stamp);
        }
    }

    /**
     * Clears the back buffer to zero and resets dirty bounds.
     */
    public void clear() {
        long stamp = lock.writeLock();
        try {
            Arrays.fill(backBuffer, (byte) 0);
            Arrays.fill(frontBuffer, (byte) 0);
            this.dirty = false;
            this.lastContentHash = 0L;
            this.dirtyMinX = TILE_SIZE;
            this.dirtyMinY = TILE_SIZE;
            this.dirtyMaxX = -1;
            this.dirtyMaxY = -1;
        } finally {
            lock.unlockWrite(stamp);
        }
    }

    /**
     * Snapshots the front buffer into a new byte array using optimistic read locking.
     */
    public byte[] snapshotFrontBuffer() {
        byte[] snap = new byte[TILE_PIXELS];
        snapshotFrontBuffer(snap);
        return snap;
    }

    /**
     * Copies front buffer into destination array using optimistic read locking to prevent torn frames.
     */
    public void snapshotFrontBuffer(byte[] dest) {
        if (dest == null || dest.length < TILE_PIXELS) {
            return;
        }
        long stamp = lock.tryOptimisticRead();
        System.arraycopy(frontBuffer, 0, dest, 0, TILE_PIXELS);
        if (!lock.validate(stamp)) {
            stamp = lock.readLock();
            try {
                System.arraycopy(frontBuffer, 0, dest, 0, TILE_PIXELS);
            } finally {
                lock.unlockRead(stamp);
            }
        }
    }

    public byte[] getFrontBuffer() {
        return frontBuffer;
    }

    public byte[] getBackBuffer() {
        return backBuffer;
    }

    public boolean isDirty() {
        return dirty;
    }

    public void setDirty(boolean dirty) {
        this.dirty = dirty;
    }

    public long getLastContentHash() {
        return lastContentHash;
    }

    public void setLastContentHash(long lastContentHash) {
        this.lastContentHash = lastContentHash;
    }

    public int getDirtyMinX() {
        return dirtyMinX <= dirtyMaxX ? dirtyMinX : 0;
    }

    public int getDirtyMinY() {
        return dirtyMinY <= dirtyMaxY ? dirtyMinY : 0;
    }

    public int getDirtyMaxX() {
        return dirtyMaxX >= dirtyMinX ? dirtyMaxX : TILE_SIZE - 1;
    }

    public int getDirtyMaxY() {
        return dirtyMaxY >= dirtyMinY ? dirtyMaxY : TILE_SIZE - 1;
    }

    public int getDirtyColumns() {
        if (!dirty || dirtyMaxX < dirtyMinX) return TILE_SIZE;
        return dirtyMaxX - dirtyMinX + 1;
    }

    public int getDirtyRows() {
        if (!dirty || dirtyMaxY < dirtyMinY) return TILE_SIZE;
        return dirtyMaxY - dirtyMinY + 1;
    }

    public int getDirtyPixelCount() {
        return getDirtyColumns() * getDirtyRows();
    }

    /**
     * Determines whether the dirty region qualifies for partial sub-rectangle transmission
     * (dirty area <= 40% of total tile pixels).
     */
    public boolean qualifiesForPartialUpdate() {
        return dirty && getDirtyPixelCount() <= MAX_PARTIAL_PIXELS;
    }
}
