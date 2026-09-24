package me.goosbanny.goosboards.display.impl;

import me.goosbanny.goosboards.config.ConfigReloadManager;
import me.goosbanny.goosboards.display.Board;
import me.goosbanny.goosboards.display.BoardManager;
import me.goosbanny.goosboards.display.DisplayPlane;
import me.goosbanny.goosboards.raycast.spatial.DisplaySpatialIndex;
import me.goosbanny.goosboards.scene.BoardConfig;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Default implementation of BoardManager bridging ConfigReloadManager and DisplaySpatialIndex.
 */
public class DefaultBoardManager implements BoardManager {

    private final ConfigReloadManager reloadManager;
    private final DisplaySpatialIndex spatialIndex;

    public DefaultBoardManager(ConfigReloadManager reloadManager, DisplaySpatialIndex spatialIndex) {
        this.reloadManager = Objects.requireNonNull(reloadManager, "reloadManager");
        this.spatialIndex = Objects.requireNonNull(spatialIndex, "spatialIndex");
    }

    @Override
    public Optional<Board> getBoard(String boardId) {
        if (boardId == null) return Optional.empty();
        BoardConfig config = reloadManager.getBoard(boardId);
        if (config == null) return Optional.empty();

        List<DisplayPlane> planes = new ArrayList<>();
        for (String dId : config.displays().keySet()) {
            UUID u = ConfigReloadManager.getDisplayUuid(boardId, dId);
            DisplayPlane plane = spatialIndex.getPlane(u);
            if (plane != null) {
                planes.add(plane);
            }
        }
        return Optional.of(new BoardImpl(boardId, planes));
    }

    @Override
    public Collection<Board> getAllBoards() {
        List<Board> boards = new ArrayList<>();
        for (String bId : reloadManager.getActiveBoards().keySet()) {
            getBoard(bId).ifPresent(boards::add);
        }
        return boards;
    }

    @Override
    public String getBoardIdForDisplay(UUID displayId) {
        return reloadManager.getBoardIdForDisplay(displayId);
    }
}
