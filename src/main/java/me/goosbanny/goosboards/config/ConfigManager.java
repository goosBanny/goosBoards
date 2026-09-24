package me.goosbanny.goosboards.config;

import com.tchristofferson.configupdater.ConfigUpdater;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.Objects;
import java.util.logging.Level;

/**
 * Manages configuration lifecycle, bundled resources, and non-destructive auto-updating.
 */
public class ConfigManager {

    private final JavaPlugin plugin;
    private final File dataFolder;
    private final File configFile;
    private final File boardsFolder;
    private final File imagesFolder;

    private FileConfiguration config;

    public ConfigManager(JavaPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.dataFolder = plugin.getDataFolder();
        this.configFile = new File(dataFolder, "config.yml");
        this.boardsFolder = new File(dataFolder, "boards");
        this.imagesFolder = new File(dataFolder, "images");
    }

    public void init() {
        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }
        if (!boardsFolder.exists()) {
            boardsFolder.mkdirs();
        }
        if (!imagesFolder.exists()) {
            imagesFolder.mkdirs();
        }

        saveDefaultFiles();
        updateAndReloadConfig();
    }

    private void saveDefaultFiles() {
        if (!configFile.exists()) {
            plugin.saveResource("config.yml", false);
        }
    }

    public void updateAndReloadConfig() {
        try {
            if (configFile.exists() && plugin.getResource("config.yml") != null) {
                ConfigUpdater.update(plugin, "config.yml", configFile, Collections.emptyList());
            }
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to auto-update config.yml non-destructively: " + e.getMessage(), e);
        }

        this.config = YamlConfiguration.loadConfiguration(configFile);
    }

    public FileConfiguration getConfig() {
        return config != null ? config : YamlConfiguration.loadConfiguration(configFile);
    }

    public File getConfigFile() {
        return configFile;
    }

    public File getBoardsFolder() {
        return boardsFolder;
    }

    public File getImagesFolder() {
        return imagesFolder;
    }
}
