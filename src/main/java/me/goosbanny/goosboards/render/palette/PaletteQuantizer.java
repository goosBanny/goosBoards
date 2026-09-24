package me.goosbanny.goosboards.render.palette;

import me.goosbanny.goosboards.render.palette.impl.PaletteQuantizerImpl;
import java.awt.image.BufferedImage;

/**
 * Contract for Minecraft 256-color map palette quantization and nearest-color matching.
 */
public interface PaletteQuantizer {
    byte matchColor(int argb);
    void quantizeTile(int[] argbPixels, int tileX, int tileY, byte[] outMapBytes, boolean dither);

    /**
     * Matches an ARGB color to the closest Minecraft map palette color index.
     *
     * @param argb 32-bit packed ARGB color
     * @return map palette byte index (0-255)
     */
    static byte match(int argb) {
        return PaletteQuantizerImpl.match(argb);
    }

    /**
     * Rescales and quantizes a BufferedImage into a map-compatible byte array.
     */
    static byte[] quantizeImage(BufferedImage image, int targetWidth, int targetHeight, boolean dither) {
        return PaletteQuantizerImpl.quantizeImage(image, targetWidth, targetHeight, dither);
    }
}
