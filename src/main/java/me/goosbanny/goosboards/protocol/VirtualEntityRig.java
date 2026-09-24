package me.goosbanny.goosboards.protocol;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.protocol.component.ComponentTypes;
import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes;
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import com.github.retrooper.packetevents.protocol.item.ItemStack;
import com.github.retrooper.packetevents.protocol.item.type.ItemTypes;
import com.github.retrooper.packetevents.protocol.nbt.NBTCompound;
import com.github.retrooper.packetevents.protocol.nbt.NBTInt;
import com.github.retrooper.packetevents.protocol.player.ClientVersion;
import com.github.retrooper.packetevents.wrapper.PacketWrapper;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerDestroyEntities;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerMapData;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnEntity;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerTeams;
import com.github.retrooper.packetevents.manager.server.ServerVersion;
import com.github.retrooper.packetevents.util.Vector3d;
import me.goosbanny.goosboards.core.logging.DebugLogger;
import me.goosbanny.goosboards.display.DisplayPlane;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

public class VirtualEntityRig {
    private final Player viewer;
    private final DisplayPlane plane;
    private final QuarantinedIdAllocator allocator;
    private final Consumer<PacketWrapper<?>> packetSender;
    private final int[] customMapIds;
    private int[][] entityIds;
    private int[][] mapIds;
    private UUID[][] entityUuids;
    private boolean spawned;
    private boolean currentlyGlowing = false;
    private String registeredTeamName = null;

    public VirtualEntityRig(Player viewer, DisplayPlane plane, QuarantinedIdAllocator allocator) {
        this(viewer, plane, allocator, null, null);
    }

    public VirtualEntityRig(Player viewer, DisplayPlane plane, QuarantinedIdAllocator allocator, Consumer<PacketWrapper<?>> packetSender) {
        this(viewer, plane, allocator, packetSender, null);
    }

    public VirtualEntityRig(Player viewer, DisplayPlane plane, QuarantinedIdAllocator allocator, Consumer<PacketWrapper<?>> packetSender, int[] customMapIds) {
        this.viewer = viewer;
        this.plane = plane;
        this.allocator = allocator;
        this.packetSender = packetSender;
        this.customMapIds = customMapIds;
    }

