package me.goosbanny.goosboards.scene.component.visual;

import me.goosbanny.goosboards.GoosBoards;
import me.goosbanny.goosboards.scene.Rect;
import me.goosbanny.goosboards.scene.RenderContext;
import me.goosbanny.goosboards.scene.component.UIComponent;

import me.goosbanny.goosboards.media.DiskMediaCache;
import me.goosbanny.goosboards.core.logging.DebugLogger;
import me.goosbanny.goosboards.render.buffer.CanvasBuffer;
import me.goosbanny.goosboards.render.palette.PaletteQuantizer;
import me.goosbanny.goosboards.media.security.SafeMediaLoader;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.net.URI;
import java.net.URLConnection;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Image component rendering static PNGs from the plugin images directory or remote web URLs.
 */
public class ImageComponent extends UIComponent {
    public record CachedImage(byte[] pixels, int width, int height) {}

    private static volatile SafeMediaLoader mediaLoader = null;
    private static volatile File imagesFolder = null;
    private static Consumer<String> assetLoadedCallback = null;
    private static final Set<String> GLOBAL_LOADING_URLS = ConcurrentHashMap.newKeySet();

    private String imageName = "";
    private String hoverImageName = "";
    private String fallback = "";
    private String cacheBehavior = "global";
    private volatile CachedImage cachedImage = null;
    private volatile CachedImage cachedHoverImage = null;
    private volatile CachedImage cachedFallbackImage = null;
    private final Map<UUID, CachedImage> perPlayerCache = new ConcurrentHashMap<>();
    private final Map<UUID, CachedImage> perPlayerHoverCache = new ConcurrentHashMap<>();

    public static void setAssetLoadedCallback(Consumer<String> callback) {
        assetLoadedCallback = callback;
    }

    private static void notifyAssetLoaded(String assetUrl) {
        if (assetLoadedCallback != null && assetUrl != null) {
            try {
                assetLoadedCallback.accept(assetUrl);
            } catch (Throwable ignored) {}
        }
    }

    public static void setSafeMediaLoader(SafeMediaLoader loader) {
        mediaLoader = loader;
    }

    public static void setImagesFolder(File folder) {
        imagesFolder = folder;
    }

    public static void clearCache() {
        GLOBAL_LOADING_URLS.clear();
    }

    public ImageComponent(String id, boolean contextDependent) {
        super(id, contextDependent);
    }

    @Override
    public boolean isContextDependent() {
        return super.isContextDependent() || (hoverImageName != null && !hoverImageName.isBlank());
    }

    @Override
    public void render(CanvasBuffer canvas, RenderContext ctx) {
        if (canvas == null) return;
        Rect b = getBounds();
        if (b.width() <= 0 || b.height() <= 0) return;

        boolean hovered = ctx != null && ctx.isHovered(getId());
        String activeName = (hovered && hoverImageName != null && !hoverImageName.isBlank())
                ? hoverImageName
                : imageName;

        String resolvedName = (ctx != null && ctx.viewer() != null) ? ctx.resolve(activeName) : activeName;
        boolean isPerPlayer = hovered || "per-player".equalsIgnoreCase(cacheBehavior) || "player".equalsIgnoreCase(cacheBehavior);
        UUID viewerId = (ctx != null && ctx.viewer() != null) ? ctx.viewer().getUniqueId() : null;

        CachedImage snap;
        if (hovered) {
            if (viewerId != null) {
                snap = perPlayerHoverCache.get(viewerId);
                if (snap == null || snap.width() != b.width() || snap.height() != b.height()) {
                    byte[] loaded = loadAndQuantize(resolvedName, b.width(), b.height(), ci -> perPlayerHoverCache.put(viewerId, ci));
                    if (loaded != null) {
                        snap = new CachedImage(loaded, b.width(), b.height());
                        perPlayerHoverCache.put(viewerId, snap);
                    }
                }
            } else {
                snap = this.cachedHoverImage;
                if (snap == null || snap.width() != b.width() || snap.height() != b.height()) {
                    byte[] loaded = loadAndQuantize(resolvedName, b.width(), b.height(), ci -> this.cachedHoverImage = ci);
                    if (loaded != null) {
                        snap = new CachedImage(loaded, b.width(), b.height());
                        this.cachedHoverImage = snap;
                    }
                }
            }
        } else if (isPerPlayer && viewerId != null) {
            snap = perPlayerCache.get(viewerId);
            if (snap == null || snap.width() != b.width() || snap.height() != b.height()) {
                byte[] loaded = loadAndQuantize(resolvedName, b.width(), b.height(), ci -> perPlayerCache.put(viewerId, ci));
                if (loaded != null) {
                    snap = new CachedImage(loaded, b.width(), b.height());
                    perPlayerCache.put(viewerId, snap);
                }
            }
        } else {
            snap = this.cachedImage;
            if (snap == null || snap.width() != b.width() || snap.height() != b.height()) {
                byte[] loaded = loadAndQuantize(resolvedName, b.width(), b.height(), ci -> this.cachedImage = ci);
                if (loaded != null) {
                    snap = new CachedImage(loaded, b.width(), b.height());
                    if (!"none".equalsIgnoreCase(cacheBehavior)) {
                        this.cachedImage = snap;
                    }
                } else {
                    snap = this.cachedImage;
                }
            }
        }

        // Fallback rendering while primary image is loading or if failed
        if (snap == null && fallback != null && !fallback.isBlank()) {
            if (cachedFallbackImage == null || cachedFallbackImage.width() != b.width() || cachedFallbackImage.height() != b.height()) {
                byte[] loaded = loadAndQuantize(fallback, b.width(), b.height(), ci -> this.cachedFallbackImage = ci);
                if (loaded != null) {
                    this.cachedFallbackImage = new CachedImage(loaded, b.width(), b.height());
                }
            }
            snap = this.cachedFallbackImage;
        }

        if (snap == null || snap.pixels() == null) return;

        byte[] pixels = snap.pixels();
        int bw = snap.width();
        int bh = snap.height();
        canvas.blitRaster(b.x(), b.y(), bw, bh, pixels);

        if (hasOutline()) {
            renderBoundsOutline(canvas, ctx);
        }
    }

