package me.goosbanny.goosboards.scene.parser.component;

import me.goosbanny.goosboards.scene.component.container.ScrollPaneComponent;
import org.bukkit.configuration.ConfigurationSection;

public class ScrollPaneParser implements ComponentParser<ScrollPaneComponent> {

    @Override
    public String getType() {
        return "scroll_pane";
    }

    @Override
    public ScrollPaneComponent parse(String id, ConfigurationSection sec, boolean contextDependent) {
        ScrollPaneComponent sp = new ScrollPaneComponent(id, contextDependent);
        String innerSize = sec.getString("scroll-inner-size", "");
        if (innerSize != null && !innerSize.isBlank()) {
            String[] parts = innerSize.trim().split("\\s+");
            if (parts.length >= 2) {
                try {
                    sp.setScrollInnerWidth(Integer.parseInt(parts[0]));
                    sp.setScrollInnerHeight(Integer.parseInt(parts[1]));
                } catch (NumberFormatException ignored) {
                }
            }
        }
        sp.setMouseScroll(sec.getBoolean("mouse-scroll", true));
        sp.setMouseScrollVertical(sec.getBoolean("mouse-scroll-vertical", true));
        sp.setMouseScrollAmount(sec.getInt("mouse-scroll-amount", 32));
        sp.setScrollBehavior(sec.getString("scroll-behavior", "snap"));
        return sp;
    }
}