    public synchronized void spawn() {
        if (spawned) {
            return;
        }
        int width = plane.getWidthTiles();
        int height = plane.getHeightTiles();
        entityIds = new int[width][height];
        mapIds = new int[width][height];
        entityUuids = new UUID[width][height];

        int clientProtocol = resolveClientProtocol();
        int itemMetadataIndex = (clientProtocol >= 768) ? 9 : 8;
        int rotationMetadataIndex = (clientProtocol >= 768) ? 10 : 9;

        // Calculate pitch and yaw based on item frame orientation so it renders flush
        float pitch = 0.0f;
        float yaw = 0.0f;
        switch (plane.itemFrameOrientation()) {
            case 0 -> pitch = 90.0f;  // Down
            case 1 -> pitch = -90.0f; // Up
            case 2 -> yaw = 180.0f;   // North (-Z)
            case 3 -> yaw = 0.0f;     // South (+Z)
            case 4 -> yaw = 90.0f;    // West (-X)
            case 5 -> yaw = 270.0f;   // East (+X)
        }

        // Horizontal rotation for floor/ceiling frames
        int frameRotation = 0;
        if (plane.itemFrameOrientation() == 1) { // UP (floor)
            frameRotation = DisplayPlane.getFrameRotation(BlockFace.UP, plane.downUnit());
        } else if (plane.itemFrameOrientation() == 0) { // DOWN (ceiling)
            frameRotation = DisplayPlane.getFrameRotation(BlockFace.DOWN, plane.downUnit());
        }

        double surfaceOffset = DisplayPlane.INVISIBLE_FRAME_OFFSET;
        var normal = plane.normal();
        double normOffsetX = normal != null ? normal.x() * surfaceOffset : 0.0;
        double normOffsetY = normal != null ? normal.y() * surfaceOffset : 0.0;
        double normOffsetZ = normal != null ? normal.z() * surfaceOffset : 0.0;

        for (int tx = 0; tx < width; tx++) {
            for (int ty = 0; ty < height; ty++) {
                int entityId = allocator.allocate();
                entityIds[tx][ty] = entityId;

                int tileIdx = ty * width + tx;
                int mapId = (customMapIds != null && tileIdx < customMapIds.length)
                        ? customMapIds[tileIdx]
                        : (10000 + (entityId & 0xFFFF));
                mapIds[tx][ty] = mapId;

                double px = plane.topLeft().x() + (tx + 0.5) * plane.rightUnit().x() + (ty + 0.5) * plane.downUnit().x() + normOffsetX;
                double py = plane.topLeft().y() + (tx + 0.5) * plane.rightUnit().y() + (ty + 0.5) * plane.downUnit().y() + normOffsetY;
                double pz = plane.topLeft().z() + (tx + 0.5) * plane.rightUnit().z() + (ty + 0.5) * plane.downUnit().z() + normOffsetZ;

                UUID frameUuid = UUID.randomUUID();
                entityUuids[tx][ty] = frameUuid;

                WrapperPlayServerSpawnEntity spawnPacket = new WrapperPlayServerSpawnEntity(
                        entityId,
                        Optional.of(frameUuid),
                        EntityTypes.ITEM_FRAME,
                        new Vector3d(px, py, pz),
                        pitch,
                        yaw,
                        yaw,
                        plane.itemFrameOrientation(),
                        Optional.empty()
                );
                sendPacket(spawnPacket);

                ItemStack mapItem = null;
                try {
                    mapItem = ItemStack.builder()
                            .type(ItemTypes.FILLED_MAP)
                            .amount(1)
                            .build();

                    if (clientProtocol >= 766) {
                        // Protocol >= 766 (1.20.5+): Data Component minecraft:map_id
                        try {
                            mapItem.setComponent(ComponentTypes.MAP_ID, mapId);
                        } catch (Throwable ignored) {
                        }
                    } else {
                        // Protocol 762 - 765 (1.19.4 - 1.20.4): NBT compound {map: (int) id}
                        NBTCompound tag = new NBTCompound();
                        tag.setTag("map", new NBTInt(mapId));
                        mapItem.setNBT(tag);
                    }
                } catch (Throwable ignored) {
                }

                List<EntityData<?>> metadata = new ArrayList<>();
                // Index 0: Bitmask (0x20 = 32 = Invisible frame; 0x40 = 64 = Glowing)
                boolean initialGlow = plane.glow() && isNativeEntityGlowEnabled();
                byte initialByte = (byte) (initialGlow ? 0x60 : 0x20);
                metadata.add(new EntityData<>(0, EntityDataTypes.BYTE, initialByte));
                // Version-aware index: 8 on <= 1.21.1 (Protocol <= 767), 9 on >= 1.21.2 (Protocol >= 768)
                metadata.add(new EntityData<>(itemMetadataIndex, EntityDataTypes.ITEMSTACK, mapItem));
                if (frameRotation != 0) {
                    metadata.add(new EntityData<>(rotationMetadataIndex, EntityDataTypes.INT, frameRotation));
                }

                WrapperPlayServerEntityMetadata metadataPacket = new WrapperPlayServerEntityMetadata(entityId, metadata);
                sendPacket(metadataPacket);
            }
        }
        spawned = true;
        if (plane.glow() && isNativeEntityGlowEnabled()) {
            this.currentlyGlowing = true;
            applyScoreboardTeam(plane.glowColor());
        }
        DebugLogger.log("Rig", "Spawned %dx%d virtual item frames for %s on display %s (orientation=%d, metadataIndex=%d, glow=%s)",
                width, height, (viewer != null ? viewer.getName() : "null"), plane.id(), plane.itemFrameOrientation(), itemMetadataIndex, plane.glow());
    }

    public boolean isNativeEntityGlowEnabled() {
        return !plane.isCircular() && !plane.isRounded();
    }

