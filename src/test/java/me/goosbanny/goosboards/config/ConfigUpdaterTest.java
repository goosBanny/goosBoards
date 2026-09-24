package me.goosbanny.goosboards.config;

import com.tchristofferson.configupdater.ConfigUpdater;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ConfigUpdaterTest {

    @Test
    @DisplayName("ConfigUpdater non-destructively preserves user modifications and comments while adding new keys")
    void testNonDestructiveConfigUpdate() throws IOException {
        File testDir = new File("build/tmp/test-config");
        testDir.mkdirs();
        File configFile = new File(testDir, "config.yml");

        // Updated resource in plugin jar with new keys and documentation comments
        String newDefaultResource = """
                # Global configuration header
                config-version: 2
                
                # Concurrency settings
                threading:
                  raycast-pool-size: 4
                
                # New database configuration
                database:
                  type: H2
                  table-prefix: "goosboards_"
                
                # Interaction limits
                interaction:
                  rate-limiting:
                    max-clicks-per-second: 8
                    max-hover-packets-per-second: 20
                """;

        // User's existing modified config on disk with custom values
        String userExistingConfig = """
                config-version: 1
                
                threading:
                  raycast-pool-size: 8
                
                interaction:
                  rate-limiting:
                    max-clicks-per-second: 16
                    max-hover-packets-per-second: 40
                """;

        Files.writeString(configFile.toPath(), userExistingConfig, StandardCharsets.UTF_8);

        Plugin mockPlugin = mock(Plugin.class);
        when(mockPlugin.getResource("config.yml")).thenAnswer(
                inv -> new ByteArrayInputStream(newDefaultResource.getBytes(StandardCharsets.UTF_8))
        );

        // Run ConfigUpdater
        ConfigUpdater.update(mockPlugin, "config.yml", configFile, Collections.emptyList());

        // Verify updated file on disk
        String updatedContent = Files.readString(configFile.toPath(), StandardCharsets.UTF_8);

        // 1. User's custom values MUST be preserved
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(configFile);
        assertEquals(8, yaml.getInt("threading.raycast-pool-size"), "User's customized pool size must be preserved");
        assertEquals(16, yaml.getInt("interaction.rate-limiting.max-clicks-per-second"), "User's custom click limit must be preserved");
        assertEquals(40, yaml.getInt("interaction.rate-limiting.max-hover-packets-per-second"), "User's custom hover limit must be preserved");

        // 2. Newly introduced keys MUST be added
        assertEquals("H2", yaml.getString("database.type"), "New database.type key must be added");
        assertEquals("goosboards_", yaml.getString("database.table-prefix"), "New database.table-prefix key must be added");

        // 3. Documentation comments from jar template MUST be present in updated config
        assertTrue(updatedContent.contains("# Global configuration header"), "Header comments must be present");
        assertTrue(updatedContent.contains("# Concurrency settings"), "Section comments must be present");
        assertTrue(updatedContent.contains("# New database configuration"), "New section comments must be present");
        assertTrue(updatedContent.contains("# Interaction limits"), "Interaction comments must be present");
    }
}
