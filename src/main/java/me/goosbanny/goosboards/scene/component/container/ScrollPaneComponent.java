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
    private int scrollOffsetX = 0;
    private int scrollOffsetY = 0;

    private boolean mouseScroll = true;
    private boolean mouseScrollVertical = true;
    private int mouseScrollAmount = 32;
    private String scrollBehavior = "snap";
    private CanvasBufferImpl innerBuffer = null;
    private int lastTilesW = 0;
    private int lastTilesH = 0;

    private final Map<UUID, Integer> viewerScrollOffsets = new ConcurrentHashMap<>();

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
            int next = Math.max(0, Math.min(this.scrollOffsetX + delta, Math.max(0, scrollInnerWidth - getBounds().width())));
            if (this.scrollOffsetX == next) {
                return false;
            }
            this.scrollOffsetX = next;
            return true;
        }
    }

    public void clearViewerScroll(UUID viewerId) {
        if (viewerId != null) {
            viewerScrollOffsets.remove(viewerId);
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

        int effectiveOffsetY = (ctx != null && ctx.viewer() != null)
                ? getEffectiveScrollOffsetY(ctx.viewer().getUniqueId())
                : scrollOffsetY;

        int tilesW = Math.max(1, (innerW + CanvasBufferImpl.TILE_SIZE - 1) / CanvasBufferImpl.TILE_SIZE);
        int tilesH = Math.max(1, (innerH + CanvasBufferImpl.TILE_SIZE - 1) / CanvasBufferImpl.TILE_SIZE);

        if (innerBuffer == null || innerBuffer.getWidthTiles() < tilesW || innerBuffer.getHeightTiles() < tilesH) {
            int allocW = Math.max(tilesW, innerBuffer != null ? innerBuffer.getWidthTiles() : tilesW);
            int allocH = Math.max(tilesH, innerBuffer != null ? innerBuffer.getHeightTiles() : tilesH);
            innerBuffer = new CanvasBufferImpl(allocW, allocH);
            lastTilesW = allocW;
            lastTilesH = allocH;
        } else {
            innerBuffer.clear();
        }

        // Render children into intermediate inner canvas buffer
        for (UIComponent child : getChildren()) {
            child.render(innerBuffer, ctx);
        }

        // Blit only the visible region [scrollOffsetX, effectiveOffsetY, scrollOffsetX + visibleW, effectiveOffsetY + visibleH]
        int startX = Math.max(0, scrollOffsetX);
        int startY = Math.max(0, effectiveOffsetY);
        int endX = Math.min(innerW, scrollOffsetX + visibleW);
        int endY = Math.min(innerH, effectiveOffsetY + visibleH);

        for (int cy = startY; cy < endY; cy++) {
            int screenY = bounds.y() + (cy - effectiveOffsetY);
            if (screenY < bounds.y() || screenY >= bounds.y() + visibleH) {
                continue;
            }
            for (int cx = startX; cx < endX; cx++) {
                int screenX = bounds.x() + (cx - scrollOffsetX);
                if (screenX < bounds.x() || screenX >= bounds.x() + visibleW) {
                    continue;
                }
                byte pixel = innerBuffer.getPixel(cx, cy);
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

        int offsetY = getEffectiveScrollOffsetY(viewerId);
        int contentX = localX + scrollOffsetX;
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

        int offsetY = getEffectiveScrollOffsetY(viewerId);
        int contentX = localX + scrollOffsetX;
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