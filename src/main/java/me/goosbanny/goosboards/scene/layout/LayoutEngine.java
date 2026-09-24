package me.goosbanny.goosboards.scene.layout;

import me.goosbanny.goosboards.scene.Rect;
import me.goosbanny.goosboards.scene.component.UIComponent;
import me.goosbanny.goosboards.scene.component.container.ScrollPaneComponent;
import me.goosbanny.goosboards.scene.component.interactive.ButtonComponent;
import me.goosbanny.goosboards.scene.component.text.PixelTextComponent;
import me.goosbanny.goosboards.scene.component.text.TextComponent;
import me.goosbanny.goosboards.scene.exception.LayoutDepthException;

import me.goosbanny.goosboards.core.logging.DebugLogger;

import java.util.List;

/**
 * Computes bounding boxes (Rect) for component trees based on declarative position and size specifications.
 */
public final class LayoutEngine {
    public static final int MAX_RECURSION_DEPTH = 16;

    private LayoutEngine() {}

    /**
     * Lays out a single root component and its entire subtree within the given canvas boundaries.
     *
     * @param root         the root component
     * @param canvasWidth  the canvas width in pixels
     * @param canvasHeight the canvas height in pixels
     */
    public static void layout(UIComponent root, int canvasWidth, int canvasHeight) {
        if (root == null) {
            return;
        }
        layoutRecursive(root, 0, 0, canvasWidth, canvasHeight, 1);
    }

    /**
     * Lays out a list of root components within the given canvas boundaries.
     *
     * @param components   the root components
     * @param canvasWidth  the canvas width in pixels
     * @param canvasHeight the canvas height in pixels
     */
    public static void layout(List<UIComponent> components, int canvasWidth, int canvasHeight) {
        if (components == null) {
            return;
        }
        for (UIComponent component : components) {
            layout(component, canvasWidth, canvasHeight);
        }
    }

    private static void layoutRecursive(
            UIComponent component,
            int parentX,
            int parentY,
            int parentWidth,
            int parentHeight,
            int depth
    ) {
        if (depth > MAX_RECURSION_DEPTH) {
            throw new LayoutDepthException("Layout recursion depth exceeded limit of " + MAX_RECURSION_DEPTH);
        }

        // 1. Calculate width and height
        int width = parseDimension(component.getSizeStr(), true, component, parentWidth);
        int height = parseDimension(component.getSizeStr(), false, component, parentHeight);

        // 2. Calculate position
        int[] rawPos = parsePosition(component.getPositionStr(), parentWidth, parentHeight);
        int rawX = rawPos[0];
        int rawY = rawPos[1];

        int x;
        String align = component.getAlignment();
        if ("center".equalsIgnoreCase(align)) {
            x = parentX + (parentWidth - width) / 2;
        } else if ("right".equalsIgnoreCase(align)) {
            x = parentX + parentWidth - width - rawX;
        } else {
            x = parentX + rawX;
        }

        int y;
        String vAlign = component.getVerticalAlignment();
        if ("center".equalsIgnoreCase(vAlign)) {
            y = parentY + (parentHeight - height) / 2;
        } else if ("bottom".equalsIgnoreCase(vAlign)) {
            y = parentY + parentHeight - height - rawY;
        } else {
            y = parentY + rawY;
        }

        if (parentWidth > 0 && x + width > parentX + parentWidth) {
            width = Math.max(1, parentX + parentWidth - x);
        }
        if (parentHeight > 0 && y + height > parentY + parentHeight) {
            height = Math.max(1, parentY + parentHeight - y);
        }

        Rect bounds = new Rect(x, y, width, height);
        component.setBounds(bounds);

        // 3. Layout children relative to this component's bounds (or (0, 0) for scroll pane inner buffer)
        if (component instanceof ScrollPaneComponent spc) {
            int innerW = spc.getScrollInnerWidth() > 0 ? spc.getScrollInnerWidth() : width;
            int innerH = spc.getScrollInnerHeight() > 0 ? spc.getScrollInnerHeight() : height;
            for (UIComponent child : component.getChildren()) {
                layoutRecursive(child, 0, 0, innerW, innerH, depth + 1);
            }
        } else {
            for (UIComponent child : component.getChildren()) {
                layoutRecursive(child, x, y, width, height, depth + 1);
            }
        }
    }

