package me.goosbanny.goosboards.raycast;

import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.Objects;
import java.util.UUID;

/**
 * Immutable DTO capturing all player state required for raycasting on the owning region thread.
 * Prevents race conditions and thread-safety violations on Folia / Paper off-thread worker pools.
 */
public record PlayerRaycastSnapshot(
        UUID playerId,
        UUID worldId,
        String worldName,
        Vector3d eyePosition,
        Vector3d lookDirection,
        boolean isSneaking
) {
    public PlayerRaycastSnapshot {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(eyePosition, "eyePosition");
        Objects.requireNonNull(lookDirection, "lookDirection");
    }

    /**
     * Snapshots the player's spatial state synchronously on their owning region thread.
     */
    public static PlayerRaycastSnapshot from(Player player) {
        Objects.requireNonNull(player, "player");
        Location eyeLoc = player.getEyeLocation();
        UUID worldId = eyeLoc.getWorld() != null ? eyeLoc.getWorld().getUID() : null;
        String worldName = eyeLoc.getWorld() != null ? eyeLoc.getWorld().getName() : "world";

        Vector3d eyePos = new Vector3d(eyeLoc.getX(), eyeLoc.getY(), eyeLoc.getZ());

        // Fast trigonometric conversion from yaw/pitch to unit look vector
        double yawRad = Math.toRadians(eyeLoc.getYaw());
        double pitchRad = Math.toRadians(eyeLoc.getPitch());
        double xz = Math.cos(pitchRad);
        double dx = -xz * Math.sin(yawRad);
        double dy = -Math.sin(pitchRad);
        double dz = xz * Math.cos(yawRad);
        Vector3d lookDir = new Vector3d(dx, dy, dz);

        return new PlayerRaycastSnapshot(
                player.getUniqueId(),
                worldId,
                worldName,
                eyePos,
                lookDir,
                player.isSneaking()
        );
    }
}
