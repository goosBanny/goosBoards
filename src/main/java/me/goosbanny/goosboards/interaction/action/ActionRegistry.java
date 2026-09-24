package me.goosbanny.goosboards.interaction.action;

import me.goosbanny.goosboards.interaction.action.handler.CommandActionHandler;
import me.goosbanny.goosboards.interaction.action.handler.PlaySoundActionHandler;
import me.goosbanny.goosboards.interaction.action.handler.SendMessageActionHandler;
import me.goosbanny.goosboards.interaction.action.handler.SwitchSceneActionHandler;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry holding strategy handlers for board actions.
 */
public class ActionRegistry {

    private final Map<String, ActionHandler> handlers = new ConcurrentHashMap<>();

    public ActionRegistry() {
        registerDefaults();
    }

    public void registerDefaults() {
        register(new CommandActionHandler());
        register(new SwitchSceneActionHandler());
        register(new PlaySoundActionHandler());
        register(new SendMessageActionHandler());
    }

    public void register(ActionHandler handler) {
        handlers.put(handler.getType().toLowerCase(Locale.ROOT), handler);
    }

    public ActionHandler get(String type) {
        if (type == null) return null;
        return handlers.get(type.toLowerCase(Locale.ROOT));
    }
}
