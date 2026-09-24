package me.goosbanny.goosboards.scene.parser;

import me.goosbanny.goosboards.scene.component.UIComponent;
import me.goosbanny.goosboards.scene.component.container.BackgroundComponent;
import me.goosbanny.goosboards.scene.component.interactive.ButtonComponent;
import me.goosbanny.goosboards.scene.parser.component.ComponentParserRegistry;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ComponentParserRegistryTest {

    @Test
    @DisplayName("ComponentParserRegistry registers all default component types")
    void testDefaultRegistryTypes() {
        ComponentParserRegistry registry = new ComponentParserRegistry();

        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("bg.type", "background");
        yaml.set("bg.color", "#FF0000");

        UIComponent bgComp = registry.parse("background", "bg", yaml.getConfigurationSection("bg"), false);
        assertNotNull(bgComp);
        assertInstanceOf(BackgroundComponent.class, bgComp);

        yaml.set("btn.type", "button");
        yaml.set("btn.color", "#00FF00");

        UIComponent btnComp = registry.parse("button", "btn", yaml.getConfigurationSection("btn"), false);
        assertNotNull(btnComp);
        assertInstanceOf(ButtonComponent.class, btnComp);
    }

    @Test
    @DisplayName("Unknown component type returns null")
    void testUnknownTypeReturnsNull() {
        ComponentParserRegistry registry = new ComponentParserRegistry();
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("unknown.type", "nonexistent_type");

        assertNull(registry.parse("nonexistent_type", "unknown", yaml.getConfigurationSection("unknown"), false));
        assertNull(registry.parse(null, "unknown", yaml.getConfigurationSection("unknown"), false));
    }
}
