package me.goosbanny.goosboards.scene.component.container;

import me.goosbanny.goosboards.scene.Rect;
import me.goosbanny.goosboards.scene.component.UIComponent;
import me.goosbanny.goosboards.scene.component.interactive.ButtonComponent;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ScrollPaneClipTest {

    @Test
    @DisplayName("Test 5.7: ScrollPane clip - button in scrolled-out region is NOT clickable")
    void testButtonScrolledOutNotClickable() {
        ScrollPaneComponent scrollPane = new ScrollPaneComponent("pane-1", false);
        scrollPane.setBounds(new Rect(0, 0, 480, 300));
        scrollPane.setScrollInnerWidth(480);
        scrollPane.setScrollInnerHeight(600);
        scrollPane.setScrollOffsetY(0);

        // Button positioned at content Y=400 (outside 300px visible window)
        ButtonComponent button = new ButtonComponent("btn-scrolled-out", false);
        button.setBounds(new Rect(10, 400, 100, 40));
        scrollPane.addChild(button);

        // containsPixel(50, 350) is tested relative to scroll pane visible top-left
        // localY = 350 exceeds visibleHeight (300) -> must be false
        assertFalse(scrollPane.containsPixel(50, 350), "Outside visible window must not be clickable");

        // Even at local coordinates within window (e.g. 50, 50), content is at Y=50, not Y=400
        assertFalse(scrollPane.containsPixel(50, 50), "Button at Y=400 should not be hit at local Y=50");
        assertNull(scrollPane.findDeepestAt(50, 350), "findDeepestAt must return null outside visible clip");
    }

    @Test
    @DisplayName("Test 5.8: ScrollPane clip - button within visible region IS clickable")
    void testButtonWithinVisibleRegionIsClickable() {
        ScrollPaneComponent scrollPane = new ScrollPaneComponent("pane-2", false);
        scrollPane.setBounds(new Rect(0, 0, 480, 300));
        scrollPane.setScrollInnerWidth(480);
        scrollPane.setScrollInnerHeight(600);
        scrollPane.setScrollOffsetY(100);

        // Button 1 at content Y=50 (visible at localY = 50 - 100 = -50 -> scrolled up out of view)
        ButtonComponent btn1 = new ButtonComponent("btn-top-scrolled-out", false);
        btn1.setBounds(new Rect(10, 50, 100, 40));
        scrollPane.addChild(btn1);

        // Button 2 at content Y=150 (visible at localY = 150 - 100 = 50 -> in viewport)
        ButtonComponent btn2 = new ButtonComponent("btn-visible", false);
        btn2.setBounds(new Rect(10, 150, 100, 40));
        scrollPane.addChild(btn2);

        // At local coordinates (50, -50), localY is negative -> not visible (miss)
        assertFalse(scrollPane.containsPixel(50, -50), "Local coordinates outside visible window must miss");

        // At local coordinates (50, 50):
        // localY is in [0, 300), contentY = 50 + 100 = 150.
        // Hits btn2 which is at content Y=150!
        assertTrue(scrollPane.containsPixel(50, 50), "Button at content Y=150 must hit when scrolled by 100");

        UIComponent hit = scrollPane.findDeepestAt(50, 50);
        assertNotNull(hit, "Should find deepest hit component");
        assertEquals("btn-visible", hit.getId());
    }

    @Test
    @DisplayName("ScrollPane bounds clipping rejects pixels outside width/height")
    void testViewportBoundaryClipping() {
        ScrollPaneComponent scrollPane = new ScrollPaneComponent("pane-3", false);
        scrollPane.setBounds(new Rect(50, 50, 200, 200));

        ButtonComponent btn = new ButtonComponent("btn-inside", false);
        btn.setBounds(new Rect(0, 0, 200, 200));
        scrollPane.addChild(btn);

        // Test boundary values
        assertTrue(scrollPane.containsPixel(0, 0));
        assertTrue(scrollPane.containsPixel(199, 199));
        assertFalse(scrollPane.containsPixel(-1, 50));
        assertFalse(scrollPane.containsPixel(50, -1));
        assertFalse(scrollPane.containsPixel(200, 50));
        assertFalse(scrollPane.containsPixel(50, 200));
    }
}
