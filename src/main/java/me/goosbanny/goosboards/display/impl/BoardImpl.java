package me.goosbanny.goosboards.display.impl;

import me.goosbanny.goosboards.display.Board;
import me.goosbanny.goosboards.display.DisplayPlane;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Standard thread-safe implementation of Board domain entity.
 */
public class BoardImpl implements Board {

    private final String id;
    private final Map<UUID, DisplayPlane> planes = new ConcurrentHashMap<>();

    public BoardImpl(String id, Collection<DisplayPlane> initialPlanes) {
        this.id = Objects.requireNonNull(id, "id");
        if (initialPlanes != null) {
            for (DisplayPlane plane : initialPlanes) {
                this.planes.put(plane.id(), plane);
            }
        }
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public Collection<DisplayPlane> getPlanes() {
        return Collections.unmodifiableCollection(planes.values());
    }

    @Override
    public DisplayPlane getPlane(UUID displayId) {
        return displayId != null ? planes.get(displayId) : null;
    }
}
