package me.goosbanny.goosboards.render.palette.impl;

import me.goosbanny.goosboards.render.palette.MapPalette;
import me.goosbanny.goosboards.render.palette.PaletteQuantizer;

import java.awt.image.BufferedImage;
import java.util.Arrays;

public class PaletteQuantizerImpl implements PaletteQuantizer {
    private static final byte[] RGB565_CACHE = new byte[32 * 64 * 32];
    private static final int[] PALETTE_R = new int[256];
    private static final int[] PALETTE_G = new int[256];
    private static final int[] PALETTE_B = new int[256];

    // Reusable thread-local buffer: 6 * 128 = 768 floats for tile-isolated
    // dithering without heap allocations
    private static final ThreadLocal<float[]> DITHER_BUFFERS = ThreadLocal.withInitial(() -> new float[6 * 128]);

    static {
        // Pre-extract palette components for fast distance and dithering math
        for (int i = 0; i < 256; i++) {
            int argb = MapPalette.ARGB_COLORS[i];
            PALETTE_R[i] = (argb >> 16) & 0xFF;
            PALETTE_G[i] = (argb >> 8) & 0xFF;
            PALETTE_B[i] = argb & 0xFF;
        }

        // Populate RGB565 lookup cache (65,536 entries)
        for (int r5 = 0; r5 < 32; r5++) {
            int r = (r5 * 255 + 15) / 31;
            for (int g6 = 0; g6 < 64; g6++) {
                int g = (g6 * 255 + 31) / 63;
                for (int b5 = 0; b5 < 32; b5++) {
                    int b = (b5 * 255 + 15) / 31;

                    int bestIndex = 4;
                    long bestDist = Long.MAX_VALUE;

                    // Search usable Minecraft palette entries (4 to 247) using Redmean color metric
                    for (int i = 4; i < 248; i++) {
                        long rmean = (r + PALETTE_R[i]) / 2;
                        long dr = r - PALETTE_R[i];
                        long dg = g - PALETTE_G[i];
                        long db = b - PALETTE_B[i];
                        long dist = (((512 + rmean) * dr * dr) >> 8) + 4 * dg * dg + (((767 - rmean) * db * db) >> 8);
                        if (dist < bestDist) {
                            bestDist = dist;
                            bestIndex = i;
                            if (dist == 0) {
                                break;
                            }
                        }
                    }

                    int key = (r5 << 11) | (g6 << 5) | b5;
                    RGB565_CACHE[key] = (byte) bestIndex;
                }
            }
        }
    }

    private int canvasWidth = -1;

    public PaletteQuantizerImpl() {
    }

    public PaletteQuantizerImpl(int canvasWidth) {
        this.canvasWidth = canvasWidth;
    }

    public void setCanvasWidth(int canvasWidth) {
        this.canvasWidth = canvasWidth;
    }

    public int getCanvasWidth() {
        return canvasWidth;
    }

    public static byte match(int argb) {
        if ((argb >>> 24) < 64) {
            return 0; // Transparent / void
        }
        int r5 = ((argb >> 16) & 0xFF) >> 3;
        int g6 = ((argb >> 8) & 0xFF) >> 2;
        int b5 = (argb & 0xFF) >> 3;
        return RGB565_CACHE[(r5 << 11) | (g6 << 5) | b5];
    }

