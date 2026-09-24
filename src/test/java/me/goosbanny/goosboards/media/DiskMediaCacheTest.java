package me.goosbanny.goosboards.media;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DiskMediaCacheTest {

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        DiskMediaCache.init(tempDir.toFile());
    }

    @Test
    @DisplayName("Saves and retrieves player skin bytes from disk")
    void testSaveAndGetSkin() {
        byte[] skinData = new byte[]{10, 20, 30, 40, 50};
        DiskMediaCache.saveSkin("Notch", skinData);

        byte[] loaded = DiskMediaCache.getSkin("Notch");
        assertNotNull(loaded, "Saved skin must be retrievable from disk");
        assertArrayEquals(skinData, loaded);
    }

    @Test
    @DisplayName("Saves and retrieves web image bytes from disk by URL hash")
    void testSaveAndGetImage() {
        String url = "https://minotar.net/avatar/MHF_Chest/100.png";
        byte[] imgData = new byte[]{-1, -2, -3, 0, 1, 2};
        DiskMediaCache.saveImage(url, imgData);

        byte[] loaded = DiskMediaCache.getImage(url);
        assertNotNull(loaded, "Saved image must be retrievable by URL");
        assertArrayEquals(imgData, loaded);
    }

    @Test
    @DisplayName("Sanitizes keys with illegal filesystem characters safely")
    void testSanitization() {
        byte[] data = new byte[]{1, 2, 3};
        DiskMediaCache.saveSkin("../../evil/name?*:", data);

        byte[] loaded = DiskMediaCache.getSkin("../../evil/name?*:");
        assertNotNull(loaded);
        assertArrayEquals(data, loaded);
    }

    @Test
    @DisplayName("Returns null on null or blank keys")
    void testNullOrBlankGuards() {
        assertNull(DiskMediaCache.getSkin(null));
        assertNull(DiskMediaCache.getSkin(""));
        assertNull(DiskMediaCache.getSkin("   "));
        assertNull(DiskMediaCache.getImage(null));
        assertNull(DiskMediaCache.getImage(""));
    }

    @Test
    @DisplayName("clearCache deletes cached files from disk")
    void testClearCache() {
        byte[] data = new byte[]{42};
        DiskMediaCache.saveSkin("Alex", data);
        DiskMediaCache.saveImage("https://example.com/test.bin", data);

        assertNotNull(DiskMediaCache.getSkin("Alex"));
        assertNotNull(DiskMediaCache.getImage("https://example.com/test.bin"));

        DiskMediaCache.clearCache();

        assertNull(DiskMediaCache.getSkin("Alex"));
        assertNull(DiskMediaCache.getImage("https://example.com/test.bin"));
    }

    @Test
    @DisplayName("Evicts oldest entries (LRU) when max cached skins limit is exceeded")
    void testLruEviction() {
        DiskMediaCache.configureLimits(3, 3, 14);

        DiskMediaCache.saveSkin("player1", new byte[]{1});
        DiskMediaCache.saveSkin("player2", new byte[]{2});
        DiskMediaCache.saveSkin("player3", new byte[]{3});
        DiskMediaCache.saveSkin("player4", new byte[]{4});
        DiskMediaCache.saveSkin("player5", new byte[]{5});

        long now = System.currentTimeMillis();
        File skinsDir = new File(tempDir.toFile(), "cache/skins");

        // Set timestamps deterministically:
        // player2: oldest (now - 50000ms)
        // player3: second oldest (now - 40000ms)
        // player4: middle (now - 30000ms)
        // player1: accessed recently (now - 10000ms)
        // player5: newest (now)
        new File(skinsDir, "player2.png").setLastModified(now - 50000);
        new File(skinsDir, "player3.png").setLastModified(now - 40000);
        new File(skinsDir, "player4.png").setLastModified(now - 30000);
        new File(skinsDir, "player1.png").setLastModified(now - 10000);
        new File(skinsDir, "player5.png").setLastModified(now);

        DiskMediaCache.pruneCache();

        assertTrue(DiskMediaCache.getCachedSkinsCount() <= 3, "Cache count must be pruned to maxSkins");
        assertNotNull(DiskMediaCache.getSkin("player1"), "Recently accessed player1 must be retained by LRU");
        assertNotNull(DiskMediaCache.getSkin("player5"), "Newest player5 must be retained");
        assertNull(DiskMediaCache.getSkin("player2"), "Oldest player2 must be evicted");
        assertNull(DiskMediaCache.getSkin("player3"), "Second oldest player3 must be evicted");
    }

    @Test
    @DisplayName("Evicts entries older than retention days")
    void testRetentionAgeEviction() {
        DiskMediaCache.configureLimits(100, 100, 1); // 1 day retention
        DiskMediaCache.saveSkin("old_player", new byte[]{99});

        // Artificially age the file to 3 days ago
        File skinsDir = new File(tempDir.toFile(), "cache/skins");
        File oldFile = new File(skinsDir, "old_player.png");
        assertTrue(oldFile.exists());
        oldFile.setLastModified(System.currentTimeMillis() - (3L * 24L * 60L * 60L * 1000L));

        DiskMediaCache.pruneCache();

        assertNull(DiskMediaCache.getSkin("old_player"), "Expired skin must be evicted");
    }
}
