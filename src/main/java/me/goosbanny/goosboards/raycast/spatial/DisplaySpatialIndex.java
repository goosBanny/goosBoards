package me.goosbanny.goosboards.raycast.spatial;

import me.goosbanny.goosboards.display.DisplayPlane;
import me.goosbanny.goosboards.raycast.Vector3d;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface DisplaySpatialIndex {
    List<DisplayPlane> nearbyBoards(Vector3d position, double maxRadius, String worldName);

    default void nearbyBoards(Vector3d position, double maxRadius, String worldName, List<DisplayPlane> outResult, Set<UUID> seenScratch) {
        if (outResult != null) {
            outResult.clear();
            outResult.addAll(nearbyBoards(position, maxRadius, worldName));
        }
    }

    void register(DisplayPlane plane);
    void unregister(UUID displayId);
    DisplayPlane getPlane(UUID displayId);
}