    private static int parseDimension(String sizeStr, boolean isWidth, UIComponent component, int parentDim) {
        if (sizeStr == null || sizeStr.isBlank()) {
            return isWidth ? parentDim : parentDim;
        }
        String[] parts = sizeStr.trim().split("\\s+");
        String token;
        if (isWidth) {
            token = parts[0];
        } else {
            if (parts.length > 1) {
                token = parts[1];
            } else {
                if (component instanceof TextComponent || component instanceof PixelTextComponent) {
                    token = "auto";
                    DebugLogger.log("Layout",
                            "Component '%s' has single-token size '%s'; defaulting height to 'auto'",
                            component.getId(), sizeStr);
                } else {
                    token = parts[0];
                }
            }
        }

        token = token.trim().toLowerCase();

        if ("max".equals(token)) {
            return parentDim;
        }

        if (token.endsWith("%")) {
            try {
                double pct = Double.parseDouble(token.substring(0, token.length() - 1)) / 100.0;
                return (int) Math.round(parentDim * pct);
            } catch (NumberFormatException ignored) {}
        }

        if ("auto".equals(token)) {
            if (component != null && component.getParent() instanceof ButtonComponent) {
                return parentDim;
            }
            if (component instanceof TextComponent tc) {
                int effFontSize = tc.getFontSize() > 0 ? tc.getFontSize() : 16;
                if (isWidth) {
                    int charWidth = Math.max(1, (int) Math.ceil(effFontSize * 0.70));
                    // Strip MiniMessage / legacy tags for character width estimation
                    String raw = tc.getText().replaceAll("<[^>]*>", "").replaceAll("&[0-9a-fk-orA-FK-OR]", "");
                    return Math.max(16, raw.length() * charWidth);
                } else {
                    return Math.max(16, (int) Math.ceil(effFontSize * 1.35));
                }
            } else if (component instanceof PixelTextComponent ptc) {
                int effFontSize = ptc.getFontSize() > 0 ? ptc.getFontSize() : (ptc.getFontScale() > 1 ? 14 * ptc.getFontScale() : 16);
                if (isWidth) {
                    String raw = ptc.getText().replaceAll("<[^>]*>", "").replaceAll("&[0-9a-fk-orA-FK-OR]", "");
                    int charWidth = Math.max(1, (int) Math.ceil(effFontSize * 0.75));
                    return Math.max(16, raw.length() * charWidth);
                } else {
                    return Math.max(16, (int) Math.ceil(effFontSize * 1.35));
                }
            }
            return isWidth ? parentDim : parentDim;
        }

        try {
            return Integer.parseInt(token);
        } catch (NumberFormatException ignored) {
            return parentDim;
        }
    }

    private static int[] parsePosition(String posStr, int parentWidth, int parentHeight) {
        if (posStr == null || posStr.isBlank()) {
            return new int[]{0, 0};
        }
        String[] parts = posStr.trim().split("\\s+");
        int x = 0;
        int y = 0;

        if (parts.length > 0) {
            x = parseCoord(parts[0], parentWidth);
        }
        if (parts.length > 1) {
            y = parseCoord(parts[1], parentHeight);
        }
        return new int[]{x, y};
    }

    private static int parseCoord(String token, int parentDim) {
        token = token.trim().toLowerCase();
        if (token.endsWith("%")) {
            try {
                double pct = Double.parseDouble(token.substring(0, token.length() - 1)) / 100.0;
                return (int) Math.round(parentDim * pct);
            } catch (NumberFormatException ignored) {}
        }
        try {
            return Integer.parseInt(token);
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }
}