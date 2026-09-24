package me.goosbanny.goosboards.display;

import org.bukkit.Location;
import org.bukkit.World;

import java.util.Objects;
import java.util.UUID;

/**
 * Immutable memory-leak safe coordinate record storing primitive coordinates and world UUID.
 * NEVER holds a strong reference to Bukkit World or Location instances.
 */
public record DisplayPos(UUID worldId, int x, int y, int z) {

    public DisplayPos {
        // worldId can be null if initialized from un-instantiated worlds in unit tests
    }

    public static DisplayPos from(Location loc) {
        if (loc == null) {
            return null;
        }
        World world = loc.getWorld();
        UUID worldId = world != null ? world.getUID() : null;
        return new DisplayPos(worldId, loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
    }

    public static DisplayPos from(UUID worldId, int x, int y, int z) {
        return new DisplayPos(worldId, x, y, z);
    }

    public boolean matchesWorld(World world) {
        if (world == null || worldId == null) {
            return false;
        }
        return world.getUID().equals(worldId);
    }

    public boolean matchesWorld(UUID otherWorldId) {
        return Objects.equals(this.worldId, otherWorldId);
    }
}
