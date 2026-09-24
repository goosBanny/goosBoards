package me.goosbanny.goosboards.command;

import me.goosbanny.goosboards.core.logging.MessageService;

import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.plugin.Plugin;

import java.util.Map;
import java.util.UUID;

/**
 * Listens for right-click-block events to drive the interactive two-corner board selection process.
 * Sends Action Bar feedback at each stage to guide the player.
 */
public class BoardSelectionListener implements Listener {

    private final BoardSelectionManager selectionManager;
    private final MessageService messageService;

    /** The command label shown in action bar hints (e.g., "gb"). */
    private final String commandLabel;

    private boolean registered = false;

    public BoardSelectionListener(
            BoardSelectionManager selectionManager,
            MessageService messageService,
            String commandLabel
    ) {
        this.selectionManager = selectionManager;
        this.messageService = messageService;
        this.commandLabel = commandLabel != null ? commandLabel : "gb";
    }

    public synchronized void register(Plugin plugin) {
        if (!registered && plugin != null) {
            plugin.getServer().getPluginManager().registerEvents(this, plugin);
            registered = true;
        }
    }

    public synchronized void unregister() {
        if (registered) {
            HandlerList.unregisterAll(this);
            registered = false;
        }
    }

    public synchronized boolean isRegistered() {
        return registered;
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = false)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event == null) return;
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        BoardSelectionManager.SelectionSession session = selectionManager.getSession(uuid);
        if (session == null) return;

        // Cancel the interact event so no normal right-click behavior occurs during selection
        event.setCancelled(true);

        Block block = event.getClickedBlock();
        BlockFace face = event.getBlockFace();
        if (block == null || face == null) return;

        String world = block.getWorld().getName();
        int bx = block.getX();
        int by = block.getY();
        int bz = block.getZ();

        BoardSelectionManager.CornerClick click = new BoardSelectionManager.CornerClick(world, bx, by, bz, face);

        if (session.getStage() == BoardSelectionManager.Stage.WAITING_FOR_CORNER_1) {
            selectionManager.recordCorner1(uuid, click);

            messageService.sendActionBar(player, "selection-corner1-actionbar", Map.of(
                    "x", String.valueOf(bx),
                    "y", String.valueOf(by),
                    "z", String.valueOf(bz),
                    "label", commandLabel
            ));

        } else if (session.getStage() == BoardSelectionManager.Stage.WAITING_FOR_CORNER_2) {
            // Validate same world
            BoardSelectionManager.CornerClick c1 = session.getCorner1();
            if (!world.equals(c1.world())) {
                messageService.send(player, "selection-world-mismatch");
                return;
            }

            selectionManager.recordCorner2(uuid, click);
            BoardSelectionManager.SelectionGeometry geo = selectionManager.computeGeometry(c1, click);

            if (geo != null) {
                messageService.sendActionBar(player, "selection-corner2-actionbar", Map.of(
                        "width", String.valueOf(geo.widthBlocks()),
                        "height", String.valueOf(geo.heightBlocks()),
                        "dir", geo.directionLabel(),
                        "label", commandLabel
                ));
            }
        }
    }
}
