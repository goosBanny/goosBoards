package me.goosbanny.goosboards.display;

import me.goosbanny.goosboards.raycast.Vector3d;
import org.bukkit.block.BlockFace;

import java.util.UUID;

public record DisplayPlane(
    UUID id,
    String worldName,
    Vector3d topLeft,        // top-left corner in world space
    double widthBlocks,
    double heightBlocks,
    Vector3d rightUnit,      // normalized right vector (Û)
    Vector3d downUnit,       // normalized down vector (V̂)
    double activationRadius, // broad-phase: player within this → enqueue for raycast
    double interactionRadius,// ray valid up to this distance
    Vector3d normal,         // precomputed unit normal pointing OUT toward the viewer
    Vector3d center,         // precomputed center in world space
    double boundingRadius,   // precomputed half-diagonal bounding sphere radius
    int itemFrameOrientation,// precomputed Minecraft item frame facing data index
    boolean glow,
    boolean hoverGlow,
    String glowColor,
    String hoverGlowColor,
    String glowMode,
    String shape,
    int cornerRadius
) {
    public boolean isCircular() {
        return "circle".equalsIgnoreCase(shape);
    }

    public boolean isRounded() {
        return "rounded".equalsIgnoreCase(shape) || "rounded_rectangle".equalsIgnoreCase(shape);
    }

    public DisplayPlane(
        UUID id,
        String worldName,
        Vector3d topLeft,
        double widthBlocks,
        double heightBlocks,
        Vector3d rightUnit,
        Vector3d downUnit,
        double activationRadius,
        double interactionRadius,
        boolean glow,
        boolean hoverGlow,
        String glowColor,
        String hoverGlowColor,
        String glowMode,
        String shape,
        int cornerRadius
    ) {
        this(
            id,
            worldName,
            topLeft,
            widthBlocks,
            heightBlocks,
            rightUnit,
            downUnit,
            activationRadius,
            interactionRadius,
            computeNormal(rightUnit, downUnit),
            computeCenter(topLeft, rightUnit, downUnit, widthBlocks, heightBlocks),
            computeBoundingRadius(rightUnit, downUnit, widthBlocks, heightBlocks),
            computeItemFrameOrientation(computeNormal(rightUnit, downUnit)),
            glow,
            hoverGlow,
            glowColor != null ? glowColor : "aqua",
            hoverGlowColor != null ? hoverGlowColor : "white",
            glowMode != null ? glowMode : "border",
            shape != null ? shape : "rectangle",
            cornerRadius
        );
    }

    public DisplayPlane(
        UUID id,
        String worldName,
        Vector3d topLeft,
        double widthBlocks,
        double heightBlocks,
        Vector3d rightUnit,
        Vector3d downUnit,
        double activationRadius,
        double interactionRadius,
        boolean glow,
        boolean hoverGlow,
        String glowColor,
        String glowMode,
        String shape,
        int cornerRadius
    ) {
        this(
            id,
            worldName,
            topLeft,
            widthBlocks,
            heightBlocks,
            rightUnit,
            downUnit,
            activationRadius,
            interactionRadius,
            glow,
            hoverGlow,
            glowColor,
            "white",
            glowMode,
            shape,
            cornerRadius
        );
    }

    public DisplayPlane(
        UUID id,
        String worldName,
        Vector3d topLeft,
        double widthBlocks,
        double heightBlocks,
        Vector3d rightUnit,
        Vector3d downUnit,
        double activationRadius,
        double interactionRadius,
        boolean glow,
        boolean hoverGlow,
        String glowColor,
        String glowMode
    ) {
        this(
            id,
            worldName,
            topLeft,
            widthBlocks,
            heightBlocks,
            rightUnit,
            downUnit,
            activationRadius,
            interactionRadius,
            glow,
            hoverGlow,
            glowColor,
            glowMode,
            "rectangle",
            0
        );
    }

    public DisplayPlane(
        UUID id,
        String worldName,
        Vector3d topLeft,
        double widthBlocks,
        double heightBlocks,
        Vector3d rightUnit,
        Vector3d downUnit,
        double activationRadius,
        double interactionRadius
    ) {
        this(
            id,
            worldName,
            topLeft,
            widthBlocks,
            heightBlocks,
            rightUnit,
            downUnit,
            activationRadius,
            interactionRadius,
            false,
            false,
            "aqua",
            "border"
        );
    }

    public DisplayPlane(UUID id, String worldName, Vector3d topLeft, int widthBlocks, int heightBlocks) {
        this(id, worldName, topLeft, widthBlocks, heightBlocks, new Vector3d(1, 0, 0), new Vector3d(0, -1, 0), 32.0, 16.0);
    }

    public int getWidthTiles() {
        return (int) Math.round(widthBlocks);
    }

    public int getHeightTiles() {
        return (int) Math.round(heightBlocks);
    }

    public static final double INVISIBLE_FRAME_OFFSET = 1.0 / 128.0; // 0.0078125 blocks
    public static final double VISIBLE_FRAME_OFFSET = 9.0 / 128.0;   // 0.0703125 blocks

    public static Vector3d deriveRight(BlockFace face, Vector3d down) {
        if (face == null) return new Vector3d(1, 0, 0);
        return switch (face) {
            case UP -> {
                // Floor: down × (0, 1, 0)
                if (down == null) yield new Vector3d(1, 0, 0);
                Vector3d r = down.cross(new Vector3d(0, 1, 0));
                yield r.length() > 1e-6 ? r.multiply(1.0 / r.length()) : new Vector3d(1, 0, 0);
            }
            case DOWN -> {
                // Ceiling: (0, -1, 0) × down
                if (down == null) yield new Vector3d(1, 0, 0);
                Vector3d r = new Vector3d(0, -1, 0).cross(down);
                yield r.length() > 1e-6 ? r.multiply(1.0 / r.length()) : new Vector3d(1, 0, 0);
            }
            case NORTH -> new Vector3d(-1, 0, 0);
            case SOUTH -> new Vector3d(1, 0, 0);
            case EAST  -> new Vector3d(0, 0, -1);
            case WEST  -> new Vector3d(0, 0, 1);
            default    -> new Vector3d(1, 0, 0);
        };
    }

    public static int getFrameRotation(BlockFace face, Vector3d down) {
        if (face == null || down == null) return 0;
        if (face == BlockFace.UP) {
            // Floor: SOUTH -> 0, WEST -> 1, NORTH -> 2, EAST -> 3
            if (down.z() > 0.5) return 0;  // SOUTH
            if (down.x() < -0.5) return 1; // WEST
            if (down.z() < -0.5) return 2; // NORTH
            if (down.x() > 0.5) return 3;  // EAST
            return 0;
        } else if (face == BlockFace.DOWN) {
            // Ceiling: SOUTH -> 2, WEST -> 1, NORTH -> 0, EAST -> 3
            if (down.z() > 0.5) return 2;  // SOUTH
            if (down.x() < -0.5) return 1; // WEST
            if (down.z() < -0.5) return 0; // NORTH
            if (down.x() > 0.5) return 3;  // EAST
            return 0;
        }
        return 0;
    }

    private static Vector3d computeNormal(Vector3d right, Vector3d down) {
        if (right == null || down == null) {
            return new Vector3d(0, 0, 1);
        }
        // down.cross(right) produces the outward normal facing the viewer across all faces
        Vector3d cross = down.cross(right);
        double len = cross.length();
        return len > 1e-12 ? cross.multiply(1.0 / len) : new Vector3d(0, 0, 1);
    }

    private static Vector3d computeCenter(Vector3d topLeft, Vector3d right, Vector3d down, double width, double height) {
        if (topLeft == null || right == null || down == null) {
            return topLeft != null ? topLeft : new Vector3d(0, 0, 0);
        }
        return topLeft.add(right.multiply(width * 0.5)).add(down.multiply(height * 0.5));
    }

    private static double computeBoundingRadius(Vector3d right, Vector3d down, double width, double height) {
        if (right == null || down == null) {
            return 0.5 * Math.sqrt(width * width + height * height);
        }
        Vector3d diagonal = right.multiply(width).add(down.multiply(height));
        return diagonal.length() * 0.5;
    }

    private static int computeItemFrameOrientation(Vector3d normal) {
        if (normal == null) {
            return 3; // default South
        }
        if (normal.y() < -0.5) return 0; // Down
        if (normal.y() > 0.5)  return 1; // Up
        if (normal.z() < -0.5) return 2; // North
        if (normal.z() > 0.5)  return 3; // South
        if (normal.x() < -0.5) return 4; // West
        if (normal.x() > 0.5)  return 5; // East
        return 3; // fallback South
    }
}