package me.goosbanny.goosboards.display;

import java.util.Collection;
import java.util.UUID;

/**
 * Domain model representing an active multi-plane interactive board.
 */
public interface Board {

    /**
     * @return the unique board identifier
     */
    String getId();

    /**
     * @return all display planes associated with this board
     */
    Collection<DisplayPlane> getPlanes();

    /**
     * Finds a display plane by its UUID.
     *
     * @param displayId display plane unique identifier
     * @return the display plane or null if not found
     */
    DisplayPlane getPlane(UUID displayId);
}