    /**
     * Quantizes an entire BufferedImage into Minecraft map color bytes.
     * <p>
     * <b>Performance & Allocation Warning:</b>
     * This method allocates six {@code float[]} row buffers per invocation and is
     * intended solely
     * for asset loading and background caching paths. It is <b>NOT</b> suitable for
     * per-frame
     * real-time render loops — use
     * {@link #quantizeTile(int[], int, byte[], boolean)} which utilizes
     * zero-allocation thread-local scratch buffers.
     */
    public static byte[] quantizeImage(BufferedImage scaled, int targetW, int targetH, boolean dither) {
        if (scaled == null || targetW <= 0 || targetH <= 0) {
            return new byte[0];
        }
        byte[] out = new byte[targetW * targetH];
        if (!dither) {
            for (int y = 0; y < targetH; y++) {
                int rowOffset = y * targetW;
                for (int x = 0; x < targetW; x++) {
                    int argb = scaled.getRGB(x, y);
                    out[rowOffset + x] = match(argb);
                }
            }
            return out;
        }

        // Floyd-Steinberg error diffusion dithering across targetW x targetH
        float[] curErrR = new float[targetW + 2];
        float[] curErrG = new float[targetW + 2];
        float[] curErrB = new float[targetW + 2];
        float[] nextErrR = new float[targetW + 2];
        float[] nextErrG = new float[targetW + 2];
        float[] nextErrB = new float[targetW + 2];

        for (int y = 0; y < targetH; y++) {
            Arrays.fill(nextErrR, 0.0f);
            Arrays.fill(nextErrG, 0.0f);
            Arrays.fill(nextErrB, 0.0f);

            int rowOffset = y * targetW;
            for (int x = 0; x < targetW; x++) {
                int argb = scaled.getRGB(x, y);
                int alpha = (argb >>> 24);
                if (alpha < 64) {
                    out[rowOffset + x] = 0;
                    continue;
                }

                int origR = (argb >> 16) & 0xFF;
                int origG = (argb >> 8) & 0xFF;
                int origB = argb & 0xFF;

                int curR = Math.clamp((int) Math.round(origR + curErrR[x + 1]), 0, 255);
                int curG = Math.clamp((int) Math.round(origG + curErrG[x + 1]), 0, 255);
                int curB = Math.clamp((int) Math.round(origB + curErrB[x + 1]), 0, 255);

                int r5 = curR >> 3;
                int g6 = curG >> 2;
                int b5 = curB >> 3;
                byte matchIndex = RGB565_CACHE[(r5 << 11) | (g6 << 5) | b5];
                out[rowOffset + x] = matchIndex;

                int matchedArgb = MapPalette.ARGB_COLORS[matchIndex & 0xFF];
                float errR = curR - ((matchedArgb >> 16) & 0xFF);
                float errG = curG - ((matchedArgb >> 8) & 0xFF);
                float errB = curB - (matchedArgb & 0xFF);

                // Error distribution: 7/16 right, 3/16 down-left, 5/16 down, 1/16 down-right
                curErrR[x + 2] += errR * (7.0f / 16.0f);
                curErrG[x + 2] += errG * (7.0f / 16.0f);
                curErrB[x + 2] += errB * (7.0f / 16.0f);

                nextErrR[x] += errR * (3.0f / 16.0f);
                nextErrG[x] += errG * (3.0f / 16.0f);
                nextErrB[x] += errB * (3.0f / 16.0f);

                nextErrR[x + 1] += errR * (5.0f / 16.0f);
                nextErrG[x + 1] += errG * (5.0f / 16.0f);
                nextErrB[x + 1] += errB * (5.0f / 16.0f);

                nextErrR[x + 2] += errR * (1.0f / 16.0f);
                nextErrG[x + 2] += errG * (1.0f / 16.0f);
                nextErrB[x + 2] += errB * (1.0f / 16.0f);
            }

            System.arraycopy(nextErrR, 0, curErrR, 0, targetW + 2);
            System.arraycopy(nextErrG, 0, curErrG, 0, targetW + 2);
            System.arraycopy(nextErrB, 0, curErrB, 0, targetW + 2);
        }
        return out;
    }

    @Override
    public byte matchColor(int argb) {
        return match(argb);
    }

