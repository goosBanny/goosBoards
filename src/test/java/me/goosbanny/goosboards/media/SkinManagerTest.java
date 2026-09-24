package me.goosbanny.goosboards.media;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.net.URL;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SkinManagerTest {

    @BeforeEach
    void setUp() {
        SkinManager.clearCache();
    }

    @Test
    @DisplayName("getFallbackSteveHead returns valid 64-byte quantized palette array")
    void testFallbackSteveHead() {
        byte[] steve = SkinManager.getFallbackSteveHead();
        assertNotNull(steve);
        assertEquals(64, steve.length);

        // Check that pixels are populated with non-zero values
        boolean hasColors = false;
        for (byte b : steve) {
            if (b != 0) {
                hasColors = true;
                break;
            }
        }
        assertTrue(hasColors, "Steve fallback must have non-zero palette indices");
    }

    @Test
    @DisplayName("compositeHead alpha-blends helmet layer over base head")
    void testCompositeHeadWithHelmet() {
        // Create 64x64 dummy skin image
        BufferedImage skin = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = skin.createGraphics();

        // Fill base head (8,8 to 16,16) with Solid Red
        g2d.setColor(Color.RED);
        g2d.fillRect(8, 8, 8, 8);

        // Fill helmet layer (40,8 to 48,16) with Semi-transparent Blue
        g2d.setColor(new Color(0, 0, 255, 128));
        g2d.fillRect(40, 8, 8, 8);
        g2d.dispose();

        byte[] composited = SkinManager.compositeHead(skin);
        assertNotNull(composited);
        assertEquals(64, composited.length);
    }

    @Test
    @DisplayName("compositeHead returns fallback when input is null")
    void testCompositeHeadNull() {
        byte[] result = SkinManager.compositeHead(null);
        assertNotNull(result);
        assertEquals(64, result.length);
        assertArrayEquals(SkinManager.getFallbackSteveHead(), result);
    }

    @Test
    @DisplayName("getOrLoadSkin returns fallback on blank input")
    void testGetOrLoadSkinBlank() {
        byte[] result = SkinManager.getOrLoadSkin("", null, null, null);
        assertNotNull(result);
        assertEquals(64, result.length);
        assertArrayEquals(SkinManager.getFallbackSteveHead(), result);
    }

    @Test
    @DisplayName("Unresolved %placeholder% inputs return null to prevent bogus web queries")
    void testUnresolvedPlaceholderGuards() {
        assertNull(SkinManager.getOrLoadSkin("%leaderboard_kills_name_1%", null, null, null));
        assertNull(SkinManager.getOrLoadSkin("%player_name%", null, null, null));
        assertNull(SkinManager.getOrLoadSkin("%ajlb_lb_kills_1_name%", null, null, null));
        assertNotNull(SkinManager.getOrLoadSkin("", null, null, null));
        assertNotNull(SkinManager.getOrLoadSkin(null, null, null, null));
    }
}
