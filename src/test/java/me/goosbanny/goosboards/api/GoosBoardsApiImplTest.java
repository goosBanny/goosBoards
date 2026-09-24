package me.goosbanny.goosboards.api;

import me.goosbanny.goosboards.api.impl.GoosBoardsApiImpl;
import me.goosbanny.goosboards.config.ConfigReloadManager;
import me.goosbanny.goosboards.render.BoardRenderEngine;
import me.goosbanny.goosboards.scene.BoardConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GoosBoardsApiImplTest {

    @Test
    @DisplayName("GoosBoardsApi delegates to ConfigReloadManager and BoardRenderEngine")
    void testApiDelegates() {
        ConfigReloadManager reloadManager = Mockito.mock(ConfigReloadManager.class);
        BoardRenderEngine renderEngine = Mockito.mock(BoardRenderEngine.class);
        when(reloadManager.getRenderEngine()).thenReturn(renderEngine);

        BoardConfig board = Mockito.mock(BoardConfig.class);
        when(reloadManager.getActiveBoards()).thenReturn(Map.of("hub-board", board));

        GoosBoardsApi api = new GoosBoardsApiImpl(reloadManager);

        assertTrue(api.hasBoard("hub-board"));
        assertFalse(api.hasBoard("other-board"));
        assertEquals(1, api.getLoadedBoardIds().size());

        api.requestRedraw("hub-board");
        verify(renderEngine).requestDirtyPass("hub-board");

        api.requestFullRedrawAll();
        verify(renderEngine).forceFullRedraw("hub-board");
    }
}