    private int resolveClientProtocol() {
        try {
            if (PacketEvents.getAPI() != null && PacketEvents.getAPI().getPlayerManager() != null && viewer != null) {
                ClientVersion cv =
                        PacketEvents.getAPI().getPlayerManager().getClientVersion(viewer);
                if (cv != null && cv != ClientVersion.UNKNOWN) {
                    return cv.getProtocolVersion();
                }
            }
        } catch (Throwable ignored) {
        }
        try {
            if (PacketEvents.getAPI() != null && PacketEvents.getAPI().getServerManager() != null) {
                ServerVersion sv =
                        PacketEvents.getAPI().getServerManager().getVersion();
                if (sv != null) {
                    return sv.toClientVersion().getProtocolVersion();
                }
            }
        } catch (Throwable ignored) {
        }
        try {
            String bukkitVersion = Bukkit.getBukkitVersion();
            if (bukkitVersion != null) {
                if (bukkitVersion.contains("1.19.4") || bukkitVersion.contains("1.20")) {
                    return 763;
                }
                if (bukkitVersion.contains("1.21-") || bukkitVersion.contains("1.21.0") || bukkitVersion.contains("1.21.1")) {
                    return 767;
                }
            }
        } catch (Throwable ignored) {
        }
        return 768; // Default modern 1.21.2+ protocol
    }

    public synchronized void despawn() {
        if (!spawned || entityIds == null) {
            return;
        }

        int width = entityIds.length;
        int height = width > 0 ? entityIds[0].length : 0;
        int count = width * height;
        int[] ids = new int[count];
        int index = 0;

        for (int tx = 0; tx < width; tx++) {
            for (int ty = 0; ty < height; ty++) {
                ids[index++] = entityIds[tx][ty];
            }
        }

        if (ids.length > 0) {
            WrapperPlayServerDestroyEntities destroyPacket = new WrapperPlayServerDestroyEntities(ids);
            sendPacket(destroyPacket);
            for (int id : ids) {
                allocator.free(id);
            }
        }

        removeScoreboardTeam();
        spawned = false;
        entityIds = null;
        mapIds = null;
        entityUuids = null;
        currentlyGlowing = false;
        DebugLogger.log("Rig", "Despawned %d virtual item frames for %s on display %s",
                ids.length, (viewer != null ? viewer.getName() : "null"), plane.id());
    }

    public synchronized void setGlowing(boolean glowing, String colorName) {
        if (!isNativeEntityGlowEnabled()) {
            if (currentlyGlowing) {
                currentlyGlowing = false;
                removeScoreboardTeam();
            }
            return;
        }
        if (!spawned || entityIds == null || this.currentlyGlowing == glowing) {
            return;
        }
        this.currentlyGlowing = glowing;
        int width = entityIds.length;
        int height = width > 0 ? entityIds[0].length : 0;
        boolean borderOnly = !"all".equalsIgnoreCase(plane.glowMode());
        byte metaByte = (byte) (glowing ? 0x60 : 0x20);

        for (int tx = 0; tx < width; tx++) {
            for (int ty = 0; ty < height; ty++) {
                if (borderOnly && !(tx == 0 || tx == width - 1 || ty == 0 || ty == height - 1)) {
                    continue;
                }
                int entityId = entityIds[tx][ty];
                List<EntityData<?>> metadata = new ArrayList<>(1);
                metadata.add(new EntityData<>(0, EntityDataTypes.BYTE, metaByte));
                sendPacket(new WrapperPlayServerEntityMetadata(entityId, metadata));
            }
        }

        if (glowing) {
            applyScoreboardTeam(colorName);
        } else {
            removeScoreboardTeam();
        }
    }

    private void applyScoreboardTeam(String colorName) {
        if (entityUuids == null) return;
        List<String> teamEntries = new ArrayList<>();
        int width = entityUuids.length;
        int height = width > 0 ? entityUuids[0].length : 0;
        boolean borderOnly = !"all".equalsIgnoreCase(plane.glowMode());

        for (int tx = 0; tx < width; tx++) {
            for (int ty = 0; ty < height; ty++) {
                if (borderOnly && !(tx == 0 || tx == width - 1 || ty == 0 || ty == height - 1)) {
                    continue;
                }
                UUID u = entityUuids[tx][ty];
                if (u != null) {
                    teamEntries.add(u.toString());
                }
            }
        }

        if (teamEntries.isEmpty()) return;

        removeScoreboardTeam();

        String tName = "gb_" + plane.id().toString().replace("-", "").substring(0, 10);
        NamedTextColor color = parseNamedTextColor(colorName);

        try {
            WrapperPlayServerTeams.ScoreBoardTeamInfo info = new WrapperPlayServerTeams.ScoreBoardTeamInfo(
                    Component.text(tName),
                    Component.empty(),
                    Component.empty(),
                    WrapperPlayServerTeams.NameTagVisibility.ALWAYS,
                    WrapperPlayServerTeams.CollisionRule.NEVER,
                    color,
                    WrapperPlayServerTeams.OptionData.NONE
            );
            WrapperPlayServerTeams teamPacket = new WrapperPlayServerTeams(
                    tName,
                    WrapperPlayServerTeams.TeamMode.CREATE,
                    info,
                    teamEntries
            );
            sendPacket(teamPacket);
            this.registeredTeamName = tName;
        } catch (Throwable ignored) {
        }
    }

