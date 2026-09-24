package me.goosbanny.goosboards.api;

import java.util.Collection;
import java.util.UUID;

/**
 * Public service interface for interacting with GoosBoards from third-party plugins.
 */
public interface GoosBoardsApi {

    /**
     * Gets the names of all currently loaded boards.
     *
     * @return an immutable collection of board IDs
     */
    Collection<String> getLoadedBoardIds();

    /**
     * Checks if a board with the given ID is loaded.
     *
     * @param boardId the board ID to check
     * @return true if loaded, false otherwise
     */
    boolean hasBoard(String boardId);

    /**
     * Triggers a dirty render pass for all displays belonging to the specified board.
     *
     * @param boardId the board ID
     */
    void requestRedraw(String boardId);

    /**
     * Triggers a full redraw for all boards and flushes tiles to all active viewers.
     */
    void requestFullRedrawAll();
}
