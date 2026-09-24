package me.goosbanny.goosboards.display;

import org.bukkit.entity.Player;
import java.util.UUID;

/**
 * Immutable snapshot of a player's spatial location, direction, and metadata,
 * captured on the primary/async tick for thread-safe consumption by raycasting,
 * proximity tracking, and render engines.
 */
public record PlayerSnapshot(
        UUID id,
        String name,
        String worldName,
        double x,
        double y,
        double z,
        double eyeHeight,
        double dirX,
        double dirY,
        double dirZ,
        int ticksLived,
        Player player
) {
    public PlayerSnapshot(
            UUID id,
            String name,
            String worldName,
            double x,
            double y,
            double z,
            double eyeHeight,
            double dirX,
            double dirY,
            double dirZ,
            Player player
    ) {
        this(id, name, worldName, x, y, z, eyeHeight, dirX, dirY, dirZ, 0, player);
    }

    public PlayerSnapshot(
            UUID id,
            String name,
            String worldName,
            double x,
            double y,
            double z,
            double eyeHeight,
            Player player
    ) {
        this(id, name, worldName, x, y, z, eyeHeight, 0.0, 0.0, 1.0, 0, player);
    }
}
