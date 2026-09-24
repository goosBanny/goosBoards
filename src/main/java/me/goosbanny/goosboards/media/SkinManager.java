package me.goosbanny.goosboards.media;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import me.goosbanny.goosboards.render.palette.impl.PaletteQuantizerImpl;
import me.goosbanny.goosboards.media.security.SafeMediaLoader;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URI;
import java.time.Duration;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * High-performance 2D player skin and avatar manager.
 * Features a zero-HTTP fast-path for online players using session profile textures,
 * 8x8 base + helmet layer alpha compositing, async Mojang query with Caffeine LRU caching,
 * and embedded Steve fallback.
 */
public final class SkinManager {

    private static final Cache<String, byte[]> SKIN_CACHE = Caffeine.newBuilder()
            .maximumSize(500)
            .expireAfterAccess(Duration.ofHours(2))
            .build();

    private static final Set<String> PENDING_SKINS = ConcurrentHashMap.newKeySet();

    // Built-in Steve 8x8 face fallback (base flesh, hair, eyes)
    private static final byte SKIN_BASE = 24;
    private static final byte HAIR_COLOR = 105;
    private static final byte EYE_WHITE = 34;
    private static final byte EYE_PUPIL = 98;

    private SkinManager() {}

    public static void clearCache() {
        SKIN_CACHE.invalidateAll();
        PENDING_SKINS.clear();
    }

    /**
     * Composites an 8x8 player head avatar from a standard 64x64 or 64x32 skin image.
     * Alpha-blends the outer helmet/hat layer (40,8)-(48,16) over the base head (8,8)-(16,16).
     */
    public static byte[] compositeHead(BufferedImage fullSkin) {
        if (fullSkin == null) {
            return getFallbackSteveHead();
        }

        BufferedImage head8x8 = new BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = head8x8.createGraphics();

        try {
            // Base head: (8,8) to (16,16)
            int skinW = fullSkin.getWidth();
            int skinH = fullSkin.getHeight();

            if (skinW >= 16 && skinH >= 16) {
                BufferedImage base = fullSkin.getSubimage(8, 8, 8, 8);
                g2d.drawImage(base, 0, 0, null);

                // Hat/helmet layer: (40,8) to (48,16) if available in texture
                if (skinW >= 48 && skinH >= 16) {
                    BufferedImage hat = fullSkin.getSubimage(40, 8, 8, 8);
                    g2d.drawImage(hat, 0, 0, null);
                }
            } else {
                // If skin image is already an 8x8 crop
                g2d.drawImage(fullSkin, 0, 0, 8, 8, null);
            }
        } finally {
            g2d.dispose();
        }

        byte[] quantized = new byte[64];
        for (int y = 0; y < 8; y++) {
            for (int x = 0; x < 8; x++) {
                int argb = head8x8.getRGB(x, y);
                quantized[y * 8 + x] = PaletteQuantizerImpl.match(argb);
            }
        }
        return quantized;
    }

    public static byte[] getFallbackSteveHead() {
        byte[] head = new byte[64];
        for (int gy = 0; gy < 8; gy++) {
            for (int gx = 0; gx < 8; gx++) {
                byte c;
                if (gy < 2) {
                    c = HAIR_COLOR;
                } else if (gx == 0 || gx == 7) {
                    c = HAIR_COLOR;
                } else if (gy == 2 && (gx == 1 || gx == 6)) {
                    c = HAIR_COLOR;
                } else if (gy == 4) {
                    c = (gx == 2 || gx == 5) ? EYE_PUPIL : ((gx == 1 || gx == 6) ? EYE_WHITE : SKIN_BASE);
                } else {
                    c = SKIN_BASE;
                }
                head[gy * 8 + gx] = c;
            }
        }
        return head;
    }

