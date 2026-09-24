package me.goosbanny.goosboards.media.gif;

import me.goosbanny.goosboards.media.exception.GifDecodeLimitException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

class GifDecoderTest {

    private static byte[] createSyntheticGif(int frameCount, int width, int height) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        // GIF89a Header
        out.write(new byte[]{'G', 'I', 'F', '8', '9', 'a'});

        // Logical Screen Descriptor (Width LE, Height LE)
        out.write(width & 0xFF);
        out.write((width >> 8) & 0xFF);
        out.write(height & 0xFF);
        out.write((height >> 8) & 0xFF);
        out.write((byte) 0x80); // GCT present, 2 colors (1 bit)
        out.write(0);           // Background color index
        out.write(0);           // Pixel aspect ratio

        // Global Color Table (2 colors * 3 bytes = 6 bytes)
        out.write(new byte[]{0, 0, 0, (byte) 255, (byte) 255, (byte) 255});

        for (int i = 0; i < frameCount; i++) {
            // Graphic Control Extension (delay 10 centiseconds = 100ms)
            out.write(new byte[]{0x21, (byte) 0xF9, 0x04, 0x00, 10, 0, 0x00, 0x00});

            // Image Descriptor (0x2C, Left=0, Top=0, Width, Height, packed=0)
            out.write(0x2C);
            out.write(0);
            out.write(0);
            out.write(0);
            out.write(0);
            out.write(width & 0xFF);
            out.write((width >> 8) & 0xFF);
            out.write(height & 0xFF);
            out.write((height >> 8) & 0xFF);
            out.write(0x00); // No local color table

            // Minimal LZW Image Data: LZW min code size = 2
            out.write(0x02);
            // Block of 2 bytes: clear code (4) + end code (5) packed: 0x04, 0x01
            out.write(0x02);
            out.write(0x04);
            out.write(0x01);
            out.write(0x00); // Block terminator
        }

        // GIF Trailer
        out.write(0x3B);
        return out.toByteArray();
    }

    @Test
    @DisplayName("Test 6.11: GIF decoder - > 100 frames rejected")
    void testExcessiveFrameCountRejected() throws Exception {
        byte[] gifBytes = createSyntheticGif(101, 10, 10);
        ByteArrayInputStream in = new ByteArrayInputStream(gifBytes);

        GifDecodeLimitException ex = assertThrows(GifDecodeLimitException.class, () ->
                GifDecoder.decode(in, 100, 2048)
        );
        assertTrue(ex.getMessage().contains("frame count (101) exceeds"));
    }

    @Test
    @DisplayName("Test 6.12: GIF decoder - frame > 2048px rejected")
    void testExcessiveDimensionRejected() throws Exception {
        byte[] gifBytes = createSyntheticGif(1, 2049, 100);
        ByteArrayInputStream in = new ByteArrayInputStream(gifBytes);

        GifDecodeLimitException ex = assertThrows(GifDecodeLimitException.class, () ->
                GifDecoder.decode(in, 100, 2048)
        );
        assertTrue(ex.getMessage().contains("dimension (2049x100) exceeds limit of 2048"));
    }

    @Test
    @DisplayName("Valid GIF within bounds decodes successfully")
    void testValidGifDecodes() throws Exception {
        byte[] gifBytes = createSyntheticGif(3, 32, 32);
        ByteArrayInputStream in = new ByteArrayInputStream(gifBytes);

        GifDecoder.DecodedGif decoded = assertDoesNotThrow(() ->
                GifDecoder.decode(in, 100, 2048)
        );

        assertEquals(3, decoded.frames().size());
        assertEquals(3, decoded.frameDelaysMs().size());
        assertEquals(32, decoded.frames().get(0).getWidth());
        assertEquals(32, decoded.frames().get(0).getHeight());
        assertEquals(100, decoded.frameDelaysMs().get(0));
    }
}
