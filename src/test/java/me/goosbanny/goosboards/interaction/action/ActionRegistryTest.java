package me.goosbanny.goosboards.interaction.action;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ActionRegistryTest {

    @Test
    @DisplayName("ActionRegistry contains default action handlers")
    void testActionRegistryDefaults() {
        ActionRegistry registry = new ActionRegistry();

        assertNotNull(registry.get("command"));
        assertNotNull(registry.get("switch_scene"));
        assertNotNull(registry.get("play_sound"));
        assertNotNull(registry.get("send_message"));
        assertNull(registry.get("nonexistent_action"));
        assertNull(registry.get(null));
    }
}
