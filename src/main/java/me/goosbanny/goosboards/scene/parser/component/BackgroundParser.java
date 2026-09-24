package me.goosbanny.goosboards.scene.parser.component;

import me.goosbanny.goosboards.render.palette.ColorUtils;
import me.goosbanny.goosboards.scene.component.container.BackgroundComponent;
import org.bukkit.configuration.ConfigurationSection;

public class BackgroundParser implements ComponentParser<BackgroundComponent> {

    @Override
    public String getType() {
        return "background";
    }

    @Override
    public BackgroundComponent parse(String id, ConfigurationSection sec, boolean contextDependent) {
        BackgroundComponent bg = new BackgroundComponent(id, contextDependent);
        bg.setShape(sec.getString("shape", "rectangle"));
        bg.setColor(ColorUtils.parseColor(sec.getString("color"), 0xFF202020));
        bg.setCornerRadius(sec.getInt("corner-radius", sec.getInt("radius", 0)));
        bg.setOutlineColor(ColorUtils.parseColor(sec.getString("outline-color"), 0x00000000));
        bg.setOutlineWidth(sec.getDouble("outline-width", 0.0));
        bg.setGlow(sec.getBoolean("glow", false));
        bg.setGlowColor(ColorUtils.parseColor(sec.getString("glow-color"), bg.getOutlineColor() != 0 ? bg.getOutlineColor() : 0xFF38BDF8));
        bg.setGlowRadius(sec.getInt("glow-radius", 3));
        bg.setOnHoverColor(ColorUtils.parseColor(sec.getString("on-hover-color", sec.getString("hover-color")), 0));
        bg.setOnHoverOutlineColor(ColorUtils.parseColor(sec.getString("on-hover-outline-color", sec.getString("hover-outline-color")), 0));
        bg.setOnHoverOutlineWidth(sec.getDouble("on-hover-outline-width", sec.getDouble("hover-outline-width", 0.0)));
        bg.setHoverGlow(sec.getBoolean("hover-glow", sec.getBoolean("glow-on-hover", false)));
        bg.setHoverGlowColor(ColorUtils.parseColor(sec.getString("hover-glow-color"), bg.getOnHoverOutlineColor() != 0 ? bg.getOnHoverOutlineColor() : bg.getGlowColor()));
        return bg;
    }
}
