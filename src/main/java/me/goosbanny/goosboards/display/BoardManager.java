package me.goosbanny.goosboards.display;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

/**
 * Domain manager interface responsible for board registries and display plane lookups.
 */
public interface BoardManager {

    /**
     * Retrieves an active board by its ID.
     *
     * @param boardId the board ID
     * @return an Optional containing the Board if present
     */
    Optional<Board> getBoard(String boardId);

    /**
     * @return all currently registered boards
     */
    Collection<Board> getAllBoards();

    /**
     * Resolves the board ID that owns the specified display plane.
     *
     * @param displayId display plane UUID
     * @return the board ID, or null if unassociated
     */
    String getBoardIdForDisplay(UUID displayId);
}
