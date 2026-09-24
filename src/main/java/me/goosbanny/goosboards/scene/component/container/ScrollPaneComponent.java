package me.goosbanny.goosboards.scene.component.container;

import me.goosbanny.goosboards.scene.Rect;
import me.goosbanny.goosboards.scene.RenderContext;
import me.goosbanny.goosboards.scene.component.UIComponent;

import me.goosbanny.goosboards.render.buffer.CanvasBuffer;
import me.goosbanny.goosboards.render.buffer.CanvasBufferImpl;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Scrollable container component supporting viewport clipping and offset content hit-testing.
 */
public class ScrollPaneComponent extends UIComponent {

    private int scrollInnerWidth = 0;
    private int scrollInnerHeight = 0;
    private volatile int scrollOffsetX = 0;
    private volatile int scrollOffsetY = 0;

    private boolean mouseScroll = true;
    private boolean mouseScrollVertical = true;
    private int mouseScrollAmount = 32;
    private String scrollBehavior = "snap";
    private CanvasBufferImpl innerBuffer = null;
    private int lastTilesW = 0;
    private int lastTilesH = 0;

    private final Map<UUID, Integer> viewerScrollOffsets = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> viewerScrollOffsetsX = new ConcurrentHashMap<>();
    private final Map<UUID, CanvasBufferImpl> perViewerInnerBuffers = new ConcurrentHashMap<>();

    public ScrollPaneComponent(String id, boolean contextDependent) {
        super(id, contextDependent);
    }

    @Override
    public boolean isContextDependent() {
        return super.isContextDependent() || mouseScroll;
    }

    public int getEffectiveScrollOffsetY(UUID viewerId) {
        if (viewerId != null && mouseScroll) {
            return viewerScrollOffsets.getOrDefault(viewerId, scrollOffsetY);
        }
        return scrollOffsetY;
    }

    public void setViewerScrollOffsetY(UUID viewerId, int offset) {
        if (viewerId == null) return;
        int maxScroll = Math.max(0, scrollInnerHeight - getBounds().height());
        int clamped = Math.max(0, Math.min(offset, maxScroll));
        viewerScrollOffsets.put(viewerId, clamped);
    }

    public int getEffectiveScrollOffsetX(UUID viewerId) {
        if (viewerId != null && mouseScroll) {
            return viewerScrollOffsetsX.getOrDefault(viewerId, scrollOffsetX);
        }
        return scrollOffsetX;
    }

    public void setViewerScrollOffsetX(UUID viewerId, int offset) {
        if (viewerId == null) return;
        int maxScroll = Math.max(0, scrollInnerWidth - getBounds().width());
        int clamped = Math.max(0, Math.min(offset, maxScroll));
        viewerScrollOffsetsX.put(viewerId, clamped);
    }

    public boolean scroll(UUID viewerId, int delta) {
        if (mouseScrollVertical) {
            int current = getEffectiveScrollOffsetY(viewerId);
            int maxScroll = Math.max(0, scrollInnerHeight - getBounds().height());
            int next = Math.max(0, Math.min(current + delta, maxScroll));
            if (current == next) {
                return false;
            }
            setViewerScrollOffsetY(viewerId, next);
            return true;
        } else {
            int current = getEffectiveScrollOffsetX(viewerId);
            int maxScroll = Math.max(0, scrollInnerWidth - getBounds().width());
            int next = Math.max(0, Math.min(current + delta, maxScroll));
            if (current == next) {
                return false;
            }
            setViewerScrollOffsetX(viewerId, next);
            return true;
        }
    }

    public void clearViewerScroll(UUID viewerId) {
        if (viewerId != null) {
            viewerScrollOffsets.remove(viewerId);
            viewerScrollOffsetsX.remove(viewerId);
            perViewerInnerBuffers.remove(viewerId);
        }
    }

    public void clearAllViewerScrolls() {
        viewerScrollOffsets.clear();
        viewerScrollOffsetsX.clear();
        perViewerInnerBuffers.clear();
    }

    public static void clearViewerScrolls(List<UIComponent> components, UUID viewerId) {
        if (components == null || viewerId == null) return;
        for (UIComponent comp : components) {
            if (comp instanceof ScrollPaneComponent pane) {
                pane.clearViewerScroll(viewerId);
            }
            clearViewerScrolls(comp.getChildren(), viewerId);
        }
    }

