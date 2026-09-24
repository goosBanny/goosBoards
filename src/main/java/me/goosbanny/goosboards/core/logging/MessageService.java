package me.goosbanny.goosboards.core.logging;

import me.goosbanny.goosboards.GoosBoards;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.tchristofferson.configupdater.ConfigUpdater;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Production localization and message service supporting non-destructive config updates,
 * dual MiniMessage & Legacy color support, and high-performance Caffeine Component caching.
 */
public class MessageService {

    private static final Pattern HEX_PATTERN_1 = Pattern.compile("&#([0-9a-fA-F]{6})");
    private static final Pattern HEX_PATTERN_2 = Pattern.compile("&x(&[0-9a-fA-F]){6}");
    private static final Pattern LEGACY_PATTERN = Pattern.compile("&([0-9a-fk-orA-FK-OR])");

    private final Plugin plugin;
    private final File messagesFile;
    private final Logger logger;
    private final MiniMessage miniMessage;
    private final Cache<String, Component> componentCache;

    private FileConfiguration config;
    private String prefix = "";

    public MessageService(Plugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.messagesFile = new File(plugin.getDataFolder(), "messages.yml");
        this.logger = plugin.getLogger() != null ? plugin.getLogger() : Logger.getLogger("GoosBoards");
        this.miniMessage = MiniMessage.miniMessage();
        this.componentCache = Caffeine.newBuilder()
                .maximumSize(500)
                .expireAfterAccess(Duration.ofMinutes(15))
                .build();
    }

    public void init() {
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }

        if (!messagesFile.exists() && plugin.getResource("messages.yml") != null) {
            plugin.saveResource("messages.yml", false);
        }

        updateAndReload();
    }

    public void updateAndReload() {
        try {
            if (messagesFile.exists() && plugin.getResource("messages.yml") != null) {
                ConfigUpdater.update(plugin, "messages.yml", messagesFile, Collections.emptyList());
            }
        } catch (IOException e) {
            logger.log(Level.WARNING, "Failed to auto-update messages.yml non-destructively: " + e.getMessage(), e);
        }

        this.config = YamlConfiguration.loadConfiguration(messagesFile);
        this.prefix = config.getString("prefix", "");
        this.componentCache.invalidateAll();
    }

    public Component get(String key) {
        return get(key, Collections.emptyMap());
    }

    public Component get(String key, Map<String, String> placeholders) {
        String raw = config != null ? config.getString(key) : null;
        if (raw == null) {
            raw = "<red>Missing message key: " + key + "</red>";
        }

        // Apply prefix if template references {prefix}
        String resolved = raw.replace("{prefix}", prefix);

        // Apply dynamic placeholders
        if (placeholders != null && !placeholders.isEmpty()) {
            for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                resolved = resolved.replace("{" + entry.getKey() + "}", entry.getValue() != null ? entry.getValue() : "");
            }
        }

        // Convert legacy color codes to MiniMessage tags
        final String formatted = convertLegacyToMiniMessage(resolved);

        return componentCache.get(formatted, miniMessage::deserialize);
    }

    public void send(CommandSender sender, String key) {
        send(sender, key, Collections.emptyMap());
    }

    public void send(CommandSender sender, String key, Map<String, String> placeholders) {
        if (sender != null) {
            sender.sendMessage(get(key, placeholders));
        }
    }

    public void sendActionBar(Player player, String key) {
        sendActionBar(player, key, Collections.emptyMap());
    }

    public void sendActionBar(Player player, String key, Map<String, String> placeholders) {
        if (player != null && player.isOnline()) {
            player.sendActionBar(get(key, placeholders));
        }
    }

    /**
     * Converts legacy Bukkit color codes (&a, &l) and hex codes (&#RRGGBB) to native MiniMessage tags.
     */
    public static String convertLegacyToMiniMessage(String message) {
        if (message == null || message.isEmpty()) {
            return "";
        }

        String result = message;

        // 1. Convert &#RRGGBB to <#RRGGBB>
        Matcher hexMatcher1 = HEX_PATTERN_1.matcher(result);
        if (hexMatcher1.find()) {
            result = hexMatcher1.replaceAll("<#$1>");
        }

        // 2. Convert &x&r&r&g&g&b&b to <#rrggbb>
        Matcher hexMatcher2 = HEX_PATTERN_2.matcher(result);
        if (hexMatcher2.find()) {
            StringBuilder sb = new StringBuilder();
            hexMatcher2.reset();
            while (hexMatcher2.find()) {
                String matched = hexMatcher2.group();
                String hex = matched.replace("&", "").substring(1);
                hexMatcher2.appendReplacement(sb, "<#" + hex + ">");
            }
            hexMatcher2.appendTail(sb);
            result = sb.toString();
        }

        // 3. Convert standard &0 - &f, &k - &r
        Matcher legacyMatcher = LEGACY_PATTERN.matcher(result);
        if (legacyMatcher.find()) {
            StringBuilder sb = new StringBuilder();
            legacyMatcher.reset();
            while (legacyMatcher.find()) {
                char code = Character.toLowerCase(legacyMatcher.group(1).charAt(0));
                String tag = switch (code) {
                    case '0' -> "<black>";
                    case '1' -> "<dark_blue>";
                    case '2' -> "<dark_green>";
                    case '3' -> "<dark_aqua>";
                    case '4' -> "<dark_red>";
                    case '5' -> "<dark_purple>";
                    case '6' -> "<gold>";
                    case '7' -> "<gray>";
                    case '8' -> "<dark_gray>";
                    case '9' -> "<blue>";
                    case 'a' -> "<green>";
                    case 'b' -> "<aqua>";
                    case 'c' -> "<red>";
                    case 'd' -> "<light_purple>";
                    case 'e' -> "<yellow>";
                    case 'f' -> "<white>";
                    case 'k' -> "<obfuscated>";
                    case 'l' -> "<bold>";
                    case 'm' -> "<strikethrough>";
                    case 'n' -> "<underlined>";
                    case 'o' -> "<italic>";
                    case 'r' -> "<reset>";
                    default -> legacyMatcher.group();
                };
                legacyMatcher.appendReplacement(sb, Matcher.quoteReplacement(tag));
            }
            legacyMatcher.appendTail(sb);
            result = sb.toString();
        }

        return result;
    }

    public FileConfiguration getConfig() {
        return config;
    }
}
