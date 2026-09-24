package me.goosbanny.goosboards.scene.component.container;

import me.goosbanny.goosboards.scene.Rect;
import me.goosbanny.goosboards.scene.RenderContext;

import me.goosbanny.goosboards.render.buffer.CanvasBufferImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ScrollPaneScissorTest {

    @Test
    @DisplayName("Test 6.13: ScrollPane scissor - content below visible window not rendered to main canvas")
    void testScissorClipsContentBelowVisibleWindow() {
        // Main canvas: 2 tiles wide (256px), 4 tiles high (512px)
        CanvasBufferImpl mainCanvas = new CanvasBufferImpl(2, 4);

        ScrollPaneComponent scrollPane = new ScrollPaneComponent("scroll-scissor", false);
        // Visible window: 256x200 at screen position (0, 0)
        scrollPane.setBounds(new Rect(0, 0, 256, 200));
        scrollPane.setScrollInnerWidth(256);
        scrollPane.setScrollInnerHeight(400);
        scrollPane.setScrollOffsetY(0);

        // Child 1: Visible element inside window at content Y=50..100
        BackgroundComponent visibleComp = new BackgroundComponent("visible-child", false);
        visibleComp.setBounds(new Rect(0, 50, 256, 50));
        visibleComp.setColor(0xFF00FF00); // Green
        scrollPane.addChild(visibleComp);

        // Child 2: Scrolled-out element below window at content Y=250..350
        BackgroundComponent scrolledOutComp = new BackgroundComponent("scrolled-out-child", false);
        scrolledOutComp.setBounds(new Rect(0, 250, 256, 100));
        scrolledOutComp.setColor(0xFFFF0000); // Red
        scrollPane.addChild(scrolledOutComp);

        // Render into main canvas
        scrollPane.render(mainCanvas, RenderContext.empty());

        // 1. Verify visible child DID render to main canvas (Y=50..99)
        byte visiblePixel = mainCanvas.getPixel(100, 75);
        assertNotEquals(0, visiblePixel, "Visible child pixels within [0, 200) should be rendered");

        // 2. Verify scissored child did NOT render to main canvas (Y=250..350)
        for (int y = 200; y < 400; y++) {
            assertEquals(0, mainCanvas.getPixel(100, y),
                    "Pixels below visible height (inner-y > 200) must NOT appear on main canvas at y=" + y);
        }
    }

    @Test
    @DisplayName("ScrollPane scissor with scroll offset renders shifted visible region")
    void testScissorWithScrollOffset() {
        CanvasBufferImpl mainCanvas = new CanvasBufferImpl(2, 4);

        ScrollPaneComponent scrollPane = new ScrollPaneComponent("scroll-offset-test", false);
        scrollPane.setBounds(new Rect(0, 0, 256, 200));
        scrollPane.setScrollInnerWidth(256);
        scrollPane.setScrollInnerHeight(400);
        // Scrolled down by 200px: visible content is Y=200..400
        scrollPane.setScrollOffsetY(200);

        // Child at content Y=250..300: now visible at screen Y = 250 - 200 = 50..100
        BackgroundComponent shiftedComp = new BackgroundComponent("shifted-child", false);
        shiftedComp.setBounds(new Rect(0, 250, 256, 50));
        shiftedComp.setColor(0xFF0000FF);
        scrollPane.addChild(shiftedComp);

        scrollPane.render(mainCanvas, RenderContext.empty());

        // Rendered at screen Y=50..99
        assertNotEquals(0, mainCanvas.getPixel(50, 75), "Shifted content should be drawn at screen Y=75");
        // Screen Y=200+ must still be 0
        assertEquals(0, mainCanvas.getPixel(50, 250), "Pixels beyond visible window must be 0");
    }
}