    @Override
    public void render(CanvasBuffer canvas, RenderContext ctx) {
        if (canvas == null) {
            return;
        }

        Rect bounds = getBounds();
        int visibleW = bounds.width();
        int visibleH = bounds.height();
        if (visibleW <= 0 || visibleH <= 0) {
            return;
        }

        int innerW = scrollInnerWidth > 0 ? scrollInnerWidth : visibleW;
        int innerH = scrollInnerHeight > 0 ? scrollInnerHeight : visibleH;

        UUID viewerId = (ctx != null && ctx.viewer() != null) ? ctx.viewer().getUniqueId() : null;
        int effectiveOffsetY = (viewerId != null) ? getEffectiveScrollOffsetY(viewerId) : scrollOffsetY;
        int effectiveOffsetX = (viewerId != null) ? getEffectiveScrollOffsetX(viewerId) : scrollOffsetX;

        int tilesW = Math.max(1, (innerW + CanvasBufferImpl.TILE_SIZE - 1) / CanvasBufferImpl.TILE_SIZE);
        int tilesH = Math.max(1, (innerH + CanvasBufferImpl.TILE_SIZE - 1) / CanvasBufferImpl.TILE_SIZE);

        CanvasBufferImpl targetInnerBuffer;
        if (viewerId != null) {
            CanvasBufferImpl buf = perViewerInnerBuffers.get(viewerId);
            if (buf == null || buf.getWidthTiles() < tilesW || buf.getHeightTiles() < tilesH) {
                int allocW = Math.max(tilesW, buf != null ? buf.getWidthTiles() : tilesW);
                int allocH = Math.max(tilesH, buf != null ? buf.getHeightTiles() : tilesH);
                buf = new CanvasBufferImpl(allocW, allocH);
                perViewerInnerBuffers.put(viewerId, buf);
            } else {
                buf.clear();
            }
            targetInnerBuffer = buf;
        } else {
            if (innerBuffer == null || innerBuffer.getWidthTiles() < tilesW || innerBuffer.getHeightTiles() < tilesH) {
                int allocW = Math.max(tilesW, innerBuffer != null ? innerBuffer.getWidthTiles() : tilesW);
                int allocH = Math.max(tilesH, innerBuffer != null ? innerBuffer.getHeightTiles() : tilesH);
                innerBuffer = new CanvasBufferImpl(allocW, allocH);
                lastTilesW = allocW;
                lastTilesH = allocH;
            } else {
                innerBuffer.clear();
            }
            targetInnerBuffer = innerBuffer;
        }

        // Render children into intermediate inner canvas buffer
        for (UIComponent child : getChildren()) {
            child.render(targetInnerBuffer, ctx);
        }

        // Blit only the visible region [effectiveOffsetX, effectiveOffsetY, effectiveOffsetX + visibleW, effectiveOffsetY + visibleH]
        int startX = Math.max(0, effectiveOffsetX);
        int startY = Math.max(0, effectiveOffsetY);
        int endX = Math.min(innerW, effectiveOffsetX + visibleW);
        int endY = Math.min(innerH, effectiveOffsetY + visibleH);

        for (int cy = startY; cy < endY; cy++) {
            int screenY = bounds.y() + (cy - effectiveOffsetY);
            if (screenY < bounds.y() || screenY >= bounds.y() + visibleH) {
                continue;
            }
            for (int cx = startX; cx < endX; cx++) {
                int screenX = bounds.x() + (cx - effectiveOffsetX);
                if (screenX < bounds.x() || screenX >= bounds.x() + visibleW) {
                    continue;
                }
                byte pixel = targetInnerBuffer.getPixel(cx, cy);
                if (pixel != 0) {
                    canvas.setPixel(screenX, screenY, pixel);
                }
            }
        }
    }

    @Override
    public boolean containsPixel(int localX, int localY) {
        return containsPixel(null, localX, localY);
    }

    public boolean containsPixel(UUID viewerId, int localX, int localY) {
        // Clip to visible window
        if (localX < 0 || localX >= getBounds().width() || localY < 0 || localY >= getBounds().height()) {
            return false;
        }

        int offsetX = getEffectiveScrollOffsetX(viewerId);
        int offsetY = getEffectiveScrollOffsetY(viewerId);
        int contentX = localX + offsetX;
        int contentY = localY + offsetY;

        for (UIComponent child : getChildren()) {
            Rect cb = child.getBounds();
            if (cb.contains(contentX, contentY)) {
                int childLocalX = contentX - cb.x();
                int childLocalY = contentY - cb.y();
                if (child.containsPixel(childLocalX, childLocalY)) {
                    return true;
                }
            }
        }
        return false;
    }

