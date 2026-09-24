package me.goosbanny.goosboards.scene.parser.component;

import me.goosbanny.goosboards.scene.component.UIComponent;
import org.bukkit.configuration.ConfigurationSection;

/**
 * Strategy interface for parsing a specific UIComponent type from a YAML ConfigurationSection.
 *
 * @param <T> the concrete UIComponent type parsed
 */
public interface ComponentParser<T extends UIComponent> {

    /**
     * The type identifier string in the YAML definition (e.g. "button", "text").
     */
    String getType();

    /**
     * Parses the YAML configuration section into the corresponding UIComponent.
     *
     * @param id               the component id
     * @param sec              the YAML configuration section for this component
     * @param contextDependent whether placeholders or dynamic conditions were detected
     * @return the initialized UIComponent
     */
    T parse(String id, ConfigurationSection sec, boolean contextDependent);
}
