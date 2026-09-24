package me.goosbanny.goosboards.scene.parser.component;

import me.goosbanny.goosboards.scene.component.visual.Head2DComponent;
import org.bukkit.configuration.ConfigurationSection;

public class Head2DParser implements ComponentParser<Head2DComponent> {

    @Override
    public String getType() {
        return "head_2d";
    }

    @Override
    public Head2DComponent parse(String id, ConfigurationSection sec, boolean contextDependent) {
        Head2DComponent head = new Head2DComponent(id, contextDependent);
        head.setSkin(sec.getString("skin", sec.getString("player", "%player_name%")));
        head.setFallbackSkin(sec.getString("fallback", sec.getString("loading-skin", "Steve")));
        return head;
    }
}