    public boolean isInViewport(int localX, int localY) {
        return localX >= 0 && localX < getBounds().width() && localY >= 0 && localY < getBounds().height();
    }

    /**
     * Finds the deepest component hit at the given local coordinates within this scroll pane,
     * honoring the viewport clip and scroll offset.
     *
     * @param localX local X relative to scroll pane visible top-left
     * @param localY local Y relative to scroll pane visible top-left
     * @return the hit UIComponent or null if outside viewport or missing children
     */
    public UIComponent findDeepestAt(int localX, int localY) {
        return findDeepestAt(null, localX, localY);
    }

    public UIComponent findDeepestAt(UUID viewerId, int localX, int localY) {
        if (localX < 0 || localX >= getBounds().width() || localY < 0 || localY >= getBounds().height()) {
            return null;
        }

        int offsetX = getEffectiveScrollOffsetX(viewerId);
        int offsetY = getEffectiveScrollOffsetY(viewerId);
        int contentX = localX + offsetX;
        int contentY = localY + offsetY;

        List<UIComponent> kids = getChildren();
        for (int i = kids.size() - 1; i >= 0; i--) {
            UIComponent child = kids.get(i);
            Rect cb = child.getBounds();
            if (cb.contains(contentX, contentY)) {
                int childLocalX = contentX - cb.x();
                int childLocalY = contentY - cb.y();
                if (child instanceof ScrollPaneComponent subPane) {
                    UIComponent deep = subPane.findDeepestAt(viewerId, childLocalX, childLocalY);
                    if (deep != null) {
                        return deep;
                    }
                } else if (child.containsPixel(childLocalX, childLocalY)) {
                    // Check if child has deeper children
                    UIComponent deep = findChildDeepest(child, childLocalX, childLocalY);
                    return deep != null ? deep : child;
                }
            }
        }
        return null;
    }

    private UIComponent findChildDeepest(UIComponent parent, int localX, int localY) {
        List<UIComponent> kids = parent.getChildren();
        for (int i = kids.size() - 1; i >= 0; i--) {
            UIComponent k = kids.get(i);
            Rect b = k.getBounds();
            if (b.contains(localX, localY)) {
                int kx = localX - b.x();
                int ky = localY - b.y();
                if (k.containsPixel(kx, ky)) {
                    UIComponent deep = findChildDeepest(k, kx, ky);
                    return deep != null ? deep : k;
                }
            }
        }
        return null;
    }

    public int getScrollInnerWidth() {
        return scrollInnerWidth;
    }

    public void setScrollInnerWidth(int scrollInnerWidth) {
        this.scrollInnerWidth = scrollInnerWidth;
    }

    public int getScrollInnerHeight() {
        return scrollInnerHeight;
    }

    public void setScrollInnerHeight(int scrollInnerHeight) {
        this.scrollInnerHeight = scrollInnerHeight;
    }

    public int getScrollOffsetX() {
        return scrollOffsetX;
    }

    public void setScrollOffsetX(int scrollOffsetX) {
        this.scrollOffsetX = scrollOffsetX;
    }

    public int getScrollOffsetY() {
        return scrollOffsetY;
    }

    public void setScrollOffsetY(int scrollOffsetY) {
        this.scrollOffsetY = scrollOffsetY;
    }

    public boolean isMouseScroll() {
        return mouseScroll;
    }

    public void setMouseScroll(boolean mouseScroll) {
        this.mouseScroll = mouseScroll;
    }

    public boolean isMouseScrollVertical() {
        return mouseScrollVertical;
    }

    public void setMouseScrollVertical(boolean mouseScrollVertical) {
        this.mouseScrollVertical = mouseScrollVertical;
    }

    public int getMouseScrollAmount() {
        return mouseScrollAmount;
    }

    public void setMouseScrollAmount(int mouseScrollAmount) {
        this.mouseScrollAmount = mouseScrollAmount;
    }

    public String getScrollBehavior() {
        return scrollBehavior;
    }

    public void setScrollBehavior(String scrollBehavior) {
        if (scrollBehavior != null && !scrollBehavior.isBlank()) {
            this.scrollBehavior = scrollBehavior.trim();
        }
    }
}