package me.goosbanny.goosboards.scene.parser.component;

import me.goosbanny.goosboards.scene.component.interactive.ActionListenerComponent;
import org.bukkit.configuration.ConfigurationSection;

public class ActionListenerParser implements ComponentParser<ActionListenerComponent> {

    @Override
    public String getType() {
        return "action_listener";
    }

    @Override
    public ActionListenerComponent parse(String id, ConfigurationSection sec, boolean contextDependent) {
        String identifier = sec.getString("identifier", sec.getString("id", id));
        ActionListenerComponent listener = new ActionListenerComponent(id, identifier);
        ConfigurationSection actionSec = sec.getConfigurationSection("on-trigger");
        if (actionSec == null) actionSec = sec.getConfigurationSection("on-action");
        if (actionSec != null) {
            for (String k : actionSec.getKeys(false)) {
                listener.addAction(k, actionSec.get(k));
            }
        }
        return listener;
    }
}
