package me.goosbanny.goosboards.api.impl;

import me.goosbanny.goosboards.api.GoosBoardsApi;
import me.goosbanny.goosboards.config.ConfigReloadManager;
import me.goosbanny.goosboards.render.BoardRenderEngine;

import java.util.Collection;
import java.util.Collections;
import java.util.Objects;

/**
 * Production implementation of the public GoosBoardsApi service.
 */
public class GoosBoardsApiImpl implements GoosBoardsApi {

    private final ConfigReloadManager reloadManager;

    public GoosBoardsApiImpl(ConfigReloadManager reloadManager) {
        this.reloadManager = Objects.requireNonNull(reloadManager, "reloadManager");
    }

    @Override
    public Collection<String> getLoadedBoardIds() {
        return Collections.unmodifiableCollection(reloadManager.getActiveBoards().keySet());
    }

    @Override
    public boolean hasBoard(String boardId) {
        return boardId != null && reloadManager.getActiveBoards().containsKey(boardId);
    }

    @Override
    public void requestRedraw(String boardId) {
        BoardRenderEngine engine = reloadManager.getRenderEngine();
        if (engine != null && boardId != null) {
            engine.requestDirtyPass(boardId);
        }
    }

    @Override
    public void requestFullRedrawAll() {
        BoardRenderEngine engine = reloadManager.getRenderEngine();
        if (engine != null) {
            for (String boardId : reloadManager.getActiveBoards().keySet()) {
                engine.forceFullRedraw(boardId);
            }
        }
    }
}
