package me.goosbanny.goosboards.render.palette;

import java.util.Locale;

public final class ColorUtils {
    private ColorUtils() {}

    /**
     * Parses a color string into a 32-bit ARGB integer.
     * Supports space-separated RGBA ("40 120 220 255"), hex ("#FF5500", "#AAFF5500"), and standard color names.
     */
    public static int parseColor(String str, int defaultColor) {
        if (str == null || str.isBlank()) {
            return defaultColor;
        }
        str = str.trim();

        // 1. Space-separated RGBA: "R G B [A]"
        if (str.contains(" ")) {
            String[] parts = str.split("\\s+");
            if (parts.length >= 3) {
                try {
                    int r = Math.clamp(Integer.parseInt(parts[0]), 0, 255);
                    int g = Math.clamp(Integer.parseInt(parts[1]), 0, 255);
                    int b = Math.clamp(Integer.parseInt(parts[2]), 0, 255);
                    int a = parts.length >= 4 ? Math.clamp(Integer.parseInt(parts[3]), 0, 255) : 255;
                    return (a << 24) | (r << 16) | (g << 8) | b;
                } catch (NumberFormatException ignored) {}
            }
        }

        // 2. Hex format: #RRGGBB or #AARRGGBB
        if (str.startsWith("#")) {
            String hex = str.substring(1);
            try {
                if (hex.length() == 6) {
                    int rgb = Integer.parseInt(hex, 16);
                    return 0xFF000000 | rgb;
                } else if (hex.length() == 8) {
                    return (int) Long.parseLong(hex, 16);
                }
            } catch (NumberFormatException ignored) {}
        }

        // 3. Named colors
        return switch (str.toLowerCase(Locale.ROOT)) {
            case "white" -> 0xFFFFFFFF;
            case "black" -> 0xFF000000;
            case "transparent", "none" -> 0x00000000;
            case "red" -> 0xFFFF5555;
            case "dark_red" -> 0xFFAA0000;
            case "green" -> 0xFF55FF55;
            case "dark_green" -> 0xFF00AA00;
            case "blue" -> 0xFF5555FF;
            case "dark_blue" -> 0xFF0000AA;
            case "yellow" -> 0xFFFFFF55;
            case "gold", "orange" -> 0xFFFFAA00;
            case "aqua", "cyan" -> 0xFF55FFFF;
            case "dark_aqua" -> 0xFF00AAAA;
            case "light_purple", "pink", "magenta" -> 0xFFFF55FF;
            case "dark_purple", "purple" -> 0xFFAA00AA;
            case "gray", "grey" -> 0xFFAAAAAA;
            case "dark_gray", "dark_grey" -> 0xFF555555;
            case "light_gray", "light_grey" -> 0xFFCCCCCC;
            default -> defaultColor;
        };
    }
}
