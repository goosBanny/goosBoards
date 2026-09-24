package me.goosbanny.goosboards.scene.parser.component;

import me.goosbanny.goosboards.render.palette.ColorUtils;
import me.goosbanny.goosboards.scene.component.interactive.ButtonComponent;
import org.bukkit.configuration.ConfigurationSection;

public class ButtonParser implements ComponentParser<ButtonComponent> {

    @Override
    public String getType() {
        return "button";
    }

    @Override
    public ButtonComponent parse(String id, ConfigurationSection sec, boolean contextDependent) {
        ButtonComponent btn = new ButtonComponent(id, contextDependent);
        btn.setShape(sec.getString("shape", "rectangle"));
        btn.setColor(ColorUtils.parseColor(sec.getString("color"), 0xFF2575FC));
        btn.setCornerRadius(sec.getInt("corner-radius", sec.getInt("radius", 0)));
        btn.setOutlineColor(ColorUtils.parseColor(sec.getString("outline-color"), 0x00000000));
        btn.setOutlineWidth(sec.getDouble("outline-width", 0.0));
        btn.setOnHoverColor(ColorUtils.parseColor(sec.getString("on-hover-color", sec.getString("hover-color")), btn.getColor()));
        btn.setOnHoverOutlineColor(ColorUtils.parseColor(sec.getString("on-hover-outline-color", sec.getString("hover-outline-color")), btn.getOutlineColor()));
        btn.setOnHoverOutlineWidth(sec.getDouble("on-hover-outline-width", sec.getDouble("hover-outline-width", btn.getOutlineWidth())));
        btn.setGlow(sec.getBoolean("glow", false));
        btn.setGlowColor(ColorUtils.parseColor(sec.getString("glow-color"), btn.getOutlineColor() != 0 ? btn.getOutlineColor() : 0xFF38BDF8));
        btn.setGlowRadius(sec.getInt("glow-radius", 3));
        btn.setHoverGlow(sec.getBoolean("hover-glow", sec.getBoolean("glow-on-hover", false)));
        btn.setHoverGlowColor(ColorUtils.parseColor(sec.getString("hover-glow-color"), btn.getOnHoverOutlineColor() != 0 ? btn.getOnHoverOutlineColor() : btn.getGlowColor()));

        btn.setImageName(sec.getString("image", ""));
        btn.setHoverImageName(sec.getString("hover-image", sec.getString("on-hover-image", "")));

        btn.setText(sec.getString("text", ""));
        btn.setFont(sec.getString("font", "SansSerif"));
        btn.setFontSize(sec.getInt("font-size", sec.getInt("size", sec.getInt("font_size", 18))));
        btn.setTextColor(ColorUtils.parseColor(sec.getString("text-color"), 0xFFFFFFFF));

        ConfigurationSection onClickSec = sec.getConfigurationSection("on-click");
        if (onClickSec != null) {
            for (String k : onClickSec.getKeys(false)) {
                btn.addOnClickAction(k, onClickSec.get(k));
            }
        }
        ConfigurationSection onHoverSec = sec.getConfigurationSection("on-hover");
        if (onHoverSec != null) {
            for (String k : onHoverSec.getKeys(false)) {
                btn.addOnHoverAction(k, onHoverSec.get(k));
            }
        }
        return btn;
    }
}
