package me.goosbanny.goosboards.command;

import me.goosbanny.goosboards.config.ConfigReloadManager;
import me.goosbanny.goosboards.display.DisplayPlane;
import me.goosbanny.goosboards.raycast.DisplayRaycaster;
import me.goosbanny.goosboards.raycast.MutableRaycastHit;
import me.goosbanny.goosboards.raycast.Vector3d;
import me.goosbanny.goosboards.raycast.spatial.DisplaySpatialIndex;
import me.goosbanny.goosboards.scene.BoardConfig;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Shared utility methods for command argument parsing and raycast lookups.
 */
public final class CommandUtils {

    private CommandUtils() {
    }

    public static Set<String> extractFlags(String[] args) {
        Set<String> flags = new HashSet<>();
        for (String arg : args) {
            if (arg.startsWith("--") && arg.length() > 2) {
                flags.add(arg.substring(2).toLowerCase(Locale.ROOT));
            } else if (arg.startsWith("-") && arg.length() > 1 && !arg.startsWith("--")) {
                flags.add(arg.substring(1).toLowerCase(Locale.ROOT));
            }
        }
        return flags;
    }

    public static List<String> filterPrefix(List<String> list, String prefix) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        List<String> result = new ArrayList<>();
        for (String item : list) {
            if (item.toLowerCase(Locale.ROOT).startsWith(lower)) {
                result.add(item);
            }
        }
        return result;
    }

    public static String sanitizeBoardName(String raw) {
        if (raw == null) return "";
        return raw.replaceAll("[^a-zA-Z0-9_\\-]", "_").toLowerCase(Locale.ROOT);
    }

    public static DisplayPlane raycastPlayerLook(Player player, DisplaySpatialIndex spatialIndex) {
        Location eye = player.getEyeLocation();
        double eyeX = eye.getX(), eyeY = eye.getY(), eyeZ = eye.getZ();
        Vector dir = eye.getDirection();
        double dirX = dir.getX(), dirY = dir.getY(), dirZ = dir.getZ();

        Vector3d pos = new Vector3d(eyeX, eyeY, eyeZ);
        List<DisplayPlane> nearby = spatialIndex.nearbyBoards(pos, 32.0, player.getWorld().getName());
        if (nearby.isEmpty()) return null;

        MutableRaycastHit bestHit = new MutableRaycastHit();
        MutableRaycastHit scratch = new MutableRaycastHit();
        DisplayPlane bestPlane = null;

        for (DisplayPlane plane : nearby) {
            DisplayRaycaster.intersect(plane, eyeX, eyeY, eyeZ, dirX, dirY, dirZ, scratch);
            if (scratch.hit && (!bestHit.hit || scratch.distance < bestHit.distance)) {
                bestHit.set(scratch.displayId, scratch.pixelX, scratch.pixelY, scratch.distance);
                bestPlane = plane;
            }
        }

        return bestPlane;
    }

    public static String resolveDisplayLabel(UUID displayId, ConfigReloadManager reloadManager) {
        if (displayId == null) return "?";
        for (BoardConfig board : reloadManager.getActiveBoards().values()) {
            for (Map.Entry<String, BoardConfig.DisplayDefinition> e : board.displays().entrySet()) {
                UUID candidate = ConfigReloadManager.getDisplayUuid(
                        reloadManager.getBoardIdForDisplay(displayId) != null
                                ? reloadManager.getBoardIdForDisplay(displayId) : "",
                        e.getKey()
                );
                if (displayId.equals(candidate)) {
                    return e.getKey();
                }
            }
        }
        return displayId.toString().substring(0, 8);
    }
}
