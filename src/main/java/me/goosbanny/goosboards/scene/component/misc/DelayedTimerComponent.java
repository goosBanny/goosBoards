package me.goosbanny.goosboards.scene.component.misc;

import me.goosbanny.goosboards.scene.RenderContext;
import me.goosbanny.goosboards.scene.component.UIComponent;

import me.goosbanny.goosboards.render.buffer.CanvasBuffer;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Non-visual delayed function timer component in scenes.
 */
public class DelayedTimerComponent extends UIComponent {

    private long delayMs = 60000L;
    private final Map<String, Object> functions = new LinkedHashMap<>();

    public DelayedTimerComponent(String id) {
        super(id, false);
    }

    public long getDelayMs() {
        return delayMs;
    }

    public void setDelayMs(long delayMs) {
        this.delayMs = delayMs;
    }

    public Map<String, Object> getFunctions() {
        return Collections.unmodifiableMap(functions);
    }

    public void addFunction(String key, Object function) {
        if (key != null && function != null) {
            this.functions.put(key, function);
        }
    }

    @Override
    public void render(CanvasBuffer canvas, RenderContext ctx) {
        // Non-visual component
    }

    @Override
    public boolean isDynamic() {
        return true;
    }

    @Override
    public boolean containsPixel(int localX, int localY) {
        return false;
    }
}