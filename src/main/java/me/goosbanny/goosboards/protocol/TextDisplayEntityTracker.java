package me.goosbanny.goosboards.protocol;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.wrapper.PacketWrapper;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerDestroyEntities;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnEntity;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Tracks packet-level virtual TextDisplay entities used for transparent UI overlays.
 */
public class TextDisplayEntityTracker {

    private final QuarantinedIdAllocator allocator;
    private final Consumer<PacketWrapper<?>> packetSender;
    private final Map<UUID, Map<UUID, List<Integer>>> activeDisplays = new ConcurrentHashMap<>();

    public TextDisplayEntityTracker(QuarantinedIdAllocator allocator) {
        this(allocator, null);
    }

    public TextDisplayEntityTracker(QuarantinedIdAllocator allocator, Consumer<PacketWrapper<?>> packetSender) {
        this.allocator = allocator;
        this.packetSender = packetSender;
    }

    /**
     * Spawns a virtual text display entity at the specified world coordinate.
     *
     * @param viewer    the viewing player
     * @param displayId the logical display plane or component ID
     * @param position  world coordinate
     * @return the allocated virtual entity ID
     */
    public int spawnTextDisplay(Player viewer, UUID displayId, Vector3d position) {
        int entityId = allocator.allocate();
        activeDisplays.computeIfAbsent(viewer.getUniqueId(), k -> new ConcurrentHashMap<>())
                .computeIfAbsent(displayId, k -> Collections.synchronizedList(new ArrayList<>()))
                .add(entityId);

        WrapperPlayServerSpawnEntity spawnPacket = new WrapperPlayServerSpawnEntity(
                entityId,
                Optional.of(UUID.randomUUID()),
                EntityTypes.TEXT_DISPLAY,
                position,
                0.0f,
                0.0f,
                0.0f,
                0,
                Optional.empty()
        );

        sendPacket(viewer, spawnPacket);
        return entityId;
    }

    public void despawnTextDisplays(Player viewer, UUID displayId) {
        Map<UUID, List<Integer>> playerDisplays = activeDisplays.get(viewer.getUniqueId());
        if (playerDisplays != null) {
            List<Integer> ids = playerDisplays.remove(displayId);
            if (ids != null && !ids.isEmpty()) {
                int[] idArray = ids.stream().mapToInt(Integer::intValue).toArray();
                sendPacket(viewer, new WrapperPlayServerDestroyEntities(idArray));
                for (int id : idArray) {
                    allocator.free(id);
                }
            }
            if (playerDisplays.isEmpty()) {
                activeDisplays.remove(viewer.getUniqueId());
            }
        }
    }

    public void despawnAll(Player viewer) {
        Map<UUID, List<Integer>> playerDisplays = activeDisplays.remove(viewer.getUniqueId());
        if (playerDisplays != null) {
            List<Integer> allIds = new ArrayList<>();
            for (List<Integer> ids : playerDisplays.values()) {
                allIds.addAll(ids);
            }
            if (!allIds.isEmpty()) {
                int[] idArray = allIds.stream().mapToInt(Integer::intValue).toArray();
                sendPacket(viewer, new WrapperPlayServerDestroyEntities(idArray));
                for (int id : idArray) {
                    allocator.free(id);
                }
            }
        }
    }

    public void despawnAll() {
        List<UUID> players = new ArrayList<>(activeDisplays.keySet());
        for (UUID playerId : players) {
            Player p = Bukkit.getPlayer(playerId);
            if (p != null && p.isOnline()) {
                despawnAll(p);
            } else {
                Map<UUID, List<Integer>> playerDisplays = activeDisplays.remove(playerId);
                if (playerDisplays != null) {
                    for (List<Integer> ids : playerDisplays.values()) {
                        for (int id : ids) {
                            allocator.free(id);
                        }
                    }
                }
            }
        }
        activeDisplays.clear();
    }

    public boolean hasActiveDisplays(UUID playerId) {
        Map<UUID, List<Integer>> m = activeDisplays.get(playerId);
        return m != null && !m.isEmpty();
    }

    private void sendPacket(Player player, PacketWrapper<?> packet) {
        if (packetSender != null) {
            packetSender.accept(packet);
        } else if (player != null && player.isOnline()) {
            PacketEvents.getAPI().getPlayerManager().sendPacket(player, packet);
        }
    }
}
