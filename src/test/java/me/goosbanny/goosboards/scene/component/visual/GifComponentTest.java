package me.goosbanny.goosboards.scene.component.visual;

import me.goosbanny.goosboards.media.DiskMediaCache;
import me.goosbanny.goosboards.scene.BoardConfig;
import me.goosbanny.goosboards.scene.Rect;
import me.goosbanny.goosboards.scene.RenderContext;
import me.goosbanny.goosboards.scene.component.UIComponent;
import me.goosbanny.goosboards.scene.parser.BoardYamlParser;

import me.goosbanny.goosboards.render.buffer.CanvasBuffer;
import me.goosbanny.goosboards.render.buffer.CanvasBufferImpl;
import me.goosbanny.goosboards.media.security.SafeMediaLoader;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.StringReader;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GifComponentTest {

    @Test
    @DisplayName("GifComponent is always dynamic to enable per-tick frame rendering")
    void testGifIsDynamic() {
        GifComponent gif = new GifComponent("banner");
        assertTrue(gif.isDynamic(), "GIF components must be dynamic so scenes re-render every tick");
    }

    @Test
    @DisplayName("GifComponent parses properties from YAML definition")
    void testGifYamlParsing() throws Exception {
        String yaml = """
                scenes:
                  default:
                    animated-banner:
                      type: gif
                      position: "10 20"
                      size: "256 128"
                      image: "fuzzy_small.gif"
                      cache-behavior: "global"
                      fps: 12.5
                      frame-delay-ms: 80
                      speed: 1.25
                      loop: true
                """;

        YamlConfiguration config = new YamlConfiguration();
        config.load(new StringReader(yaml));

        BoardYamlParser parser = new BoardYamlParser();
        var scenes = parser.parseScenes(config.getConfigurationSection("scenes"), 512, 512);

        assertNotNull(scenes);
        BoardConfig.SceneDefinition def = scenes.get("default");
        assertNotNull(def);
        assertEquals(1, def.components().size());

        UIComponent comp = def.components().get(0);
        assertInstanceOf(GifComponent.class, comp);

        GifComponent gif = (GifComponent) comp;
        assertEquals("fuzzy_small.gif", gif.getImageName());
        assertEquals("global", gif.getCacheBehavior());
        assertEquals(12.5, gif.getFps(), 0.001);
        assertEquals(80, gif.getFrameDelayMs());
        assertEquals(1.25, gif.getSpeedMultiplier(), 0.001);
        assertTrue(gif.isLoop());
        assertTrue(gif.isDynamic());
    }

    @Test
    @DisplayName("GifComponent global framerate limits can be tuned")
    void testGlobalSettings() {
        GifComponent.setGlobalSettings(15, 8);
        GifComponent gif = new GifComponent("test");
        gif.setFps(25.0);
        assertEquals(25.0, gif.getFps());
    }

    @Test
    @DisplayName("GifComponent containsPixel tests bounding box")
    void testContainsPixel() {
        GifComponent gif = new GifComponent("test");
        gif.setBounds(new Rect(10, 10, 100, 100));
        assertTrue(gif.containsPixel(50, 50));
        assertTrue(gif.containsPixel(0, 0));
        assertTrue(gif.containsPixel(99, 99));
        assertFalse(gif.containsPixel(-1, 50));
        assertFalse(gif.containsPixel(100, 50));
    }

    @Test
    @DisplayName("GifComponent supports HTTP/HTTPS URLs with SafeMediaLoader and DiskMediaCache")
    void testRemoteGifUrl() {
        SafeMediaLoader mockLoader = Mockito.mock(SafeMediaLoader.class);
        Mockito.when(mockLoader.loadSecureImage(Mockito.any(), Mockito.anyInt(), Mockito.anyInt()))
                .thenReturn(CompletableFuture.completedFuture(new byte[0]));

        GifComponent.setSafeMediaLoader(mockLoader);

        GifComponent gif = new GifComponent("url-gif");
        gif.setImageName("https://media.example.com/test.gif");
        gif.setBounds(new Rect(0, 0, 64, 64));

        CanvasBuffer canvas = new CanvasBufferImpl(1, 1);
        gif.render(canvas, RenderContext.empty());

        assertEquals("https://media.example.com/test.gif", gif.getImageName());
    }

    @Test
    @DisplayName("Extracts direct GIF media URL from HTML metadata (og:image, twitter:image, Tenor JSON)")
    void testExtractDirectMediaUrlFromTenorHtml() {
        String ogHtml = """
                <!DOCTYPE html>
                <html>
                <head>
                <meta property="og:image" content="https://media1.tenor.com/m/bRrcX_image.gif">
                <title>Tenor GIF</title>
                </head>
                </html>
                """;
        assertEquals("https://media1.tenor.com/m/bRrcX_image.gif",
                GifComponent.extractDirectMediaUrlFromHtml(ogHtml));

        String twitterHtml = """
                <meta name="twitter:image" content="https://media.tenor.com/m/twitter.gif">
                """;
        assertEquals("https://media.tenor.com/m/twitter.gif",
                GifComponent.extractDirectMediaUrlFromHtml(twitterHtml));

        String tenorJsonHtml = """
                {"gif":{"url":"https:\\/\\/media1.tenor.com\\/m\\/direct.gif"}}
                """;
        assertEquals("https://media1.tenor.com/m/direct.gif",
                GifComponent.extractDirectMediaUrlFromHtml(tenorJsonHtml));

        String regularHtml = "<html><body>No image here</body></html>";
        assertNull(GifComponent.extractDirectMediaUrlFromHtml(regularHtml));
    }

    @Test
    @DisplayName("Framerate resampling preserves total animation duration without speed distortion")
    void testDurationPreservingResampling() {
        // 10 frames of 100ms each = 1000ms native duration
        List<Integer> delays = List.of(100, 100, 100, 100, 100, 100, 100, 100, 100, 100);

        // Resample at 5 FPS -> 5 frames of 200ms
        List<GifComponent.FrameSample> samples5fps = GifComponent.computeFrameSamples(delays, 5.0, 0, 20);
        assertEquals(5, samples5fps.size());
        int total5fps = samples5fps.stream().mapToInt(GifComponent.FrameSample::delayMs).sum();
        assertEquals(1000, total5fps, "Total duration must match exactly 1000ms");

        // Resample at 2 FPS -> 2 frames of 500ms
        List<GifComponent.FrameSample> samples2fps = GifComponent.computeFrameSamples(delays, 2.0, 0, 20);
        assertEquals(2, samples2fps.size());
        int total2fps = samples2fps.stream().mapToInt(GifComponent.FrameSample::delayMs).sum();
        assertEquals(1000, total2fps, "Total duration must match exactly 1000ms");
    }

    @Test
    @DisplayName("Sub-20ms delays are clamped to browser-standard 100ms to prevent hyper-speed playback")
    void testClampingSub20msDelays() {
        // Delays of 0ms or 10ms
        List<Integer> buggyDelays = List.of(0, 10, 0, 5, 0);
        List<GifComponent.FrameSample> samples = GifComponent.computeFrameSamples(buggyDelays, 0.0, 0, 20);

        assertEquals(5, samples.size());
        for (GifComponent.FrameSample sample : samples) {
            assertTrue(sample.delayMs() >= 50, "Frame delay must be at least minAllowedDelay (50ms)");
        }
        int totalDuration = samples.stream().mapToInt(GifComponent.FrameSample::delayMs).sum();
        assertEquals(500, totalDuration, "Each sub-20ms frame is clamped to 100ms, total = 500ms");
    }
}