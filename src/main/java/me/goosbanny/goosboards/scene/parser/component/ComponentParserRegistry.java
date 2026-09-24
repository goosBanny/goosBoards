package me.goosbanny.goosboards.scene.parser.component;

import me.goosbanny.goosboards.scene.component.UIComponent;
import org.bukkit.configuration.ConfigurationSection;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry holding all active ComponentParser strategy instances for extensible scene parsing.
 */
public class ComponentParserRegistry {

    private final Map<String, ComponentParser<?>> parsers = new ConcurrentHashMap<>();

    public ComponentParserRegistry() {
        registerDefaults();
    }

    public void registerDefaults() {
        register(new BackgroundParser());
        register(new ButtonParser());
        register(new PixelTextParser());
        register(new ImageParser());
        register(new TextParser());
        register(new ScrollPaneParser());
        register(new Head2DParser());
        register(new AutoFontSizeTextParser());
        register(new GifParser());
        register(new DelayedTimerParser());
        register(new ActionListenerParser());
    }

    public void register(ComponentParser<?> parser) {
        parsers.put(parser.getType().toLowerCase(Locale.ROOT), parser);
    }

    public UIComponent parse(String type, String id, ConfigurationSection sec, boolean contextDependent) {
        if (type == null) return null;
        ComponentParser<?> parser = parsers.get(type.toLowerCase(Locale.ROOT));
        if (parser != null) {
            return parser.parse(id, sec, contextDependent);
        }
        return null;
    }
}
