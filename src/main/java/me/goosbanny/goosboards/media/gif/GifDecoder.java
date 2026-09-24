package me.goosbanny.goosboards.media.gif;

import me.goosbanny.goosboards.media.exception.GifDecodeLimitException;

import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.stream.ImageInputStream;
import java.awt.AlphaComposite;
import java.awt.Composite;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Memory-bounded GIF decoder enforcing maximum frame count and dimension limits.
 */
public final class GifDecoder {

    public record GifFrame(BufferedImage image, int delayMs) {}

    public record DecodedGif(List<BufferedImage> frames, List<Integer> frameDelaysMs) {}

    public static final int DEFAULT_MAX_FRAMES = 100;
    public static final int DEFAULT_MAX_DIMENSION = 2048;
    public static final long DEFAULT_MAX_UNCOMPRESSED_BYTES = 64 * 1024 * 1024L; // 64 MB budget

    private GifDecoder() {}

    /**
     * Decodes an animated GIF stream with standard safety bounds (100 frames, 2048x2048 resolution).
     */
    public static DecodedGif decode(InputStream in) throws IOException {
        return decode(in, DEFAULT_MAX_FRAMES, DEFAULT_MAX_DIMENSION);
    }

    /**
     * Decodes an animated GIF stream with safety bounds on frame count and image resolution.
     *
     * @param in           the GIF input stream
     * @param maxFrames    maximum allowed frames (capped at 100)
     * @param maxDimension maximum allowed width or height in pixels (capped at 2048)
     * @return DecodedGif containing the frames and delay times
     * @throws GifDecodeLimitException if frames or dimensions exceed limits
     * @throws IOException            if reading fails
     */
    public static DecodedGif decode(InputStream in, int maxFrames, int maxDimension) throws IOException {
        int effectiveMaxFrames = (maxFrames > 0) ? Math.min(maxFrames, DEFAULT_MAX_FRAMES) : DEFAULT_MAX_FRAMES;
        int effectiveMaxDimension = (maxDimension > 0) ? Math.min(maxDimension, DEFAULT_MAX_DIMENSION) : DEFAULT_MAX_DIMENSION;
        Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName("gif");
        if (!readers.hasNext()) {
            throw new IOException("No GIF ImageReader available in ImageIO runtime");
        }

        ImageReader reader = readers.next();
        try (ImageInputStream iisIn = ImageIO.createImageInputStream(in)) {
            if (iisIn == null) {
                throw new IOException("Unable to create ImageInputStream from input stream");
            }
            reader.setInput(iisIn, false, false);

            int numImages = reader.getNumImages(true);
            if (numImages > effectiveMaxFrames) {
                throw new GifDecodeLimitException(
                        "GIF frame count (" + numImages + ") exceeds configured maximum limit of " + effectiveMaxFrames
                );
            }

            List<BufferedImage> frames = new ArrayList<>(numImages);
            List<Integer> delays = new ArrayList<>(numImages);

            int masterWidth = Math.max(1, reader.getWidth(0));
            int masterHeight = Math.max(1, reader.getHeight(0));
            if (masterWidth > effectiveMaxDimension || masterHeight > effectiveMaxDimension) {
                throw new GifDecodeLimitException(
                        "GIF master dimension (" + masterWidth + "x" + masterHeight + ") exceeds limit of " + effectiveMaxDimension
                );
            }

            long totalMemoryBytes = 0L;
            BufferedImage master = new BufferedImage(masterWidth, masterHeight, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2d = master.createGraphics();

            for (int i = 0; i < numImages; i++) {
                int width = reader.getWidth(i);
                int height = reader.getHeight(i);
                if (width > effectiveMaxDimension || height > effectiveMaxDimension) {
                    g2d.dispose();
                    throw new GifDecodeLimitException(
                            "GIF frame " + i + " dimension (" + width + "x" + height + ") exceeds limit of " + effectiveMaxDimension
                    );
                }

                long frameMemory = (long) width * height * 4L;
                totalMemoryBytes += frameMemory;
                if (totalMemoryBytes > DEFAULT_MAX_UNCOMPRESSED_BYTES) {
                    g2d.dispose();
                    throw new GifDecodeLimitException(
                            "Total uncompressed GIF memory (" + totalMemoryBytes + " bytes) exceeds maximum budget of " + DEFAULT_MAX_UNCOMPRESSED_BYTES + " bytes"
                    );
                }

                BufferedImage rawFrame = reader.read(i);
                IIOMetadata metadata = null;
                try {
                    metadata = reader.getImageMetadata(i);
                } catch (Exception ignored) {}

                int left = extractNodeInt(metadata, "ImageDescriptor", "imageLeftPosition", 0);
                int top = extractNodeInt(metadata, "ImageDescriptor", "imageTopPosition", 0);
                String disposal = extractNodeString(metadata, "GraphicControlExtension", "disposalMethod", "none");

                g2d.drawImage(rawFrame, left, top, null);

                BufferedImage composite = new BufferedImage(masterWidth, masterHeight, BufferedImage.TYPE_INT_ARGB);
                Graphics2D cg = composite.createGraphics();
                cg.drawImage(master, 0, 0, null);
                cg.dispose();
                frames.add(composite);

                if ("restoreToBackgroundColor".equalsIgnoreCase(disposal)) {
                    Composite prev = g2d.getComposite();
                    g2d.setComposite(AlphaComposite.Clear);
                    g2d.fillRect(left, top, rawFrame.getWidth(), rawFrame.getHeight());
                    g2d.setComposite(prev);
                }

                int delayMs = extractFrameDelay(metadata);
                delays.add(delayMs);
            }
            g2d.dispose();

            return new DecodedGif(frames, delays);
        } finally {
            reader.dispose();
        }
    }

    private static int extractFrameDelay(IIOMetadata metadata) {
        if (metadata != null) {
            try {
                String[] names = metadata.getMetadataFormatNames();
                for (String name : names) {
                    Node root = metadata.getAsTree(name);
                    int delay = findDelayInNode(root);
                    if (delay > 0) return delay;
                }
            } catch (Exception ignored) {}
        }
        return 100; // Default 100ms
    }

    private static int extractNodeInt(IIOMetadata metadata, String nodeName, String attrName, int defVal) {
        if (metadata == null) return defVal;
        try {
            for (String name : metadata.getMetadataFormatNames()) {
                Node root = metadata.getAsTree(name);
                int val = findNodeInt(root, nodeName, attrName);
                if (val >= 0) return val;
            }
        } catch (Exception ignored) {}
        return defVal;
    }

    private static int findNodeInt(Node node, String nodeName, String attrName) {
        if (node == null) return -1;
        if (nodeName.equalsIgnoreCase(node.getNodeName())) {
            NamedNodeMap attrs = node.getAttributes();
            if (attrs != null) {
                Node attr = attrs.getNamedItem(attrName);
                if (attr != null) {
                    try {
                        return Integer.parseInt(attr.getNodeValue());
                    } catch (NumberFormatException ignored) {}
                }
            }
        }
        for (Node child = node.getFirstChild(); child != null; child = child.getNextSibling()) {
            int val = findNodeInt(child, nodeName, attrName);
            if (val >= 0) return val;
        }
        return -1;
    }

    private static String extractNodeString(IIOMetadata metadata, String nodeName, String attrName, String defVal) {
        if (metadata == null) return defVal;
        try {
            for (String name : metadata.getMetadataFormatNames()) {
                Node root = metadata.getAsTree(name);
                String val = findNodeString(root, nodeName, attrName);
                if (val != null) return val;
            }
        } catch (Exception ignored) {}
        return defVal;
    }

    private static String findNodeString(Node node, String nodeName, String attrName) {
        if (node == null) return null;
        if (nodeName.equalsIgnoreCase(node.getNodeName())) {
            NamedNodeMap attrs = node.getAttributes();
            if (attrs != null) {
                Node attr = attrs.getNamedItem(attrName);
                if (attr != null) {
                    return attr.getNodeValue();
                }
            }
        }
        for (Node child = node.getFirstChild(); child != null; child = child.getNextSibling()) {
            String val = findNodeString(child, nodeName, attrName);
            if (val != null) return val;
        }
        return null;
    }

    private static int findDelayInNode(Node node) {
        if (node == null) return -1;
        if ("GraphicControlExtension".equalsIgnoreCase(node.getNodeName())) {
            NamedNodeMap attrs = node.getAttributes();
            if (attrs != null) {
                Node delayAttr = attrs.getNamedItem("delayTime");
                if (delayAttr != null) {
                    try {
                        int delayCentiseconds = Integer.parseInt(delayAttr.getNodeValue());
                        int delayMs = delayCentiseconds * 10;
                        if (delayMs < 20) {
                            delayMs = 100; // Browser standard: clamp <= 1 centisecond (< 20ms) to 100ms
                        }
                        return delayMs;
                    } catch (NumberFormatException ignored) {}
                }
            }
        }
        for (Node child = node.getFirstChild(); child != null; child = child.getNextSibling()) {
            int found = findDelayInNode(child);
            if (found > 0) return found;
        }
        return -1;
    }
}
