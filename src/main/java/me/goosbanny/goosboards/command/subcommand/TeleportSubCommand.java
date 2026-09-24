package me.goosbanny.goosboards.command.subcommand;

import me.goosbanny.goosboards.command.CommandUtils;
import me.goosbanny.goosboards.command.SubCommand;
import me.goosbanny.goosboards.config.ConfigReloadManager;
import me.goosbanny.goosboards.core.logging.MessageService;
import me.goosbanny.goosboards.display.DisplayPlane;
import me.goosbanny.goosboards.raycast.Vector3d;
import me.goosbanny.goosboards.raycast.spatial.DisplaySpatialIndex;
import me.goosbanny.goosboards.scene.BoardConfig;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Handles /gb teleport <display> and alias tp.
 */
public class TeleportSubCommand implements SubCommand {

    private final ConfigReloadManager reloadManager;
    private final DisplaySpatialIndex spatialIndex;
    private final MessageService messageService;

    public TeleportSubCommand(ConfigReloadManager reloadManager, DisplaySpatialIndex spatialIndex, MessageService messageService) {
        this.reloadManager = Objects.requireNonNull(reloadManager, "reloadManager");
        this.spatialIndex = Objects.requireNonNull(spatialIndex, "spatialIndex");
        this.messageService = Objects.requireNonNull(messageService, "messageService");
    }

    @Override
    public String getName() {
        return "teleport";
    }

    @Override
    public List<String> getAliases() {
        return List.of("tp");
    }

    @Override
    public void execute(CommandSender sender, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            messageService.send(sender, "selection-player-only");
            return;
        }
        if (args.length < 1 || args[0].startsWith("-")) {
            messageService.send(sender, "unknown-command", Map.of("sub", "teleport <display>", "label", label));
            return;
        }

        String displayId = args[0];
        String boardId = null;
        BoardConfig.DisplayDefinition displayDef = null;

        for (Map.Entry<String, BoardConfig> entry : reloadManager.getActiveBoards().entrySet()) {
            BoardConfig.DisplayDefinition d = entry.getValue().displays().get(displayId);
            if (d != null) {
                boardId = entry.getKey();
                displayDef = d;
                break;
            }
        }

        if (displayDef == null) {
            messageService.send(sender, "display-not-found", Map.of("display", displayId));
            return;
        }

        UUID dispUuid = ConfigReloadManager.getDisplayUuid(boardId, displayId);
        DisplayPlane plane = spatialIndex.getPlane(dispUuid);
        if (plane == null) {
            messageService.send(sender, "display-not-found", Map.of("display", displayId));
            return;
        }

        Vector3d center = plane.center();
        Vector3d normal = plane.normal();
        double tpX = center.x() + normal.x() * 3.0;
        double tpY = center.y();
        double tpZ = center.z() + normal.z() * 3.0;

        double dX = center.x() - tpX;
        double dZ = center.z() - tpZ;
        float yaw = (float) (Math.toDegrees(Math.atan2(-dX, dZ)));
        float pitch = (float) Math.toDegrees(Math.atan2(-(center.y() - tpY), Math.sqrt(dX * dX + dZ * dZ)));

        Location loc = new Location(Bukkit.getWorld(displayDef.world()), tpX, tpY, tpZ, yaw, pitch);
        if (loc.getWorld() == null) {
            messageService.send(sender, "display-not-found", Map.of("display", displayId));
            return;
        }
        player.teleport(loc);
        messageService.send(sender, "teleport-success", Map.of("display", displayId, "board", boardId));
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 1) {
            List<String> displayIds = new ArrayList<>();
            for (BoardConfig b : reloadManager.getActiveBoards().values()) {
                displayIds.addAll(b.displays().keySet());
            }
            return CommandUtils.filterPrefix(displayIds, args[0]);
        }
        return List.of();
    }
}
