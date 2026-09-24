package me.goosbanny.goosboards.interaction;

import me.goosbanny.goosboards.interaction.action.ActionHandler;
import me.goosbanny.goosboards.interaction.action.ActionRegistry;
import me.goosbanny.goosboards.core.logging.DebugLogger;
import me.goosbanny.goosboards.interaction.economy.ChargeResult;
import me.goosbanny.goosboards.interaction.economy.EconomyGuard;

import me.goosbanny.goosboards.GoosBoards;
import me.goosbanny.goosboards.api.event.BoardSceneChangeEvent;
import me.goosbanny.goosboards.integration.PlaceholderHook;
import me.goosbanny.goosboards.core.logging.MessageService;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.function.BiConsumer;

/**
 * Centralized executor for button actions, timers, and external action listeners.
 */
public final class ActionDispatcher {

    private ActionDispatcher() {
    }

    public static boolean execute(
            Player player,
            String sourceId,
            String currentSceneId,
            String actionKey,
            Object rawAction,
            EconomyGuard economyGuard,
            BiConsumer<Player, String> sceneSwitchHandler
    ) {
        return execute(player, null, sourceId, currentSceneId, actionKey, rawAction, economyGuard,
                (p, bId, target) -> {
                    if (sceneSwitchHandler != null) sceneSwitchHandler.accept(p, target);
                });
    }

    public static boolean execute(
            Player player,
            String boardId,
            String sourceId,
            String currentSceneId,
            String actionKey,
            Object rawAction,
            EconomyGuard economyGuard,
            InteractionRouter.SceneSwitchListener sceneSwitchListener
    ) {
        if (player == null || rawAction == null) {
            return false;
        }

        double price = getDoubleField(rawAction, "price", 0.0);
        boolean charged = false;
        if (price > 0.0 && economyGuard != null) {
            String idempotencyKey = player.getUniqueId() + ":" + sourceId + ":" + (System.currentTimeMillis() / 1000L);
            ChargeResult chargeResult = economyGuard.tryCharge(player.getUniqueId(), price, idempotencyKey);
            if (!chargeResult.success()) {
                player.sendMessage("§cTransaction failed: " + chargeResult.receiptOrReason());
                return false;
            }
            charged = true;
        }

        try {
            executeActionBody(player, boardId, sourceId, currentSceneId, actionKey, rawAction, sceneSwitchListener, charged, price, economyGuard);
            return true;
        } catch (Throwable t) {
            if (charged && economyGuard != null) {
                economyGuard.refund(player.getUniqueId(), price);
            }
            throw t;
        }
    }

    private static final ActionRegistry REGISTRY = new ActionRegistry();

    public static ActionRegistry getRegistry() {
        return REGISTRY;
    }

    private static void executeActionBody(
            Player player,
            String boardId,
            String sourceId,
            String currentSceneId,
            String actionKey,
            Object rawAction,
            InteractionRouter.SceneSwitchListener sceneSwitchListener,
            boolean charged,
            double price,
            EconomyGuard economyGuard
    ) {
        String type = getStringField(rawAction, "type", "");
        ActionHandler handler = REGISTRY.get(type);
        if (handler != null) {
            handler.execute(player, boardId, sourceId, currentSceneId, actionKey, rawAction, sceneSwitchListener, charged, price, economyGuard);
        }
    }


    public static String getStringField(Object obj, String key, String def) {
        if (obj instanceof ConfigurationSection sec) {
            return sec.getString(key, def);
        } else if (obj instanceof Map<?, ?> map) {
            Object val = map.get(key);
            return val != null ? String.valueOf(val) : def;
        }
        return def;
    }

    public static double getDoubleField(Object obj, String key, double def) {
        if (obj instanceof ConfigurationSection sec) {
            return sec.getDouble(key, def);
        } else if (obj instanceof Map<?, ?> map) {
            Object val = map.get(key);
            if (val instanceof Number num) return num.doubleValue();
            try {
                return val != null ? Double.parseDouble(String.valueOf(val)) : def;
            } catch (NumberFormatException ignored) {
                return def;
            }
        }
        return def;
    }

    public static boolean getBooleanField(Object obj, String key, boolean def) {
        if (obj instanceof ConfigurationSection sec) {
            return sec.getBoolean(key, def);
        } else if (obj instanceof Map<?, ?> map) {
            Object val = map.get(key);
            if (val instanceof Boolean b) return b;
            return val != null ? Boolean.parseBoolean(String.valueOf(val)) : def;
        }
        return def;
    }

    public static java.util.List<String> getStringListField(Object obj, String... keys) {
        for (String key : keys) {
            java.util.List<String> list = extractStringList(obj, key);
            if (!list.isEmpty()) {
                return list;
            }
        }
        return java.util.Collections.emptyList();
    }

    private static java.util.List<String> extractStringList(Object obj, String key) {
        if (obj instanceof ConfigurationSection sec) {
            if (sec.isList(key)) {
                return sec.getStringList(key);
            }
            String single = sec.getString(key);
            if (single != null && !single.isBlank()) {
                return java.util.List.of(single);
            }
        } else if (obj instanceof Map<?, ?> map) {
            Object val = map.get(key);
            if (val instanceof java.util.List<?> list) {
                java.util.List<String> result = new java.util.ArrayList<>(list.size());
                for (Object item : list) {
                    if (item != null) {
                        String s = String.valueOf(item).trim();
                        if (!s.isEmpty()) {
                            result.add(s);
                        }
                    }
                }
                return result;
            } else if (val != null) {
                String single = String.valueOf(val).trim();
                if (!single.isEmpty()) {
                    return java.util.List.of(single);
                }
            }
        }
        return java.util.Collections.emptyList();
    }
}