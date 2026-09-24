package me.goosbanny.goosboards.command.subcommand;

import me.goosbanny.goosboards.command.CommandUtils;
import me.goosbanny.goosboards.command.SubCommand;
import me.goosbanny.goosboards.config.ConfigReloadManager;
import me.goosbanny.goosboards.core.logging.MessageService;
import me.goosbanny.goosboards.display.DisplayPlane;
import me.goosbanny.goosboards.raycast.DisplayRaycaster;
import me.goosbanny.goosboards.raycast.MutableRaycastHit;
import me.goosbanny.goosboards.raycast.Vector3d;
import me.goosbanny.goosboards.raycast.spatial.DisplaySpatialIndex;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Handles /gb coordinates and alias coords.
 */
public class CoordinatesSubCommand implements SubCommand {

    private final ConfigReloadManager reloadManager;
    private final DisplaySpatialIndex spatialIndex;
    private final MessageService messageService;

    public CoordinatesSubCommand(ConfigReloadManager reloadManager, DisplaySpatialIndex spatialIndex, MessageService messageService) {
        this.reloadManager = Objects.requireNonNull(reloadManager, "reloadManager");
        this.spatialIndex = Objects.requireNonNull(spatialIndex, "spatialIndex");
        this.messageService = Objects.requireNonNull(messageService, "messageService");
    }

    @Override
    public String getName() {
        return "coordinates";
    }

    @Override
    public List<String> getAliases() {
        return List.of("coords");
    }

    @Override
    public void execute(CommandSender sender, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            messageService.send(sender, "selection-player-only");
            return;
        }

        Location eye = player.getEyeLocation();
        double eyeX = eye.getX(), eyeY = eye.getY(), eyeZ = eye.getZ();
        Vector dir = eye.getDirection();
        double dirX = dir.getX(), dirY = dir.getY(), dirZ = dir.getZ();

        Vector3d pos = new Vector3d(eyeX, eyeY, eyeZ);
        List<DisplayPlane> nearby = spatialIndex.nearbyBoards(pos, 32.0, player.getWorld().getName());
        if (nearby.isEmpty()) {
            messageService.send(sender, "coordinates-none");
            return;
        }

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

        if (!bestHit.hit || bestPlane == null) {
            messageService.send(sender, "coordinates-none");
            return;
        }

        int tileX = (int) bestHit.pixelX / 128;
        int tileY = (int) bestHit.pixelY / 128;

        String displayLabel = CommandUtils.resolveDisplayLabel(bestHit.displayId, reloadManager);
        messageService.send(sender, "coordinates-result", Map.of(
                "display", displayLabel,
                "pixel_x", String.valueOf((int) bestHit.pixelX),
                "pixel_y", String.valueOf((int) bestHit.pixelY),
                "tile_x", String.valueOf(tileX),
                "tile_y", String.valueOf(tileY)
        ));
    }
}
