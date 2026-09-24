package me.goosbanny.goosboards.scene.parser.component;

import me.goosbanny.goosboards.render.palette.ColorUtils;
import me.goosbanny.goosboards.scene.component.text.TextComponent;
import org.bukkit.configuration.ConfigurationSection;

public class AutoFontSizeTextParser implements ComponentParser<TextComponent> {

    @Override
    public String getType() {
        return "auto_font_size_text";
    }

    @Override
    public TextComponent parse(String id, ConfigurationSection sec, boolean contextDependent) {
        TextComponent autoTxt = new TextComponent(id, contextDependent);
        autoTxt.setText(sec.getString("text", ""));
        autoTxt.setFallbackText(sec.getString("fallback", ""));
        autoTxt.setFont(sec.getString("font", "SansSerif"));
        autoTxt.setFontSize(0); // 0 signals auto-fit to the renderer
        autoTxt.setOutlineColor(ColorUtils.parseColor(sec.getString("outline-color"), 0x00000000));
        autoTxt.setOutlineStroke(sec.getDouble("outline-stroke", 0.0));
        return autoTxt;
    }
}
