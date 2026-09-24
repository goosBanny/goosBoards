package me.goosbanny.goosboards.scene.component.interactive;

import me.goosbanny.goosboards.scene.RenderContext;
import me.goosbanny.goosboards.scene.component.UIComponent;

import me.goosbanny.goosboards.render.buffer.CanvasBuffer;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Non-visual action listener component triggered by external events or /gb trigger.
 */
public class ActionListenerComponent extends UIComponent {

    private final String identifier;
    private final Map<String, Object> actions = new LinkedHashMap<>();

    public ActionListenerComponent(String id, String identifier) {
        super(id, false);
        this.identifier = identifier != null ? identifier : id;
    }

    public String getIdentifier() {
        return identifier;
    }

    public Map<String, Object> getActions() {
        return Collections.unmodifiableMap(actions);
    }

    public void addAction(String key, Object action) {
        if (key != null && action != null) {
            this.actions.put(key, action);
        }
    }

    @Override
    public void render(CanvasBuffer canvas, RenderContext ctx) {
        // Non-visual component
    }

    @Override
    public boolean containsPixel(int localX, int localY) {
        return false;
    }
}