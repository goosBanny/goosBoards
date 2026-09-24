package me.goosbanny.goosboards.scene.component;

import me.goosbanny.goosboards.scene.Rect;
import me.goosbanny.goosboards.scene.RenderContext;

import me.goosbanny.goosboards.render.buffer.CanvasBuffer;
import me.goosbanny.goosboards.render.palette.PaletteQuantizer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Abstract base class for all scene graph DOM elements.
 */
public abstract class UIComponent {
    private final String id;
    private boolean contextDependent;
    private Rect bounds = new Rect(0, 0, 0, 0);
    private UIComponent parent;
    private final List<UIComponent> children = new ArrayList<>();

    private String positionStr = "0 0";
    private String sizeStr = "auto auto";
    private String alignment = "left";
    private String verticalAlignment = "top";

    private int outlineColor = 0x00000000;
    private double outlineWidth = 0.0;
    private int onHoverOutlineColor = 0x00000000;
    private double onHoverOutlineWidth = 0.0;

    protected UIComponent(String id, boolean contextDependent) {
        this.id = id != null ? id : "";
        this.contextDependent = contextDependent;
    }

    /**
     * Template method: renders this component into the canvas buffer.
     *
     * @param canvas the target canvas buffer
     * @param ctx    the current render context
     */
    public abstract void render(CanvasBuffer canvas, RenderContext ctx);

    /**
     * Template method: tests whether a local pixel coordinate (relative to this component's top-left)
     * hits this component.
     *
     * @param localX the X offset within this component [0, bounds.width())
     * @param localY the Y offset within this component [0, bounds.height())
     * @return true if the pixel hits this component
     */
    public abstract boolean containsPixel(int localX, int localY);

    public String getId() {
        return id;
    }

    public boolean isContextDependent() {
        return contextDependent || onHoverOutlineColor != 0 || onHoverOutlineWidth > 0.0;
    }

    public void setContextDependent(boolean contextDependent) {
        this.contextDependent = contextDependent;
    }

    public int getOutlineColor() {
        return outlineColor;
    }

    public void setOutlineColor(int outlineColor) {
        this.outlineColor = outlineColor;
    }

    public double getOutlineWidth() {
        return outlineWidth;
    }

    public void setOutlineWidth(double outlineWidth) {
        this.outlineWidth = Math.max(0.0, outlineWidth);
    }

    public int getOnHoverOutlineColor() {
        return onHoverOutlineColor;
    }

    public void setOnHoverOutlineColor(int onHoverOutlineColor) {
        this.onHoverOutlineColor = onHoverOutlineColor;
    }

    public double getOnHoverOutlineWidth() {
        return onHoverOutlineWidth;
    }

    public void setOnHoverOutlineWidth(double onHoverOutlineWidth) {
        this.onHoverOutlineWidth = Math.max(0.0, onHoverOutlineWidth);
    }

    public boolean hasOutline() {
        return (outlineColor != 0 && outlineWidth > 0.0) || onHoverOutlineColor != 0 || onHoverOutlineWidth > 0.0;
    }

    /**
     * Renders a rectangular border outline around this component's bounds into the canvas.
     */
    public void renderBoundsOutline(CanvasBuffer canvas, RenderContext ctx) {
        if (canvas == null) return;
        Rect b = getBounds();
        if (b.width() <= 0 || b.height() <= 0) return;

        boolean isHovered = ctx != null && ctx.isHovered(getId());
        int activeOutline = (isHovered && onHoverOutlineColor != 0) ? onHoverOutlineColor : outlineColor;
        double activeWidth;
        if (isHovered) {
            if (onHoverOutlineWidth > 0.0) {
                activeWidth = onHoverOutlineWidth;
            } else if (onHoverOutlineColor != 0 && outlineWidth <= 0.0) {
                activeWidth = 1.0;
            } else {
                activeWidth = outlineWidth;
            }
        } else {
            activeWidth = outlineWidth;
        }

        if (activeOutline == 0 || activeWidth <= 0.0) return;

        byte colorIdx = PaletteQuantizer.match(activeOutline);
        if (colorIdx == 0) return;

        int ow = Math.max(1, (int) Math.ceil(activeWidth));
        int bx = b.x();
        int by = b.y();
        int bw = b.width();
        int bh = b.height();
        int canvasW = canvas.getWidth();
        int canvasH = canvas.getHeight();

        for (int o = 0; o < ow; o++) {
            int topY = by + o;
            int bottomY = by + bh - 1 - o;
            for (int x = bx + o; x < bx + bw - o; x++) {
                if (x >= 0 && x < canvasW) {
                    if (topY >= 0 && topY < canvasH) canvas.setPixel(x, topY, colorIdx);
                    if (bottomY >= 0 && bottomY < canvasH) canvas.setPixel(x, bottomY, colorIdx);
                }
            }
            int leftX = bx + o;
            int rightX = bx + bw - 1 - o;
            for (int y = by + o; y < by + bh - o; y++) {
                if (y >= 0 && y < canvasH) {
                    if (leftX >= 0 && leftX < canvasW) canvas.setPixel(leftX, y, colorIdx);
                    if (rightX >= 0 && rightX < canvasW) canvas.setPixel(rightX, y, colorIdx);
                }
            }
        }
    }

    public boolean isDynamic() {
        return false;
    }

    public Rect getBounds() {
        return bounds;
    }

    public void setBounds(Rect bounds) {
        this.bounds = bounds != null ? bounds : new Rect(0, 0, 0, 0);
    }

    public UIComponent getParent() {
        return parent;
    }

    public void setParent(UIComponent parent) {
        this.parent = parent;
    }

    public List<UIComponent> getChildren() {
        return Collections.unmodifiableList(children);
    }

    public void addChild(UIComponent child) {
        if (child != null) {
            child.setParent(this);
            children.add(child);
            if (child.isContextDependent()) {
                this.contextDependent = true;
            }
        }
    }

    public String getPositionStr() {
        return positionStr;
    }

    public void setPositionStr(String positionStr) {
        if (positionStr != null && !positionStr.isBlank()) {
            this.positionStr = positionStr.trim();
        }
    }

    public String getSizeStr() {
        return sizeStr;
    }

    public void setSizeStr(String sizeStr) {
        if (sizeStr != null && !sizeStr.isBlank()) {
            this.sizeStr = sizeStr.trim();
        }
    }

    public String getAlignment() {
        return alignment;
    }

    public void setAlignment(String alignment) {
        if (alignment != null && !alignment.isBlank()) {
            this.alignment = alignment.trim().toLowerCase();
        }
    }

    public String getVerticalAlignment() {
        return verticalAlignment;
    }

    public void setVerticalAlignment(String verticalAlignment) {
        if (verticalAlignment != null && !verticalAlignment.isBlank()) {
            this.verticalAlignment = verticalAlignment.trim().toLowerCase();
        }
    }
}