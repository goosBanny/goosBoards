package me.goosbanny.goosboards.scene.component.container;

import me.goosbanny.goosboards.scene.Rect;
import me.goosbanny.goosboards.scene.component.interactive.ButtonComponent;

import me.goosbanny.goosboards.render.buffer.CanvasBufferImpl;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ScrollPaneViewerScrollTest {

    @Test
    @DisplayName("Per-viewer scroll isolation: viewer 1 scroll does not alter viewer 2")
    void testPerViewerScrollIsolation() {
        ScrollPaneComponent pane = new ScrollPaneComponent("scroll-viewer-test", false);
        pane.setBounds(new Rect(0, 0, 400, 200));
        pane.setScrollInnerWidth(400);
        pane.setScrollInnerHeight(600);
        pane.setMouseScroll(true);
        pane.setMouseScrollAmount(32);

        assertTrue(pane.isContextDependent(), "ScrollPane with mouseScroll=true must report context-dependent");

        UUID viewerA = UUID.randomUUID();
        UUID viewerB = UUID.randomUUID();

        assertEquals(0, pane.getEffectiveScrollOffsetY(viewerA));
        assertEquals(0, pane.getEffectiveScrollOffsetY(viewerB));

        // Viewer A scrolls down twice (64px)
        assertTrue(pane.scroll(viewerA, 32));
        assertTrue(pane.scroll(viewerA, 32));
        assertEquals(64, pane.getEffectiveScrollOffsetY(viewerA));

        // Viewer B remains at 0
        assertEquals(0, pane.getEffectiveScrollOffsetY(viewerB));

        // Viewer A scrolls up past top: clamps to 0
        assertTrue(pane.scroll(viewerA, -100));
        assertEquals(0, pane.getEffectiveScrollOffsetY(viewerA));

        // Cannot scroll up past 0 (returns false when unchanged)
        assertFalse(pane.scroll(viewerA, -32));
        assertEquals(0, pane.getEffectiveScrollOffsetY(viewerA));

        // Max scroll height is innerHeight (600) - visibleHeight (200) = 400
        assertTrue(pane.scroll(viewerB, 500));
        assertEquals(400, pane.getEffectiveScrollOffsetY(viewerB));
        assertFalse(pane.scroll(viewerB, 32), "Should return false when already clamped at maximum scroll");

        // Clear viewer scroll resets
        pane.clearViewerScroll(viewerB);
        assertEquals(0, pane.getEffectiveScrollOffsetY(viewerB));
    }

    @Test
    @DisplayName("findDeepestAt honors per-viewer scroll offset")
    void testFindDeepestAtWithPerViewerScroll() {
        ScrollPaneComponent pane = new ScrollPaneComponent("scroll-deep-test", false);
        pane.setBounds(new Rect(0, 0, 400, 200));
        pane.setScrollInnerWidth(400);
        pane.setScrollInnerHeight(600);
        pane.setMouseScroll(true);

        ButtonComponent btn1 = new ButtonComponent("btn-top", false);
        btn1.setBounds(new Rect(10, 20, 100, 40));
        pane.addChild(btn1);

        ButtonComponent btn2 = new ButtonComponent("btn-bottom", false);
        btn2.setBounds(new Rect(10, 250, 100, 40));
        pane.addChild(btn2);

        UUID viewerA = UUID.randomUUID();
        UUID viewerB = UUID.randomUUID();

        // Viewer A is at offset 0: local (20, 30) hits btn1
        assertEquals(btn1, pane.findDeepestAt(viewerA, 20, 30));

        // Viewer B scrolls down by 200px
        pane.scroll(viewerB, 200);

        // At viewer B's scroll offset (200):
        // local (20, 30) maps to content (20, 230), misses btn1
        assertNull(pane.findDeepestAt(viewerB, 20, 30));

        // local (20, 60) maps to content (20, 260), hits btn2
        assertEquals(btn2, pane.findDeepestAt(viewerB, 20, 60));
    }
}