    /**
     * Resolves an 8x8 quantized avatar.
     * Uses in-memory PlayerProfile textures for online players with zero network overhead.
     */
    public static byte[] getOrLoadSkin(
            String resolvedSkin,
            Player onlineViewer,
            SafeMediaLoader mediaLoader,
            Consumer<String> onLoaded
    ) {
        if (resolvedSkin == null || resolvedSkin.isBlank()) {
            return getFallbackSteveHead();
        }

        // Unresolved / loading placeholder guard: do not make bogus web queries for %placeholder%
        if (resolvedSkin.startsWith("%") && resolvedSkin.endsWith("%")) {
            return null;
        }

        String key = resolvedSkin.toLowerCase();
        byte[] cached = SKIN_CACHE.getIfPresent(key);
        if (cached != null) {
            return cached;
        }

        // Tier 2: In-Game Online Player Profile (Fast-path, ensures live skins without disk staleness)
        Player targetPlayer = null;
        if (onlineViewer != null && (onlineViewer.getName().equalsIgnoreCase(resolvedSkin) || onlineViewer.getUniqueId().toString().equalsIgnoreCase(resolvedSkin))) {
            targetPlayer = onlineViewer;
        } else if (Bukkit.getServer() != null) {
            try {
                targetPlayer = Bukkit.getPlayerExact(resolvedSkin);
                if (targetPlayer == null) {
                    try {
                        UUID uuid = UUID.fromString(resolvedSkin);
                        targetPlayer = Bukkit.getPlayer(uuid);
                    } catch (IllegalArgumentException notUuid) {}
                }
            } catch (Throwable ignored) {}
        }

        String targetUrl = null;
        if (targetPlayer != null && targetPlayer.isOnline()) {
            try {
                PlayerProfile profile = targetPlayer.getPlayerProfile();
                if (profile != null && profile.getTextures() != null && profile.getTextures().getSkin() != null) {
                    targetUrl = profile.getTextures().getSkin().toString();
                }
            } catch (Throwable ignored) {}
        }

        // Tier 3: Paper OfflinePlayer local profile cache
        if (targetUrl == null && Bukkit.getServer() != null) {
            try {
                OfflinePlayer offlinePlayer = null;
                try {
                    UUID uuid = UUID.fromString(resolvedSkin);
                    offlinePlayer = Bukkit.getOfflinePlayer(uuid);
                } catch (IllegalArgumentException notUuid) {
                    offlinePlayer = Bukkit.getOfflinePlayer(resolvedSkin);
                }
                if (offlinePlayer != null) {
                    PlayerProfile profile = offlinePlayer.getPlayerProfile();
                    if (profile != null && profile.getTextures() != null && profile.getTextures().getSkin() != null) {
                        targetUrl = profile.getTextures().getSkin().toString();
                    }
                }
            } catch (Throwable ignored) {}
        }

        // If in-game or offline profile provided an immutable texture URL, check disk cache by URL!
        if (targetUrl != null) {
            byte[] diskBytes = DiskMediaCache.getImage(targetUrl);
            if (diskBytes != null && diskBytes.length > 0) {
                try {
                    BufferedImage diskImg = ImageIO.read(new ByteArrayInputStream(diskBytes));
                    if (diskImg != null) {
                        byte[] quantized = compositeHead(diskImg);
                        SKIN_CACHE.put(key, quantized);
                        return quantized;
                    }
                } catch (Exception ignored) {}
            }
        }

        // Tier 4: Persistent Local Disk Cache by skin name/UUID
        byte[] diskBytes = DiskMediaCache.getSkin(key);
        if (diskBytes != null && diskBytes.length > 0) {
            try {
                BufferedImage diskImg = ImageIO.read(new ByteArrayInputStream(diskBytes));
                if (diskImg != null) {
                    byte[] quantized = compositeHead(diskImg);
                    SKIN_CACHE.put(key, quantized);
                    return quantized;
                }
            } catch (Exception ignored) {}
        }

        // Tier 5: Direct URL or remote avatar provider with fallback
        String primaryUrl;
        String fallbackUrl = null;
        if (targetUrl != null) {
            primaryUrl = targetUrl;
        } else if (resolvedSkin.startsWith("http://") || resolvedSkin.startsWith("https://")) {
            primaryUrl = resolvedSkin;
        } else {
            String clean = resolvedSkin.replaceAll("[^a-zA-Z0-9_-]", "");
            if (clean.isBlank()) clean = "Steve";
            primaryUrl = "https://minotar.net/skin/" + clean + ".png";
            fallbackUrl = "https://crafatar.com/skins/" + clean;
        }

        final String finalPrimary = primaryUrl;
        final String finalFallback = fallbackUrl;
        final String finalTargetUrl = targetUrl;

        if (PENDING_SKINS.add(key)) {
            fetchSkinAsync(finalPrimary, mediaLoader)
                    .thenCompose(bytes -> {
                        if (bytes != null && bytes.length > 0) {
                            return CompletableFuture.completedFuture(bytes);
                        }
                        if (finalFallback != null) {
                            return fetchSkinAsync(finalFallback, mediaLoader);
                        }
                        return CompletableFuture.completedFuture(null);
                    })
                    .thenAccept(bytes -> {
                        PENDING_SKINS.remove(key);
                        if (bytes != null && bytes.length > 0) {
                            try {
                                BufferedImage raw = ImageIO.read(new ByteArrayInputStream(bytes));
                                if (raw != null) {
                                    byte[] quantized = compositeHead(raw);
                                    SKIN_CACHE.put(key, quantized);
                                    DiskMediaCache.saveSkin(key, bytes);
                                    if (finalTargetUrl != null) {
                                        DiskMediaCache.saveImage(finalTargetUrl, bytes);
                                    }
                                    if (onLoaded != null) {
                                        onLoaded.accept(key);
                                    }
                                }
                            } catch (Exception ignored) {}
                        }
                    })
                    .exceptionally(ex -> {
                        PENDING_SKINS.remove(key);
                        return null;
                    });
        }

        return null;
    }

    private static CompletableFuture<byte[]> fetchSkinAsync(String url, SafeMediaLoader mediaLoader) {
        if (url == null || url.isBlank()) {
            return CompletableFuture.completedFuture(null);
        }
        if (mediaLoader != null) {
            return mediaLoader.loadSecureImage(URI.create(url), 500_000, 4000)
                    .exceptionally(ex -> null);
        }
        return CompletableFuture.supplyAsync(() -> {
            try (InputStream in = URI.create(url).toURL().openStream()) {
                return in.readAllBytes();
            } catch (Exception e) {
                return null;
            }
        });
    }
}