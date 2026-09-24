package me.goosbanny.goosboards.command;

import me.goosbanny.goosboards.raycast.Vector3d;
import org.bukkit.block.BlockFace;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Manages interactive two-corner board creation sessions.
 * Sessions expire after {@link #SESSION_TIMEOUT_MS} if the player does not complete selection.
 */
public class BoardSelectionManager {

    public static final long SESSION_TIMEOUT_MS = TimeUnit.MINUTES.toMillis(5);

    public enum Stage {
        WAITING_FOR_CORNER_1,
        WAITING_FOR_CORNER_2
    }

    /**
     * An immutable snapshot of a corner click: world name, block integer coordinates, and block face.
     */
    public record CornerClick(String world, int bx, int by, int bz, BlockFace face) {}

    /**
     * Computed display geometry after both corners are selected.
     */
    public record SelectionGeometry(
            String world,
            double topLeftX, double topLeftY, double topLeftZ,
            int widthBlocks, int heightBlocks,
            String direction
    ) {
        public String directionLabel() {
            return direction;
        }

        public Vector3d topLeft() {
            return new Vector3d(topLeftX, topLeftY, topLeftZ);
        }
    }

    public static class SelectionSession {
        private final UUID playerUuid;
        private final long startedAt;

        private Stage stage = Stage.WAITING_FOR_CORNER_1;
        private CornerClick corner1 = null;
        private CornerClick corner2 = null;

        public SelectionSession(UUID playerUuid) {
            this.playerUuid = playerUuid;
            this.startedAt = System.currentTimeMillis();
        }

        public boolean isExpired() {
            return System.currentTimeMillis() - startedAt > SESSION_TIMEOUT_MS;
        }

        public Stage getStage() {
            return stage;
        }

        public CornerClick getCorner1() {
            return corner1;
        }

        public CornerClick getCorner2() {
            return corner2;
        }

        public UUID getPlayerUuid() {
            return playerUuid;
        }

        void setCorner1(CornerClick c) {
            this.corner1 = c;
            this.stage = Stage.WAITING_FOR_CORNER_2;
        }

        void setCorner2(CornerClick c) {
            this.corner2 = c;
        }
    }

    private final ConcurrentHashMap<UUID, SelectionSession> sessions = new ConcurrentHashMap<>();
    private Consumer<Boolean> activeStateListener;

    public void setActiveStateListener(Consumer<Boolean> activeStateListener) {
        this.activeStateListener = activeStateListener;
    }

    public boolean hasActiveSessions() {
        return !sessions.isEmpty();
    }

    public int getActiveSessionCount() {
        return sessions.size();
    }

    public SelectionSession startSession(UUID playerUuid) {
        SelectionSession session = new SelectionSession(playerUuid);
        boolean becameActive = false;
        synchronized (sessions) {
            boolean wasEmpty = sessions.isEmpty();
            sessions.put(playerUuid, session);
            if (wasEmpty) {
                becameActive = true;
            }
        }
        if (becameActive && activeStateListener != null) {
            activeStateListener.accept(true);
        }
        return session;
    }

    public SelectionSession getSession(UUID playerUuid) {
        SelectionSession s = sessions.get(playerUuid);
        if (s == null) return null;
        if (s.isExpired()) {
            cancelSession(playerUuid);
            return null;
        }
        return s;
    }

    public boolean cancelSession(UUID playerUuid) {
        boolean removed;
        boolean becameEmpty = false;
        synchronized (sessions) {
            removed = sessions.remove(playerUuid) != null;
            if (removed && sessions.isEmpty()) {
                becameEmpty = true;
            }
        }
        if (becameEmpty && activeStateListener != null) {
            activeStateListener.accept(false);
        }
        return removed;
    }

    /**
     * Sweeps and evicts all expired selection sessions to prevent memory leaks.
     */
    public void sweepExpiredSessions() {
        boolean becameEmpty = false;
        synchronized (sessions) {
            boolean wasNotEmpty = !sessions.isEmpty();
            sessions.entrySet().removeIf(e -> e.getValue().isExpired());
            if (wasNotEmpty && sessions.isEmpty()) {
                becameEmpty = true;
            }
        }
        if (becameEmpty && activeStateListener != null) {
            activeStateListener.accept(false);
        }
    }

    public void recordCorner1(UUID playerUuid, CornerClick click) {
        SelectionSession s = getSession(playerUuid);
        if (s != null) {
            s.setCorner1(click);
        }
    }

    public void recordCorner2(UUID playerUuid, CornerClick click) {
        SelectionSession s = getSession(playerUuid);
        if (s != null) {
            s.setCorner2(click);
        }
    }

    public void finalizeSession(UUID playerUuid) {
        cancelSession(playerUuid);
    }

    /**
     * Computes the display geometry from two corner clicks.
     */
    public SelectionGeometry computeGeometry(CornerClick c1, CornerClick c2) {
        if (c1 == null || c2 == null) return null;
        if (!c1.world().equals(c2.world())) return null;

        String direction = faceToDirection(c1.face());

        int minX = Math.min(c1.bx(), c2.bx());
        int maxX = Math.max(c1.bx(), c2.bx());
        int minY = Math.min(c1.by(), c2.by());
        int maxY = Math.max(c1.by(), c2.by());
        int minZ = Math.min(c1.bz(), c2.bz());
        int maxZ = Math.max(c1.bz(), c2.bz());

        int heightBlocks = maxY - minY + 1;
        int widthBlocks;
        double topLeftX, topLeftY, topLeftZ;

        topLeftY = maxY + 1.0; // Top of the top-most block row

        switch (direction) {
            case "south" -> {
                widthBlocks = maxX - minX + 1;
                topLeftX = minX;             // Step +X to the right
                topLeftZ = maxZ + 1.0;        // Front surface of south face
            }
            case "north" -> {
                widthBlocks = maxX - minX + 1;
                topLeftX = maxX + 1.0;        // Step -X to the right
                topLeftZ = minZ;              // Front surface of north face
            }
            case "east" -> {
                widthBlocks = maxZ - minZ + 1;
                topLeftX = maxX + 1.0;        // Front surface of east face
                topLeftZ = maxZ + 1.0;        // Step -Z to the right
            }
            case "west" -> {
                widthBlocks = maxZ - minZ + 1;
                topLeftX = minX;              // Front surface of west face
                topLeftZ = minZ;              // Step +Z to the right
            }
            case "up" -> {
                widthBlocks = maxX - minX + 1;
                heightBlocks = maxZ - minZ + 1;
                topLeftX = minX;
                topLeftY = maxY + 1.0;        // Top surface of floor
                topLeftZ = minZ;
            }
            case "down" -> {
                widthBlocks = maxX - minX + 1;
                heightBlocks = maxZ - minZ + 1;
                topLeftX = minX;
                topLeftY = minY;              // Bottom surface of ceiling
                topLeftZ = maxZ + 1.0;
            }
            default -> {
                widthBlocks = maxX - minX + 1;
                topLeftX = minX;
                topLeftZ = minZ;
            }
        }

        return new SelectionGeometry(
                c1.world(),
                topLeftX, topLeftY, topLeftZ,
                Math.max(1, widthBlocks),
                Math.max(1, heightBlocks),
                direction
        );
    }

    public static String faceToDirection(BlockFace face) {
        if (face == null) return "south";
        return switch (face) {
            case NORTH -> "north";
            case SOUTH -> "south";
            case EAST  -> "east";
            case WEST  -> "west";
            case UP    -> "up";
            case DOWN  -> "down";
            default    -> "south";
        };
    }
}