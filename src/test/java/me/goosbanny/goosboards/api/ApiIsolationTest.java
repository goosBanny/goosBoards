package me.goosbanny.goosboards.api;

import me.goosbanny.goosboards.api.event.BoardClickEvent;
import me.goosbanny.goosboards.api.event.BoardHoverEvent;
import me.goosbanny.goosboards.api.event.BoardSceneChangeEvent;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class ApiIsolationTest {

    private static final String[] FORBIDDEN_PACKAGES = {
            "me.goosbanny.goosboards.protocol",
            "me.goosbanny.goosboards.render",
            "me.goosbanny.goosboards.scene",
            "me.goosbanny.goosboards.interaction",
            "me.goosbanny.goosboards.media",
            "me.goosbanny.goosboards.storage",
            "me.goosbanny.goosboards.command",
            "me.goosbanny.goosboards.config",
            "me.goosbanny.goosboards.display",
            "me.goosbanny.goosboards.raycast",
            "me.goosbanny.goosboards.core"
    };

    @Test
    @DisplayName("API package has zero imports or dependencies on internal plugin packages")
    void testApiIsolationFromInternalPackages() throws IOException {
        File apiDir = new File("src/main/java/me/goosbanny/goosboards/api");
        assertTrue(apiDir.exists() && apiDir.isDirectory(), "API directory must exist");

        File[] files = apiDir.listFiles((dir, name) -> name.endsWith(".java"));
        assertNotNull(files, "API source files must not be null");
        assertTrue(files.length > 0, "API directory must contain java sources");

        for (File file : files) {
            List<String> lines = Files.readAllLines(file.toPath());
            for (String line : lines) {
                String trimmed = line.trim();
                if (trimmed.startsWith("import ")) {
                    for (String forbidden : FORBIDDEN_PACKAGES) {
                        assertFalse(trimmed.contains(forbidden),
                                "API file " + file.getName() + " contains forbidden internal import: " + trimmed);
                    }
                }
            }
        }
    }

    @Test
    @DisplayName("Mock third-party addon compiles and registers listeners using only public SPI")
    void testThirdPartyAddonUsage() {
        StubAddonListener listener = new StubAddonListener();
        assertNotNull(listener);
        assertFalse(listener.clickHandled);
        assertFalse(listener.hoverHandled);
        assertFalse(listener.sceneChangeHandled);

        Player mockPlayer = mock(Player.class);
        BoardClickEvent click = new BoardClickEvent(mockPlayer, "s1", "d1", 0, 0, ClickType.LEFT_CLICK);
        BoardHoverEvent hover = new BoardHoverEvent(mockPlayer, "s1", 0, 0);
        BoardSceneChangeEvent change = new BoardSceneChangeEvent(mockPlayer, "b1", "s1", "s2");

        listener.onBoardClick(click);
        listener.onBoardHover(hover);
        listener.onSceneChange(change);

        assertTrue(listener.clickHandled);
        assertTrue(listener.hoverHandled);
        assertTrue(listener.sceneChangeHandled);
    }

    /**
     * Standalone stub addon listener proving SPI usability without internal classes.
     */
    public static class StubAddonListener implements Listener {
        public boolean clickHandled = false;
        public boolean hoverHandled = false;
        public boolean sceneChangeHandled = false;

        @EventHandler
        public void onBoardClick(BoardClickEvent event) {
            clickHandled = true;
            if (event.getClickType() == ClickType.RIGHT_CLICK) {
                event.setCancelled(true);
            }
        }

        @EventHandler
        public void onBoardHover(BoardHoverEvent event) {
            hoverHandled = true;
        }

        @EventHandler
        public void onSceneChange(BoardSceneChangeEvent event) {
            sceneChangeHandled = true;
        }
    }
}
