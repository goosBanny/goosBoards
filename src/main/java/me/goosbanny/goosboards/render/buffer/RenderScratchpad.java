package me.goosbanny.goosboards.render.buffer;

/**
 * Thread-local scratch buffers pinned permanently to worker threads,
 * eliminating all raster, quantization, and dithering memory allocations in render loops.
 */
public final class RenderScratchpad {

    public static final int TILE_PIXELS = 128 * 128; // 16,384

    /**
     * Temporary ARGB raster buffer for a 128x128 tile.
     */
    public static final ThreadLocal<int[]> ARGB_TILE_SCRATCH =
            ThreadLocal.withInitial(() -> new int[TILE_PIXELS]);

    /**
     * RGB error diffusion accumulator buffer for tile-isolated Floyd-Steinberg dithering.
     * 128 * 128 * 3 integers (separate R, G, B channels).
     */
    public static final ThreadLocal<int[]> DITHER_ERROR_SCRATCH =
            ThreadLocal.withInitial(() -> new int[TILE_PIXELS * 3]);

    /**
     * Reusable scratch buffer for packing sub-rectangle map packet payloads.
     */
    public static final ThreadLocal<byte[]> SUB_TILE_BYTE_SCRATCH =
            ThreadLocal.withInitial(() -> new byte[TILE_PIXELS]);

    /**
     * Clears all thread-local scratch buffers for the calling thread to prevent classloader leaks.
     */
    public static void clearCurrentThread() {
        ARGB_TILE_SCRATCH.remove();
        DITHER_ERROR_SCRATCH.remove();
        SUB_TILE_BYTE_SCRATCH.remove();
    }

    private RenderScratchpad() {
    }
}
