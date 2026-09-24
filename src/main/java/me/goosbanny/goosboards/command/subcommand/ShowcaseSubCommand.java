package me.goosbanny.goosboards.command.subcommand;

import me.goosbanny.goosboards.command.BoardSelectionManager;
import me.goosbanny.goosboards.command.CommandUtils;
import me.goosbanny.goosboards.command.SubCommand;
import me.goosbanny.goosboards.config.ConfigReloadManager;
import me.goosbanny.goosboards.core.logging.MessageService;
import me.goosbanny.goosboards.raycast.Vector3d;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.util.RayTraceResult;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Handles /gb showcase [name].
 */
public class ShowcaseSubCommand implements SubCommand {

    private final ConfigReloadManager reloadManager;
    private final BoardSelectionManager selectionManager;
    private final MessageService messageService;
    private final File boardsFolder;

    public ShowcaseSubCommand(
            ConfigReloadManager reloadManager,
            BoardSelectionManager selectionManager,
            MessageService messageService,
            File boardsFolder
    ) {
        this.reloadManager = Objects.requireNonNull(reloadManager, "reloadManager");
        this.selectionManager = Objects.requireNonNull(selectionManager, "selectionManager");
        this.messageService = Objects.requireNonNull(messageService, "messageService");
        this.boardsFolder = Objects.requireNonNull(boardsFolder, "boardsFolder");
    }

    @Override
    public String getName() {
        return "showcase";
    }

    @Override
    public void execute(CommandSender sender, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            messageService.send(sender, "selection-player-only");
            return;
        }

        String boardName = args.length > 0 && !args[0].startsWith("-")
                ? CommandUtils.sanitizeBoardName(args[0])
                : "showcase";
        if (boardName.isBlank()) {
            boardName = "showcase";
        }

        UUID uuid = player.getUniqueId();
        BoardSelectionManager.SelectionSession session = selectionManager.getSession(uuid);
        BoardSelectionManager.SelectionGeometry geo;

        if (session != null && session.getCorner1() != null && session.getCorner2() != null) {
            geo = selectionManager.computeGeometry(session.getCorner1(), session.getCorner2());
        } else {
            geo = computeAutoShowcaseGeometry(player);
        }

        if (geo == null) {
            messageService.send(sender, "selection-world-mismatch");
            return;
        }

        File boardFile = new File(boardsFolder, boardName + ".yml");
        try {
            writeShowcaseTemplate(boardFile, boardName, geo);
        } catch (IOException e) {
            messageService.send(sender, "board-create-failed", Map.of("error", e.getMessage() != null ? e.getMessage() : "IO error"));
            return;
        }

        selectionManager.finalizeSession(uuid);
        reloadManager.reload();