    private void removeScoreboardTeam() {
        if (registeredTeamName != null) {
            try {
                WrapperPlayServerTeams removePacket = new WrapperPlayServerTeams(
                        registeredTeamName,
                        WrapperPlayServerTeams.TeamMode.REMOVE,
                        Optional.empty()
                );
                sendPacket(removePacket);
            } catch (Throwable ignored) {
            }
            this.registeredTeamName = null;
        }
    }

    public static NamedTextColor parseNamedTextColor(String colorStr) {
        if (colorStr == null || colorStr.isBlank()) {
            return NamedTextColor.WHITE;
        }
        String clean = colorStr.trim().toLowerCase(Locale.ROOT).replace("-", "_").replace(" ", "_");
        return switch (clean) {
            case "black" -> NamedTextColor.BLACK;
            case "dark_blue" -> NamedTextColor.DARK_BLUE;
            case "dark_green" -> NamedTextColor.DARK_GREEN;
            case "dark_aqua", "cyan" -> NamedTextColor.DARK_AQUA;
            case "dark_red" -> NamedTextColor.DARK_RED;
            case "dark_purple", "purple" -> NamedTextColor.DARK_PURPLE;
            case "gold", "orange" -> NamedTextColor.GOLD;
            case "gray", "grey" -> NamedTextColor.GRAY;
            case "dark_gray", "dark_grey" -> NamedTextColor.DARK_GRAY;
            case "blue" -> NamedTextColor.BLUE;
            case "green" -> NamedTextColor.GREEN;
            case "aqua", "light_blue" -> NamedTextColor.AQUA;
            case "red" -> NamedTextColor.RED;
            case "light_purple", "pink", "magenta" -> NamedTextColor.LIGHT_PURPLE;
            case "yellow" -> NamedTextColor.YELLOW;
            default -> {
                if (clean.startsWith("#") && clean.length() == 7) {
                    yield matchClosestNamedTextColor(clean);
                }
                yield NamedTextColor.WHITE;
            }
        };
    }

    private static NamedTextColor matchClosestNamedTextColor(String hex) {
        try {
            int rgb = Integer.parseInt(hex.substring(1), 16);
            int r = (rgb >> 16) & 0xFF;
            int g = (rgb >> 8) & 0xFF;
            int b = rgb & 0xFF;

            NamedTextColor best = NamedTextColor.WHITE;
            double bestDist = Double.MAX_VALUE;

            for (NamedTextColor ntc : NamedTextColor.NAMES.values()) {
                int cr = ntc.red();
                int cg = ntc.green();
                int cb = ntc.blue();
                double d = (r - cr) * (r - cr) + (g - cg) * (g - cg) + (b - cb) * (b - cb);
                if (d < bestDist) {
                    bestDist = d;
                    best = ntc;
                }
            }
            return best;
        } catch (Throwable ignored) {
            return NamedTextColor.WHITE;
        }
    }

    public synchronized boolean isGlowing() {
        return currentlyGlowing;
    }

    protected void sendPacket(PacketWrapper<?> packet) {
        if (packetSender != null) {
            packetSender.accept(packet);
            return;
        }
        try {
            if (PacketEvents.getAPI() != null && PacketEvents.getAPI().getPlayerManager() != null) {
                PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, packet);
            }
        } catch (Throwable ignored) {
            // Safe fallback during tests without live PacketEvents runtime
        }
    }

    public Player getViewer() {
        return viewer;
    }

    public DisplayPlane getPlane() {
        return plane;
    }

    public synchronized boolean isSpawned() {
        return spawned;
    }

    public synchronized int[][] getEntityIds() {
        return entityIds;
    }

    public synchronized int[][] getMapIds() {
        return mapIds;
    }
}