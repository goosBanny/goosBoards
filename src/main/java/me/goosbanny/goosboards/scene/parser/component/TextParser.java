package me.goosbanny.goosboards.scene.parser.component;

import me.goosbanny.goosboards.render.palette.ColorUtils;
import me.goosbanny.goosboards.scene.component.text.TextComponent;
import org.bukkit.configuration.ConfigurationSection;

public class TextParser implements ComponentParser<TextComponent> {

    @Override
    public String getType() {
        return "text";
    }

    @Override
    public TextComponent parse(String id, ConfigurationSection sec, boolean contextDependent) {
        TextComponent txt = new TextComponent(id, contextDependent);
        txt.setText(sec.getString("text", ""));
        txt.setFallbackText(sec.getString("fallback", ""));
        txt.setFont(sec.getString("font", "SansSerif"));
        txt.setFontSize(sec.getInt("font-size", sec.getInt("size", sec.getInt("font_size", 18))));
        txt.setOutlineColor(ColorUtils.parseColor(sec.getString("outline-color", sec.getString("outline")), 0x00000000));
        txt.setOutlineStroke(sec.getDouble("outline-stroke", sec.getDouble("outline-width", sec.getDouble("outline-thickness", 0.0))));
        txt.setOnHoverOutlineColor(ColorUtils.parseColor(sec.getString("on-hover-outline-color", sec.getString("hover-outline-color")), 0));
        txt.setOnHoverOutlineWidth(sec.getDouble("on-hover-outline-width", sec.getDouble("hover-outline-width", 0.0)));
        return txt;
    }
}
