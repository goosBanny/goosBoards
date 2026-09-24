package me.goosbanny.goosboards.scene.parser.component;

import me.goosbanny.goosboards.render.palette.ColorUtils;
import me.goosbanny.goosboards.scene.component.visual.GifComponent;
import org.bukkit.configuration.ConfigurationSection;

public class GifParser implements ComponentParser<GifComponent> {

    @Override
    public String getType() {
        return "gif";
    }

    @Override
    public GifComponent parse(String id, ConfigurationSection sec, boolean contextDependent) {
        GifComponent gif = new GifComponent(id);
        if (sec.isConfigurationSection("image")) {
            gif.setImageName(sec.getString("image.name", ""));
        } else {
            gif.setImageName(sec.getString("image", sec.getString("gif", "")));
        }
        gif.setCacheBehavior(sec.getString("cache-behavior", "global"));
        gif.setLoop(sec.getBoolean("loop", true));
        gif.setFps(sec.getDouble("fps", 0.0));
        gif.setFrameDelayMs(sec.getInt("frame-delay-ms", sec.getInt("delay-ms", 0)));
        gif.setSpeedMultiplier(sec.getDouble("speed", sec.getDouble("speed-multiplier", 1.0)));

        gif.setOutlineColor(ColorUtils.parseColor(sec.getString("outline-color", sec.getString("outline")), 0x00000000));
        gif.setOutlineWidth(sec.getDouble("outline-width", sec.getDouble("outline-thickness", 0.0)));
        gif.setOnHoverOutlineColor(ColorUtils.parseColor(sec.getString("on-hover-outline-color", sec.getString("hover-outline-color")), 0));
        gif.setOnHoverOutlineWidth(sec.getDouble("on-hover-outline-width", sec.getDouble("hover-outline-width", 0.0)));
        return gif;
    }
}
