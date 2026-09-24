package me.goosbanny.goosboards.scene.parser.component;

import me.goosbanny.goosboards.render.palette.ColorUtils;
import me.goosbanny.goosboards.scene.component.visual.ImageComponent;
import org.bukkit.configuration.ConfigurationSection;

public class ImageParser implements ComponentParser<ImageComponent> {

    @Override
    public String getType() {
        return "image";
    }

    @Override
    public ImageComponent parse(String id, ConfigurationSection sec, boolean contextDependent) {
        ImageComponent img = new ImageComponent(id, contextDependent);
        if (sec.isConfigurationSection("image")) {
            img.setImageName(sec.getString("image.name", ""));
        } else {
            img.setImageName(sec.getString("image", ""));
        }
        img.setHoverImageName(sec.getString("hover-image", sec.getString("on-hover-image", "")));
        img.setCacheBehavior(sec.getString("cache-behavior", "global"));
        img.setFallback(sec.getString("fallback", ""));

        img.setOutlineColor(ColorUtils.parseColor(sec.getString("outline-color", sec.getString("outline")), 0x00000000));
        img.setOutlineWidth(sec.getDouble("outline-width", sec.getDouble("outline-thickness", 0.0)));
        img.setOnHoverOutlineColor(ColorUtils.parseColor(sec.getString("on-hover-outline-color", sec.getString("hover-outline-color")), 0));
        img.setOnHoverOutlineWidth(sec.getDouble("on-hover-outline-width", sec.getDouble("hover-outline-width", 0.0)));
        return img;
    }
}
