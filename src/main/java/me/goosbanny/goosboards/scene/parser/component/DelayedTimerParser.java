package me.goosbanny.goosboards.scene.parser.component;

import me.goosbanny.goosboards.scene.component.misc.DelayedTimerComponent;
import org.bukkit.configuration.ConfigurationSection;

public class DelayedTimerParser implements ComponentParser<DelayedTimerComponent> {

    @Override
    public String getType() {
        return "delayed_function_timer";
    }

    @Override
    public DelayedTimerComponent parse(String id, ConfigurationSection sec, boolean contextDependent) {
        DelayedTimerComponent timer = new DelayedTimerComponent(id);
        long delayTicks = sec.getLong("delay", sec.getLong("delay-ticks", 20L));
        long delayMs = sec.contains("delay-ms")
                ? sec.getLong("delay-ms")
                : delayTicks * 50L;
        timer.setDelayMs(delayMs);
        ConfigurationSection funcSec = sec.getConfigurationSection("functions");
        if (funcSec == null) funcSec = sec.getConfigurationSection("on-trigger");
        if (funcSec != null) {
            for (String k : funcSec.getKeys(false)) {
                timer.addFunction(k, funcSec.get(k));
            }
        }
        return timer;
    }
}
