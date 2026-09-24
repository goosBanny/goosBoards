package me.goosbanny.goosboards.scene.parser.component;

import me.goosbanny.goosboards.render.palette.ColorUtils;
import me.goosbanny.goosboards.scene.component.text.PixelTextComponent;
import org.bukkit.configuration.ConfigurationSection;

public class PixelTextParser implements ComponentParser<PixelTextComponent> {

    @Override
    public String getType() {
        return "pixel_text";
    }

    @Override
    public PixelTextComponent parse(String id, ConfigurationSection sec, boolean contextDependent) {
        PixelTextComponent pt = new PixelTextComponent(id, contextDependent);
        pt.setText(sec.getString("text", ""));
        pt.setFallbackText(sec.getString("fallback", ""));
        pt.setColor(ColorUtils.parseColor(sec.getString("color"), 0xFFFFFFFF));
        int scale = sec.getInt("font-scale", sec.getInt("scale", sec.getInt("font_scale", 1)));
        int fontSize = sec.getInt("font-size", sec.getInt("size", sec.getInt("font_size", 0)));
        pt.setFontScale(scale);
        pt.setFontSize(fontSize);
        pt.setShadow(sec.getBoolean("shadow", false));
        return pt;
    }
}