    private byte[] loadAndQuantize(String targetName, int targetW, int targetH, Consumer<CachedImage> onLoaded) {
        return loadAndQuantizeImage(getId(), targetName, this.fallback, targetW, targetH, onLoaded);
    }

    public static byte[] loadAndQuantizeImage(
            String compId,
            String targetName,
            int targetW,
            int targetH,
            Consumer<CachedImage> onLoaded
    ) {
        return loadAndQuantizeImage(compId, targetName, null, targetW, targetH, onLoaded);
    }

    public static byte[] loadAndQuantizeImage(
            String compId,
            String targetName,
            String fallbackUrl,
            int targetW,
            int targetH,
            Consumer<CachedImage> onLoaded
    ) {
        if (targetName == null || targetName.isBlank()) return null;

        // Async web image loading with atomic deduplication and persistent disk cache
        if (targetName.startsWith("http://") || targetName.startsWith("https://")) {
            // 1. Check persistent disk cache first!
            byte[] diskCached = DiskMediaCache.getImage(targetName);
            if (diskCached != null && diskCached.length > 0) {
                try {
                    BufferedImage raw = ImageIO.read(new ByteArrayInputStream(diskCached));
                    if (raw != null) {
                        return scaleAndQuantize(compId, targetName, raw, targetW, targetH);
                    }
                } catch (Exception ignored) {}
            }

            // 2. Not in disk cache: fetch asynchronously with deduplication & auto-CDN / fallback retry
            fetchWebImageAsync(compId, targetName, fallbackUrl, targetW, targetH, onLoaded, 0);
            return null;
        }

        try {
            BufferedImage raw = null;
            if (imagesFolder != null) {
                File folder = imagesFolder.getCanonicalFile();
                File file = new File(folder, targetName).getCanonicalFile();
                if (!file.toPath().startsWith(folder.toPath())) {
                    DebugLogger.log("Image", "Component '%s' path traversal attempt blocked: '%s'",
                            compId, targetName);
                    return null;
                }
                if (file.exists()) {
                    raw = ImageIO.read(file);
                } else {
                    DebugLogger.log("Image", "Component '%s' file '%s' not found in folder %s",
                            compId, targetName, folder.getAbsolutePath());
                }
            }
            if (raw == null) {
                try (InputStream in = ImageComponent.class.getResourceAsStream("/images/" + targetName)) {
                    if (in != null) {
                        raw = ImageIO.read(in);
                    }
                }
            }
            if (raw == null) return null;
            return scaleAndQuantize(compId, targetName, raw, targetW, targetH);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static void fetchWebImageAsync(
            String compId,
            String targetUrl,
            String fallbackUrl,
            int targetW,
            int targetH,
            Consumer<CachedImage> onLoaded,
            int retryAttempt
    ) {
        if (!GLOBAL_LOADING_URLS.add(targetUrl)) {
            return;
        }

        DebugLogger.log("Image", "Component '%s' requesting web image '%s' (%dx%d px, attempt %d)",
                compId, targetUrl, targetW, targetH, retryAttempt + 1);

        CompletableFuture<byte[]> future;
        if (mediaLoader != null) {
            future = mediaLoader.loadSecureImage(URI.create(targetUrl), 10_000_000, 8000);
        } else {
            future = CompletableFuture.supplyAsync(() -> {
                try {
                    URLConnection conn = URI.create(targetUrl).toURL().openConnection();
                    conn.setConnectTimeout(8000);
                    conn.setReadTimeout(8000);
                    conn.setRequestProperty("User-Agent", "GoosBoards/1.0 (Minecraft Paper Engine)");
                    conn.setRequestProperty("Accept", "image/*");
                    try (InputStream in = conn.getInputStream()) {
                        return in.readAllBytes();
                    }
                } catch (Exception e) {
                    return null;
                }
            });
        }

        future.whenComplete((bytes, ex) -> {
            GLOBAL_LOADING_URLS.remove(targetUrl);
            if (bytes != null && bytes.length > 0) {
                DiskMediaCache.saveImage(targetUrl, bytes);
                try {
                    DebugLogger.log("Image", "Component '%s' received %d bytes from '%s'",
                            compId, bytes.length, targetUrl);
                    BufferedImage raw = ImageIO.read(new ByteArrayInputStream(bytes));
                    if (raw != null) {
                        byte[] quantized = scaleAndQuantize(compId, targetUrl, raw, targetW, targetH);
                        if (quantized != null) {
                            if (onLoaded != null) {
                                onLoaded.accept(new CachedImage(quantized, targetW, targetH));
                            }
                            notifyAssetLoaded(targetUrl);
                            return;
                        }
                    }
                } catch (Exception ignored) {}
            }

            // Primary attempt failed. Try alternative CDN / fallbackUrl if available
            String alternativeUrl = resolveAlternativeUrl(targetUrl, fallbackUrl);
            if (alternativeUrl != null && !alternativeUrl.equalsIgnoreCase(targetUrl)) {
                DebugLogger.log("Image", "Component '%s' falling back from '%s' to '%s'",
                        compId, targetUrl, alternativeUrl);
                byte[] altDiskCached = DiskMediaCache.getImage(alternativeUrl);
                if (altDiskCached != null && altDiskCached.length > 0) {
                    try {
                        BufferedImage raw = ImageIO.read(new ByteArrayInputStream(altDiskCached));
                        if (raw != null) {
                            byte[] quantized = scaleAndQuantize(compId, targetUrl, raw, targetW, targetH);
                            if (quantized != null) {
                                DiskMediaCache.saveImage(targetUrl, altDiskCached);
                                if (onLoaded != null) {
                                    onLoaded.accept(new CachedImage(quantized, targetW, targetH));
                                }
                                notifyAssetLoaded(targetUrl);
                                return;
                            }
                        }
                    } catch (Exception ignored) {}
                }

                // Fetch alternative asynchronously
                CompletableFuture<byte[]> altFuture;
                if (mediaLoader != null) {
                    altFuture = mediaLoader.loadSecureImage(URI.create(alternativeUrl), 10_000_000, 8000);
                } else {
                    altFuture = CompletableFuture.supplyAsync(() -> {
                        try {
                            URLConnection conn = URI.create(alternativeUrl).toURL().openConnection();
                            conn.setConnectTimeout(8000);
                            conn.setReadTimeout(8000);
                            conn.setRequestProperty("User-Agent", "GoosBoards/1.0 (Minecraft Paper Engine)");
                            conn.setRequestProperty("Accept", "image/*");
                            try (InputStream in = conn.getInputStream()) {
                                return in.readAllBytes();
                            }
                        } catch (Exception e) {
                            return null;
                        }
                    });
                }
                altFuture.whenComplete((altBytes, altEx) -> {
                    if (altBytes != null && altBytes.length > 0) {
                        DiskMediaCache.saveImage(alternativeUrl, altBytes);
                        DiskMediaCache.saveImage(targetUrl, altBytes);
                        try {
                            DebugLogger.log("Image", "Component '%s' alternative CDN succeeded (%d bytes)",
                                    compId, altBytes.length);
                            BufferedImage raw = ImageIO.read(new ByteArrayInputStream(altBytes));
                            if (raw != null) {
                                byte[] quantized = scaleAndQuantize(compId, targetUrl, raw, targetW, targetH);
                                if (quantized != null) {
                                    if (onLoaded != null) {
                                        onLoaded.accept(new CachedImage(quantized, targetW, targetH));
                                    }
                                    notifyAssetLoaded(targetUrl);
                                }
                            }
                        } catch (Exception ignored) {}
                    } else if (retryAttempt < 3) {
                        scheduleRetry(compId, targetUrl, fallbackUrl, targetW, targetH, onLoaded, retryAttempt);
                    }
                });
                return;
            }

            // Retry with exponential backoff if retries left
            if (retryAttempt < 3) {
                scheduleRetry(compId, targetUrl, fallbackUrl, targetW, targetH, onLoaded, retryAttempt);
            } else {
                DebugLogger.log("Image", "Component '%s' exhausted retries for '%s'", compId, targetUrl);
            }
        });
    }

    private static void scheduleRetry(
            String compId,
            String targetUrl,
            String fallbackUrl,
            int targetW,
            int targetH,
            Consumer<CachedImage> onLoaded,
            int retryAttempt
    ) {
        long delayMs = (1L << retryAttempt) * 1000L;
        DebugLogger.log("Image", "Component '%s' retrying '%s' in %dms (attempt %d)",
                compId, targetUrl, delayMs, retryAttempt + 1);
        CompletableFuture.delayedExecutor(delayMs, TimeUnit.MILLISECONDS).execute(() ->
                fetchWebImageAsync(compId, targetUrl, fallbackUrl, targetW, targetH, onLoaded, retryAttempt + 1)
        );
    }

    private static String resolveAlternativeUrl(String targetUrl, String fallbackUrl) {
        if (fallbackUrl != null && !fallbackUrl.isBlank() && !fallbackUrl.equalsIgnoreCase(targetUrl)) {
            return fallbackUrl;
        }
        if (targetUrl == null) return null;
        if (targetUrl.contains("minotar.net/avatar/")) {
            int idx = targetUrl.indexOf("/avatar/") + "/avatar/".length();
            String sub = targetUrl.substring(idx);
            int slash = sub.indexOf('/');
            String id = slash != -1 ? sub.substring(0, slash) : sub;
            if (id.endsWith(".png")) id = id.substring(0, id.length() - 4);
            return "https://crafatar.com/avatars/" + id + "?size=100&overlay";
        } else if (targetUrl.contains("minotar.net/helm/")) {
            int idx = targetUrl.indexOf("/helm/") + "/helm/".length();
            String sub = targetUrl.substring(idx);
            int slash = sub.indexOf('/');
            String id = slash != -1 ? sub.substring(0, slash) : sub;
            if (id.endsWith(".png")) id = id.substring(0, id.length() - 4);
            return "https://crafatar.com/avatars/" + id + "?size=100&overlay";
        }
        return null;
    }

    public static byte[] scaleAndQuantize(String compId, String targetName, BufferedImage raw, int targetW, int targetH) {
        try {
            BufferedImage scaled = new BufferedImage(targetW, targetH, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2d = scaled.createGraphics();
            try {
                g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2d.drawImage(raw, 0, 0, targetW, targetH, null);
            } finally {
                g2d.dispose();
            }

            byte[] out = PaletteQuantizer.quantizeImage(scaled, targetW, targetH, true);
            int visible = 0;
            for (byte b : out) {
                if (b != 0) visible++;
            }
            DebugLogger.log("Image", "Component '%s' dither-quantized '%s' to %dx%d -> %d visible pixels",
                    compId, targetName, targetW, targetH, visible);
            return out;
        } catch (Throwable ignored) {
            return null;
        }
    }

    @Override
    public boolean containsPixel(int localX, int localY) {
        Rect b = getBounds();
        return localX >= 0 && localX < b.width() && localY >= 0 && localY < b.height();
    }

    public String getImageName() {
        return imageName;
    }

    public void setImageName(String imageName) {
        this.imageName = imageName != null ? imageName : "";
        this.cachedImage = null;
    }

    public String getHoverImageName() {
        return hoverImageName;
    }

    public void setHoverImageName(String hoverImageName) {
        this.hoverImageName = hoverImageName != null ? hoverImageName : "";
        this.cachedHoverImage = null;
        if (!this.hoverImageName.isBlank()) {
            setContextDependent(true);
        }
    }

    public String getCacheBehavior() {
        return cacheBehavior;
    }

    public void setCacheBehavior(String cacheBehavior) {
        if (cacheBehavior != null && !cacheBehavior.isBlank()) {
            this.cacheBehavior = cacheBehavior.trim().toLowerCase();
        }
    }

    public String getFallback() {
        return fallback;
    }

    public void setFallback(String fallback) {
        this.fallback = fallback != null ? fallback : "";
        this.cachedFallbackImage = null;
    }
}