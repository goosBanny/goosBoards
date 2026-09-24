package me.goosbanny.goosboards.display;

import me.goosbanny.goosboards.config.ConfigReloadManager;
import me.goosbanny.goosboards.display.impl.DefaultBoardManager;
import me.goosbanny.goosboards.raycast.Vector3d;
import me.goosbanny.goosboards.raycast.spatial.DisplaySpatialIndex;
import me.goosbanny.goosboards.scene.BoardConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

class DefaultBoardManagerTest {

    @Test
    @DisplayName("DefaultBoardManager looks up board and resolves planes")
    void testBoardLookup() {
        ConfigReloadManager reloadManager = Mockito.mock(ConfigReloadManager.class);
        DisplaySpatialIndex spatialIndex = Mockito.mock(DisplaySpatialIndex.class);

        BoardConfig.DisplayDefinition dispDef = Mockito.mock(BoardConfig.DisplayDefinition.class);
        BoardConfig config = new BoardConfig(
                new BoardConfig.BoardSettings(true, true, false, true, true, true, -1),
                Map.of("main", dispDef),
                Map.of()
        );

        when(reloadManager.getBoard("test-board")).thenReturn(config);
        when(reloadManager.getActiveBoards()).thenReturn(Map.of("test-board", config));

        UUID dispUuid = ConfigReloadManager.getDisplayUuid("test-board", "main");
        DisplayPlane plane = new DisplayPlane(dispUuid, "test-board", new Vector3d(0, 64, 0), 4, 3);
        when(spatialIndex.getPlane(dispUuid)).thenReturn(plane);

        DefaultBoardManager manager = new DefaultBoardManager(reloadManager, spatialIndex);

        Optional<Board> boardOpt = manager.getBoard("test-board");
        assertTrue(boardOpt.isPresent());
        Board board = boardOpt.get();
        assertEquals("test-board", board.getId());
        assertEquals(1, board.getPlanes().size());
        assertEquals(plane, board.getPlane(dispUuid));

        assertEquals(1, manager.getAllBoards().size());
        assertTrue(manager.getBoard("nonexistent").isEmpty());
    }
}