    @Override
    public void quantizeTile(int[] argbPixels, int tileX, int tileY, byte[] outMapBytes, boolean dither) {
        if (argbPixels == null || outMapBytes == null) {
            return;
        }

        int stride;
        if (canvasWidth > 0) {
            stride = canvasWidth;
        } else if (argbPixels.length == 128 * 128) {
            stride = 128;
        } else {
            stride = (int) Math.round(Math.sqrt(argbPixels.length));
        }

        int startX = tileX * 128;
        int startY = tileY * 128;

        // Optimization: When dithering is disabled, directly quantize via
        // nearest-neighbor without uniform check
        if (!dither) {
            quantizeNearestNeighbor(argbPixels, startX, startY, stride, outMapBytes);
            return;
        }

        // Check for flat color tile (selective dithering bypass per §4.2)
        boolean isUniform = true;
        int firstColor = (startX < stride && startY * stride + startX < argbPixels.length)
                ? argbPixels[startY * stride + startX]
                : 0;

        for (int ly = 0; ly < 128; ly++) {
            int py = startY + ly;
            int rowOffset = py * stride;
            for (int lx = 0; lx < 128; lx++) {
                int px = startX + lx;
                int idx = rowOffset + px;
                int color = (px < stride && idx < argbPixels.length) ? argbPixels[idx] : 0;
                if (color != firstColor) {
                    isUniform = false;
                    break;
                }
            }
            if (!isUniform) {
                break;
            }
        }

        if (isUniform) {
            quantizeNearestNeighbor(argbPixels, startX, startY, stride, outMapBytes);
            return;
        }

        // Floyd-Steinberg dithering with tile-isolated error clamping using
        // thread-local buffer
        float[] buf = DITHER_BUFFERS.get();
        Arrays.fill(buf, 0.0f);

        int offR0 = 0;
        int offG0 = 128;
        int offB0 = 256;
        int offR1 = 384;
        int offG1 = 512;
        int offB1 = 640;

        for (int ly = 0; ly < 128; ly++) {
            int py = startY + ly;
            int rowOffset = py * stride;
            int outOffset = ly * 128;

            for (int lx = 0; lx < 128; lx++) {
                int px = startX + lx;
                int idx = rowOffset + px;
                int argb = (px < stride && idx < argbPixels.length) ? argbPixels[idx] : 0;

                if ((argb >>> 24) < 128) {
                    outMapBytes[outOffset + lx] = 0;
                    continue;
                }

                int origR = (argb >> 16) & 0xFF;
                int origG = (argb >> 8) & 0xFF;
                int origB = argb & 0xFF;

                float curRf = origR + buf[offR0 + lx];
                float curGf = origG + buf[offG0 + lx];
                float curBf = origB + buf[offB0 + lx];

                int curR = Math.clamp((int) Math.round(curRf), 0, 255);
                int curG = Math.clamp((int) Math.round(curGf), 0, 255);
                int curB = Math.clamp((int) Math.round(curBf), 0, 255);

                int r5 = curR >> 3;
                int g6 = curG >> 2;
                int b5 = curB >> 3;
                byte matchedIndex = RGB565_CACHE[(r5 << 11) | (g6 << 5) | b5];
                outMapBytes[outOffset + lx] = matchedIndex;

                int uIndex = matchedIndex & 0xFF;
                float diffR = curRf - PALETTE_R[uIndex];
                float diffG = curGf - PALETTE_G[uIndex];
                float diffB = curBf - PALETTE_B[uIndex];

                // Diffuse error strictly within tile boundaries (lx in [0, 127], ly in [0,
                // 127])
                if (lx + 1 < 128) {
                    float f7 = 7.0f / 16.0f;
                    buf[offR0 + lx + 1] += diffR * f7;
                    buf[offG0 + lx + 1] += diffG * f7;
                    buf[offB0 + lx + 1] += diffB * f7;
                }

                if (ly + 1 < 128) {
                    float f3 = 3.0f / 16.0f;
                    float f5 = 5.0f / 16.0f;
                    float f1 = 1.0f / 16.0f;

                    if (lx - 1 >= 0) {
                        buf[offR1 + lx - 1] += diffR * f3;
                        buf[offG1 + lx - 1] += diffG * f3;
                        buf[offB1 + lx - 1] += diffB * f3;
                    }

                    buf[offR1 + lx] += diffR * f5;
                    buf[offG1 + lx] += diffG * f5;
                    buf[offB1 + lx] += diffB * f5;

                    if (lx + 1 < 128) {
                        buf[offR1 + lx + 1] += diffR * f1;
                        buf[offG1 + lx + 1] += diffG * f1;
                        buf[offB1 + lx + 1] += diffB * f1;
                    }
                }
            }

            // Advance to next row (copy row 1 to row 0, then zero out row 1)
            System.arraycopy(buf, offR1, buf, offR0, 384);
            Arrays.fill(buf, offR1, offR1 + 384, 0.0f);
        }
    }

    private void quantizeNearestNeighbor(int[] argbPixels, int startX, int startY, int stride, byte[] outMapBytes) {
        for (int ly = 0; ly < 128; ly++) {
            int py = startY + ly;
            int rowOffset = py * stride;
            int outOffset = ly * 128;
            for (int lx = 0; lx < 128; lx++) {
                int px = startX + lx;
                int idx = rowOffset + px;
                int argb = (px < stride && idx < argbPixels.length) ? argbPixels[idx] : 0;
                outMapBytes[outOffset + lx] = matchColor(argb);
            }
        }
    }

    /**
     * Clears the thread-local dither buffers for the calling thread to prevent
     * classloader leaks.
     */
    public static void clearThreadLocal() {
        DITHER_BUFFERS.remove();
    }
}