        sender.sendMessage(String.format("§a[GoosBoards] Created 8x8 interactive showcase board '§f%s§a' facing §f%s§a!",
                boardName, geo.directionLabel()));
        messageService.sendActionBar(player, "board-created-actionbar", Map.of("board", boardName));
    }

    private BoardSelectionManager.SelectionGeometry computeAutoShowcaseGeometry(Player player) {
        Location loc = player.getLocation();
        String world = player.getWorld().getName();

        RayTraceResult rayTrace = null;
        try {
            rayTrace = player.rayTraceBlocks(25.0);
        } catch (Throwable ignored) {
        }

        Block targetBlock = rayTrace != null ? rayTrace.getHitBlock() : null;
        BlockFace hitFace = rayTrace != null ? rayTrace.getHitBlockFace() : null;

        int bx, by, bz;
        String direction;

        if (targetBlock != null) {
            bx = targetBlock.getX();
            by = targetBlock.getY();
            bz = targetBlock.getZ();

            if (hitFace == BlockFace.NORTH || hitFace == BlockFace.SOUTH || hitFace == BlockFace.EAST || hitFace == BlockFace.WEST) {
                direction = hitFace.name().toLowerCase(Locale.ROOT);
            } else {
                float yaw = (loc.getYaw() % 360 + 360) % 360;
                if (yaw >= 315 || yaw < 45) {
                    direction = "north";
                } else if (yaw >= 45 && yaw < 135) {
                    direction = "east";
                } else if (yaw >= 135 && yaw < 225) {
                    direction = "south";
                } else {
                    direction = "west";
                }
            }
        } else {
            float yaw = (loc.getYaw() % 360 + 360) % 360;
            int px = loc.getBlockX();
            int py = loc.getBlockY();
            int pz = loc.getBlockZ();

            if (yaw >= 315 || yaw < 45) {
                direction = "north";
                bx = px - 4; by = py + 6; bz = pz + 4;
            } else if (yaw >= 45 && yaw < 135) {
                direction = "east";
                bx = px - 4; by = py + 6; bz = pz - 4;
            } else if (yaw >= 135 && yaw < 225) {
                direction = "south";
                bx = px - 4; by = py + 6; bz = pz - 4;
            } else {
                direction = "west";
                bx = px + 4; by = py + 6; bz = pz - 4;
            }
        }

        double topLeftX, topLeftY, topLeftZ;
        topLeftY = by + 1.0;

        switch (direction) {
            case "south" -> {
                topLeftX = bx;
                topLeftZ = bz + 1.0;
            }
            case "north" -> {
                topLeftX = bx + 1.0;
                topLeftZ = bz;
            }
            case "east" -> {
                topLeftX = bx + 1.0;
                topLeftZ = bz + 1.0;
            }
            case "west" -> {
                topLeftX = bx;
                topLeftZ = bz;
            }
            default -> {
                topLeftX = bx;
                topLeftZ = bz;
            }
        }

        return new BoardSelectionManager.SelectionGeometry(
                world,
                topLeftX, topLeftY, topLeftZ,
                8, 8,
                direction
        );
    }

    private void writeShowcaseTemplate(File file, String boardName, BoardSelectionManager.SelectionGeometry geo) throws IOException {
        Vector3d tl = geo.topLeft();
        String template = null;
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("boards/showcase.yml")) {
            if (in != null) {
                template = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
        } catch (Throwable ignored) {
        }

        String displaysSection = String.format(Locale.ROOT,
                "displays:\n" +
                "  main:\n" +
                "    world: \"%s\"\n" +
                "    width: %d\n" +
                "    height: %d\n" +
                "    top-left:\n" +
                "      x: %.1f\n" +
                "      y: %.1f\n" +
                "      z: %.1f\n" +
                "    direction: \"%s\"\n" +
                "    distance: 32.0\n" +
                "    interaction-distance: 16.0\n" +
                "    shape: \"rounded\"\n" +
                "    corner-radius: 32\n" +
                "    hover-glow: true\n" +
                "    glow-color: \"aqua\"\n" +
                "    glow-mode: \"border\"\n",
                geo.world(), geo.widthBlocks(), geo.heightBlocks(),
                tl.x(), tl.y(), tl.z(),
                geo.directionLabel()
        );

        if (template != null && !template.isBlank()) {
            int dispIdx = template.indexOf("displays:");
            int scenesIdx = template.indexOf("scenes:");
            if (dispIdx != -1 && scenesIdx != -1) {
                String replaced = template.substring(0, dispIdx) + displaysSection + "\n" + template.substring(scenesIdx);
                Files.writeString(file.toPath(), replaced, StandardCharsets.UTF_8);
                return;
            }
        }

        try (PrintWriter pw = new PrintWriter(new FileWriter(file))) {
            pw.println("# GoosBoards - 8x8 Interactive Showcase");
            pw.println("# Auto-generated by /gb showcase");
            pw.println();
            pw.println("settings:");
            pw.println("  dithering: true");
            pw.println("  reset-on-radius-exit: true");
            pw.println();
            pw.print(displaysSection);
            pw.println();
            pw.println("scenes:");
            pw.println("  default:");
            pw.println("    background:");
            pw.println("      type: background");
            pw.println("      position: \"0 0\"");
            pw.println("      size: \"100% 100%\"");
            pw.println("      color: \"#0f172a\"");
            pw.println("      corner-radius: 8");
            pw.println("      outline-color: \"#3b82f6\"");
            pw.println("      outline-width: 2");
            pw.println();
            pw.println("    header-title:");
            pw.println("      type: text");
            pw.println("      position: \"32 32\"");
            pw.println("      size: \"960 48\"");
            pw.println("      alignment: left");
            pw.println("      text: \"<gradient:#38BDF8:#818CF8><b>GOOSBOARDS 8X8 SHOWCASE</b></gradient>\"");
            pw.println("      font: \"SansSerif\"");
            pw.println("      font-size: 34");
            pw.println("      outline-color: \"#000000\"");
            pw.println("      outline-stroke: 1.5");
        }
    }
}
