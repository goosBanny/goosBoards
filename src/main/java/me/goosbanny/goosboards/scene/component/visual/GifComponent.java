package me.goosbanny.goosboards.scene.component.visual;

import me.goosbanny.goosboards.scene.Rect;
import me.goosbanny.goosboards.scene.RenderContext;
import me.goosbanny.goosboards.scene.component.UIComponent;

import me.goosbanny.goosboards.GoosBoards;
import me.goosbanny.goosboards.media.DiskMediaCache;
import me.goosbanny.goosboards.core.logging.DebugLogger;
import me.goosbanny.goosboards.render.buffer.CanvasBuffer;
import me.goosbanny.goosboards.media.gif.GifDecoder;
import me.goosbanny.goosboards.render.palette.PaletteQuantizer;
import me.goosbanny.goosboards.media.security.SafeMediaLoader;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * High-performance animated GIF component with pre-quantized frame caching,
 * configurable per-gif/global framerate controls, and zero-allocation per-tick blitting.
 */
public class GifComponent extends UIComponent {

    public record QuantizedFrame(byte[] pixels, int delayMs) {}

    public record CachedGif(List<QuantizedFrame> frames, int totalDurationMs, int width, int height) {}

    private static final Cache<String, CachedGif> GLOBAL_CACHE = Caffeine.newBuilder()
            .maximumSize(50)
            .expireAfterAccess(Duration.ofMinutes(10))
            .build();
    private static final Set<String> LOADING_KEYS = ConcurrentHashMap.newKeySet();
    private static final ExecutorService GIF_WORKER = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "GoosBoards-Gif-Worker");
        t.setDaemon(true);
        return t;
    });

    private static File imagesFolder;
    private static SafeMediaLoader mediaLoader;
    private static int globalMaxFps = 20;
    private static int globalDefaultFps = 10;

    public static void setImagesFolder(File folder) {
        imagesFolder = folder;
    }

    public static void setSafeMediaLoader(SafeMediaLoader loader) {
        mediaLoader = loader;
    }

    public static void setGlobalSettings(int maxFps, int defaultFps) {
        globalMaxFps = Math.clamp(maxFps, 1, 60);
        globalDefaultFps = Math.clamp(defaultFps, 1, 60);
    }

    public static void clearCache() {
        GLOBAL_CACHE.invalidateAll();
        LOADING_KEYS.clear();
    }

    private String imageName = "";
    private String cacheBehavior = "global";
    private boolean loop = true;
    private double fps = 0.0;
    private int frameDelayMs = 0;
    private double speedMultiplier = 1.0;

    private volatile long playStartTimeMs = 0L;
    private volatile CachedGif localCachedGif = null;

    public GifComponent(String id) {
        super(id, false);
    }

    public String getImageName() {
        return imageName;
    }

    public void setImageName(String imageName) {
        this.imageName = imageName != null ? imageName.trim() : "";
        this.localCachedGif = null;
        this.playStartTimeMs = 0L;
    }

    public String getCacheBehavior() {
        return cacheBehavior;
    }

    public void setCacheBehavior(String cacheBehavior) {
        this.cacheBehavior = cacheBehavior != null ? cacheBehavior : "global";
    }

    public boolean isLoop() {
        return loop;
    }

    public void setLoop(boolean loop) {
        this.loop = loop;
    }

    public double getFps() {
        return fps;
    }

    public void setFps(double fps) {
        this.fps = Math.max(0.0, fps);
    }

    public int getFrameDelayMs() {
        return frameDelayMs;
    }

    public void setFrameDelayMs(int frameDelayMs) {
        this.frameDelayMs = Math.max(0, frameDelayMs);
    }

    public double getSpeedMultiplier() {
        return speedMultiplier;
    }

    public void setSpeedMultiplier(double speedMultiplier) {
        this.speedMultiplier = speedMultiplier > 0.0 ? speedMultiplier : 1.0;
    }

    @Override
    public boolean isDynamic() {
        return true;
    }

    @Override
    public void render(CanvasBuffer canvas, RenderContext ctx) {
        if (canvas == null) return;
        Rect b = getBounds();
        if (b.width() <= 0 || b.height() <= 0) return;

        String rawSource = (ctx != null && ctx.viewer() != null) ? ctx.resolve(imageName) : imageName;
        if (rawSource.isBlank()) return;

        int targetW = b.width();
        int targetH = b.height();
        String cacheKey = rawSource + "@" + targetW + "x" + targetH + "@fps=" + fps + "@delay=" + frameDelayMs + "@maxFps=" + globalMaxFps + "@defFps=" + globalDefaultFps;

        CachedGif gif = localCachedGif;
        if (gif == null || gif.width() != targetW || gif.height() != targetH) {
            gif = GLOBAL_CACHE.getIfPresent(cacheKey);
            if (gif != null) {
                localCachedGif = gif;
            } else {
                triggerAsyncLoad(rawSource, cacheKey, targetW, targetH);
                return;
            }
        }

        List<QuantizedFrame> frames = gif.frames();
        if (frames.isEmpty() || gif.totalDurationMs() <= 0) return;

        long now = System.currentTimeMillis();
        if (playStartTimeMs == 0L) {
            playStartTimeMs = now;
        }
        long elapsed = (long) ((now - playStartTimeMs) * speedMultiplier);
        if (loop) {
            elapsed %= gif.totalDurationMs();
        } else if (elapsed >= gif.totalDurationMs()) {
            elapsed = gif.totalDurationMs() - 1;
        }

        // Determine current frame based on cumulative timing
        QuantizedFrame currentFrame = frames.get(0);
        int accumulated = 0;
        for (QuantizedFrame f : frames) {
            accumulated += f.delayMs();
            if (elapsed < accumulated) {
                currentFrame = f;
                break;
            }
        }

        byte[] pixels = currentFrame.pixels();
        if (pixels == null || pixels.length < targetW * targetH) return;

        int bx = b.x();
        int by = b.y();
        canvas.blitRaster(bx, by, targetW, targetH, pixels);

        if (hasOutline()) {
            renderBoundsOutline(canvas, ctx);
        }
    }

    private void triggerAsyncLoad(String source, String cacheKey, int targetW, int targetH) {
        if (!LOADING_KEYS.add(cacheKey)) {
            return;
        }

        GIF_WORKER.submit(() -> {
            try {
                InputStream in = openSourceStream(source);
                if (in == null) {
                    DebugLogger.log("Gif", "Failed to open GIF stream for: %s", source);
                    return;
                }

                try (in) {
                    GifDecoder.DecodedGif decoded = GifDecoder.decode(in, GifDecoder.DEFAULT_MAX_FRAMES, GifDecoder.DEFAULT_MAX_DIMENSION);
                    List<BufferedImage> rawFrames = decoded.frames();
                    List<Integer> rawDelays = decoded.frameDelaysMs();
                    int numRaw = rawFrames.size();
                    if (numRaw == 0) return;

                    List<FrameSample> samples = computeFrameSamples(rawDelays, fps, frameDelayMs, globalMaxFps);
                    if (samples.isEmpty()) return;

                    List<QuantizedFrame> quantizedFrames = new ArrayList<>(samples.size());
                    int totalDuration = 0;
                    for (FrameSample sample : samples) {
                        byte[] pixels = scaleAndQuantize(rawFrames.get(sample.rawIndex()), targetW, targetH);
                        quantizedFrames.add(new QuantizedFrame(pixels, sample.delayMs()));
                        totalDuration += sample.delayMs();
                    }

                    CachedGif cached = new CachedGif(quantizedFrames, totalDuration, targetW, targetH);
                    GLOBAL_CACHE.put(cacheKey, cached);
                    localCachedGif = cached;
                    playStartTimeMs = System.currentTimeMillis();
                    DebugLogger.log("Gif", "Successfully pre-quantized %d frames for %s (%dx%d, total %dms)",
                            quantizedFrames.size(), source, targetW, targetH, totalDuration);

                    try {
                        GoosBoards instance = GoosBoards.getInstance();
                        if (instance != null && instance.getRenderEngine() != null) {
                            instance.getRenderEngine().requestComponentRedraw(source);
                        }
                    } catch (Throwable ignored) {}
                }
            } catch (Throwable t) {
                DebugLogger.log("Gif", "Error decoding animated GIF: %s - %s", source, t.getMessage());
            } finally {
                LOADING_KEYS.remove(cacheKey);
            }
        });
    }

    private byte[] scaleAndQuantize(BufferedImage rawFrame, int targetW, int targetH) {
        BufferedImage scaled = new BufferedImage(targetW, targetH, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = scaled.createGraphics();
        g2d.drawImage(rawFrame.getScaledInstance(targetW, targetH, Image.SCALE_SMOOTH), 0, 0, null);
        g2d.dispose();

        byte[] pixels = new byte[targetW * targetH];
        for (int y = 0; y < targetH; y++) {
            int rOff = y * targetW;
            for (int x = 0; x < targetW; x++) {
                int argb = scaled.getRGB(x, y);
                int alpha = (argb >>> 24);
                if (alpha > 64) {
                    pixels[rOff + x] = PaletteQuantizer.match(argb);
                } else {
                    pixels[rOff + x] = 0; // Transparent
                }
            }
        }
        return pixels;
    }

    public record FrameSample(int rawIndex, int delayMs) {}

    public static List<FrameSample> computeFrameSamples(List<Integer> rawDelays, double fps, int frameDelayMs, int globalMaxFps) {
        if (rawDelays == null || rawDelays.isEmpty()) return Collections.emptyList();
        int numRaw = rawDelays.size();

        int[] rawStartTimes = new int[numRaw];
        int rawTotalDuration = 0;
        for (int i = 0; i < numRaw; i++) {
            rawStartTimes[i] = rawTotalDuration;
            int d = rawDelays.get(i);
            if (d < 20) d = 100; // Standard browser clamp for delays < 20ms
            rawTotalDuration += d;
        }
        if (rawTotalDuration <= 0) {
            rawTotalDuration = numRaw * 100;
        }

        int minAllowedDelay = (int) Math.floor(1000.0 / Math.max(1, globalMaxFps));
        List<FrameSample> samples = new ArrayList<>();

        if (frameDelayMs > 0) {
            for (int i = 0; i < numRaw; i++) {
                samples.add(new FrameSample(i, frameDelayMs));
            }
            return samples;
        }

        double effectiveFps = 0.0;
        if (fps > 0.0) {
            effectiveFps = Math.min(fps, (double) globalMaxFps);
        } else {
            double nativeFps = (numRaw * 1000.0) / rawTotalDuration;
            if (nativeFps > globalMaxFps) {
                effectiveFps = globalMaxFps;
            }
        }

        if (effectiveFps > 0.0) {
            // Resample at target FPS preserving exact original total duration
            int stepMs = Math.max(minAllowedDelay, (int) Math.round(1000.0 / effectiveFps));
            int sampleCount = Math.max(1, (int) Math.ceil((double) rawTotalDuration / stepMs));
            for (int s = 0; s < sampleCount; s++) {
                int sampleTime = s * stepMs;
                int rawIdx = 0;
                for (int r = 0; r < numRaw; r++) {
                    if (sampleTime >= rawStartTimes[r]) {
                        rawIdx = r;
                    } else {
                        break;
                    }
                }
                int delay = (s == sampleCount - 1)
                        ? Math.max(minAllowedDelay, rawTotalDuration - s * stepMs)
                        : stepMs;
                samples.add(new FrameSample(rawIdx, delay));
            }
        } else {
            // Retain native frames & delays
            for (int i = 0; i < numRaw; i++) {
                int delay = rawDelays.get(i);
                if (delay < 20) delay = 100;
                delay = Math.max(minAllowedDelay, delay);
                samples.add(new FrameSample(i, delay));
            }
        }
        return samples;
    }

    private static final Pattern OG_IMAGE_1 = Pattern.compile("<meta[^>]+property=[\"']og:image[\"'][^>]+content=[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE);
    private static final Pattern OG_IMAGE_2 = Pattern.compile("<meta[^>]+content=[\"']([^\"']+)[\"'][^>]+property=[\"']og:image[\"']", Pattern.CASE_INSENSITIVE);
    private static final Pattern TWITTER_IMAGE_1 = Pattern.compile("<meta[^>]+name=[\"']twitter:image[\"'][^>]+content=[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE);
    private static final Pattern TWITTER_IMAGE_2 = Pattern.compile("<meta[^>]+content=[\"']([^\"']+)[\"'][^>]+name=[\"']twitter:image[\"']", Pattern.CASE_INSENSITIVE);
    private static final Pattern TENOR_GIF_JSON = Pattern.compile("\"(?:medium)?gif\"\\s*:\\s*\\{\\s*\"url\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern CONTENT_URL = Pattern.compile("\"contentUrl\"\\s*:\\s*\"([^\"]+\\.gif[^\"]*)\"");

    public static String extractDirectMediaUrlFromHtml(String html) {
        if (html == null || html.isBlank()) return null;
        Matcher m = OG_IMAGE_1.matcher(html);
        if (m.find()) return m.group(1).trim();
        m = OG_IMAGE_2.matcher(html);
        if (m.find()) return m.group(1).trim();
        m = TWITTER_IMAGE_1.matcher(html);
        if (m.find()) return m.group(1).trim();
        m = TWITTER_IMAGE_2.matcher(html);
        if (m.find()) return m.group(1).trim();
        m = TENOR_GIF_JSON.matcher(html);
        if (m.find()) return m.group(1).replace("\\u002F", "/").replace("\\/", "/").trim();
        m = CONTENT_URL.matcher(html);
        if (m.find()) return m.group(1).replace("\\u002F", "/").replace("\\/", "/").trim();
        return null;
    }

    private static boolean isValidGifHeader(byte[] data) {
        if (data == null || data.length < 6) return false;
        return data[0] == 'G' && data[1] == 'I' && data[2] == 'F' && data[3] == '8'
                && (data[4] == '7' || data[4] == '9') && data[5] == 'a';
    }

    private InputStream openSourceStream(String source) {
        try {
            if (source.startsWith("http://") || source.startsWith("https://")) {
                // 1. Check persistent disk cache first
                byte[] diskCached = DiskMediaCache.getImage(source);
                if (diskCached != null && isValidGifHeader(diskCached)) {
                    return new ByteArrayInputStream(diskCached);
                }

                // 2. Download via anti-SSRF SafeMediaLoader or URLConnection
                byte[] bytes = fetchBytes(source);
                if (bytes == null || bytes.length == 0) return null;

                // 3. If direct GIF header, cache and return
                if (isValidGifHeader(bytes)) {
                    DiskMediaCache.saveImage(source, bytes);
                    return new ByteArrayInputStream(bytes);
                }

                // 4. If payload is HTML or web page (e.g. Tenor, Giphy), extract direct media URL
                String text = new String(bytes, 0, Math.min(bytes.length, 65536), StandardCharsets.UTF_8);
                String directUrl = extractDirectMediaUrlFromHtml(text);
                if (directUrl != null && !directUrl.isBlank() && !directUrl.equalsIgnoreCase(source)) {
                    DebugLogger.log("Gif", "Resolved direct GIF media URL: %s from web page: %s", directUrl, source);
                    byte[] directCached = DiskMediaCache.getImage(directUrl);
                    if (directCached != null && isValidGifHeader(directCached)) {
                        DiskMediaCache.saveImage(source, directCached);
                        return new ByteArrayInputStream(directCached);
                    }
                    byte[] directBytes = fetchBytes(directUrl);
                    if (directBytes != null && isValidGifHeader(directBytes)) {
                        DiskMediaCache.saveImage(directUrl, directBytes);
                        DiskMediaCache.saveImage(source, directBytes);
                        return new ByteArrayInputStream(directBytes);
                    }
                }
                return null;
            }

            if (imagesFolder != null) {
                File folder = imagesFolder.getCanonicalFile();
                File file = new File(folder, source).getCanonicalFile();
                if (file.toPath().startsWith(folder.toPath()) && file.exists() && file.isFile()) {
                    return new FileInputStream(file);
                }
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private byte[] fetchBytes(String url) {
        try {
            if (mediaLoader != null) {
                return mediaLoader.loadSecureImage(URI.create(url), 8 * 1024 * 1024, 6000).get(10, TimeUnit.SECONDS);
            } else {
                URLConnection conn = URI.create(url).toURL().openConnection();
                conn.setConnectTimeout(4000);
                conn.setReadTimeout(6000);
                conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36 GoosBoards/1.0");
                conn.setRequestProperty("Accept", "image/*,text/html,*/*;q=0.8");
                try (InputStream in = conn.getInputStream()) {
                    return in.readAllBytes();
                }
            }
        } catch (Throwable t) {
            DebugLogger.log("Gif", "Failed to fetch bytes from: %s (%s)", url, t.getMessage());
            return null;
        }
    }

    @Override
    public boolean containsPixel(int localX, int localY) {
        Rect b = getBounds();
        return localX >= 0 && localX < b.width() && localY >= 0 && localY < b.height();
    }
}