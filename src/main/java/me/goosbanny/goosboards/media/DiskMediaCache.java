package me.goosbanny.goosboards.media;

import me.goosbanny.goosboards.GoosBoards;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Persistent disk cache for downloaded player skins and remote web images.
 * Files are stored under {@code plugins/GoosBoards/cache/skins/} and
 * {@code plugins/GoosBoards/cache/images/}.
 *
 * <p>Implements LRU (least recently used) eviction and retention limits to
 * prevent unbounded disk storage consumption.</p>
 */
public final class DiskMediaCache {

    public static final int DEFAULT_MAX_SKINS = 1000;
    public static final int DEFAULT_MAX_IMAGES = 500;
    public static final int DEFAULT_RETENTION_DAYS = 14;

    private static final Logger LOGGER = Logger.getLogger("GoosBoards");

    private static final ExecutorService PRUNE_EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "GoosBoards-MediaCache-Prune");
        t.setDaemon(true);
        return t;
    });

    private static volatile File baseCacheFolder;
    private static volatile File skinsFolder;
    private static volatile File imagesFolder;
    private static volatile boolean initialized = false;

    private static volatile int maxSkins = DEFAULT_MAX_SKINS;
    private static volatile int maxImages = DEFAULT_MAX_IMAGES;
    private static volatile int retentionDays = DEFAULT_RETENTION_DAYS;

    private static final AtomicInteger writeCounter = new AtomicInteger();

    private DiskMediaCache() {}

    public static synchronized void init(File dataFolder) {
        init(dataFolder, DEFAULT_MAX_SKINS, DEFAULT_MAX_IMAGES, DEFAULT_RETENTION_DAYS);
    }

    public static synchronized void init(File dataFolder, int maxSkinsLimit, int maxImagesLimit, int retentionDaysLimit) {
        if (dataFolder == null) {
            return;
        }
        baseCacheFolder = new File(dataFolder, "cache");
        skinsFolder = new File(baseCacheFolder, "skins");
        imagesFolder = new File(baseCacheFolder, "images");

        if (!skinsFolder.exists()) {
            skinsFolder.mkdirs();
        }
        if (!imagesFolder.exists()) {
            imagesFolder.mkdirs();
        }

        configureLimits(maxSkinsLimit, maxImagesLimit, retentionDaysLimit);
        initialized = true;

        pruneCacheAsync();
    }

    public static void configureLimits(int maxSkinsLimit, int maxImagesLimit, int retentionDaysLimit) {
        maxSkins = maxSkinsLimit > 0 ? maxSkinsLimit : DEFAULT_MAX_SKINS;
        maxImages = maxImagesLimit > 0 ? maxImagesLimit : DEFAULT_MAX_IMAGES;
        retentionDays = retentionDaysLimit > 0 ? retentionDaysLimit : DEFAULT_RETENTION_DAYS;
    }

    public static byte[] getSkin(String key) {
        if (!initialized || key == null || key.isBlank() || skinsFolder == null) {
            return null;
        }
        String safeName = sanitizeKey(key) + ".png";
        File file = new File(skinsFolder, safeName);
        if (file.exists() && file.isFile() && file.length() > 0) {
            try {
                file.setLastModified(System.currentTimeMillis());
                return Files.readAllBytes(file.toPath());
            } catch (IOException e) {
                LOGGER.log(Level.FINE, "Failed to read cached skin from disk: " + file.getName(), e);
            }
        }
        return null;
    }

    public static void saveSkin(String key, byte[] data) {
        if (!initialized || key == null || key.isBlank() || data == null || data.length == 0 || skinsFolder == null) {
            return;
        }
        String safeName = sanitizeKey(key) + ".png";
        File targetFile = new File(skinsFolder, safeName);
        writeAtomically(targetFile, data);

        if (writeCounter.incrementAndGet() % 25 == 0) {
            pruneCacheAsync();
        }
    }

    public static byte[] getImage(String url) {
        if (!initialized || url == null || url.isBlank() || imagesFolder == null) {
            return null;
        }
        String hash = sha256(url);
        File file = new File(imagesFolder, hash + ".bin");
        if (file.exists() && file.isFile() && file.length() > 0) {
            try {
                file.setLastModified(System.currentTimeMillis());
                return Files.readAllBytes(file.toPath());
            } catch (IOException e) {
                LOGGER.log(Level.FINE, "Failed to read cached image from disk: " + file.getName(), e);
            }
        }
        return null;
    }

    public static void saveImage(String url, byte[] data) {
        if (!initialized || url == null || url.isBlank() || data == null || data.length == 0 || imagesFolder == null) {
            return;
        }
        String hash = sha256(url);
        File targetFile = new File(imagesFolder, hash + ".bin");
        writeAtomically(targetFile, data);

        if (writeCounter.incrementAndGet() % 25 == 0) {
            pruneCacheAsync();
        }
    }

    public static void pruneCacheAsync() {
        if (!initialized) return;
        PRUNE_EXECUTOR.submit(DiskMediaCache::pruneCache);
    }

    public static synchronized void pruneCache() {
        if (!initialized) return;
        long maxAgeMillis = (long) retentionDays * 24L * 60L * 60L * 1000L;
        long now = System.currentTimeMillis();

        pruneFolder(skinsFolder, maxSkins, now, maxAgeMillis);
        pruneFolder(imagesFolder, maxImages, now, maxAgeMillis);
    }

    private static void pruneFolder(File folder, int maxFiles, long now, long maxAgeMillis) {
        if (folder == null || !folder.exists() || !folder.isDirectory()) {
            return;
        }
        File[] files = folder.listFiles(f -> f.isFile() && !f.getName().endsWith(".tmp"));
        if (files == null || files.length == 0) {
            return;
        }

        List<File> fileList = new ArrayList<>(Arrays.asList(files));

        // 1. Evict entries exceeding retention age
        if (maxAgeMillis > 0) {
            fileList.removeIf(f -> {
                if ((now - f.lastModified()) > maxAgeMillis) {
                    try {
                        Files.deleteIfExists(f.toPath());
                        return true;
                    } catch (IOException ignored) {
                        return false;
                    }
                }
                return false;
            });
        }

        // 2. If remaining count exceeds maxFiles, evict oldest entries (LRU)
        if (maxFiles > 0 && fileList.size() > maxFiles) {
            fileList.sort(Comparator.comparingLong(File::lastModified));
            int toRemove = fileList.size() - maxFiles;
            for (int i = 0; i < toRemove; i++) {
                try {
                    Files.deleteIfExists(fileList.get(i).toPath());
                } catch (IOException ignored) {}
            }
        }
    }

    public static synchronized void clearCache() {
        if (!initialized) {
            return;
        }
        deleteRecursively(skinsFolder);
        deleteRecursively(imagesFolder);
        if (skinsFolder != null) skinsFolder.mkdirs();
        if (imagesFolder != null) imagesFolder.mkdirs();
    }

    private static void writeAtomically(File targetFile, byte[] data) {
        Path targetPath = targetFile.toPath();
        Path tempPath = targetFile.getParentFile().toPath().resolve(targetFile.getName() + ".tmp");
        try {
            Files.write(tempPath, data, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
            try {
                Files.move(tempPath, targetPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException atomicEx) {
                Files.move(tempPath, targetPath, StandardCopyOption.REPLACE_EXISTING);
            }
            targetFile.setLastModified(System.currentTimeMillis());
        } catch (IOException e) {
            try {
                Files.deleteIfExists(tempPath);
                Files.write(targetPath, data, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
                targetFile.setLastModified(System.currentTimeMillis());
            } catch (IOException fallbackEx) {
                LOGGER.log(Level.WARNING, "Failed to write media cache file: " + targetFile.getName(), fallbackEx);
            }
        }
    }

    private static void deleteRecursively(File file) {
        if (file == null || !file.exists()) {
            return;
        }
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursively(child);
                }
            }
        }
        file.delete();
    }

    private static String sanitizeKey(String key) {
        return key.replaceAll("[^a-zA-Z0-9_.-]", "_").toLowerCase();
    }

    private static String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            return String.valueOf(input.hashCode());
        }
    }

    public static int getMaxSkins() {
        return maxSkins;
    }

    public static int getMaxImages() {
        return maxImages;
    }

    public static int getRetentionDays() {
        return retentionDays;
    }

    public static int getCachedSkinsCount() {
        if (skinsFolder == null || !skinsFolder.exists()) return 0;
        File[] files = skinsFolder.listFiles(f -> f.isFile() && !f.getName().endsWith(".tmp"));
        return files != null ? files.length : 0;
    }

    public static int getCachedImagesCount() {
        if (imagesFolder == null || !imagesFolder.exists()) return 0;
        File[] files = imagesFolder.listFiles(f -> f.isFile() && !f.getName().endsWith(".tmp"));
        return files != null ? files.length : 0;
    }
}
