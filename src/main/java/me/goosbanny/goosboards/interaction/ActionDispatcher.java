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

    public static void execute(
            Player player,
            String sourceId,
            String currentSceneId,
            String actionKey,
            Object rawAction,
            EconomyGuard economyGuard,
            BiConsumer<Player, String> sceneSwitchHandler
    ) {
        execute(player, null, sourceId, currentSceneId, actionKey, rawAction, economyGuard,
                (p, bId, target) -> {
                    if (sceneSwitchHandler != null) sceneSwitchHandler.accept(p, target);
                });
    }

    public static void execute(
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
            return;
        }

        double price = getDoubleField(rawAction, "price", 0.0);
        boolean charged = false;
        if (price > 0.0 && economyGuard != null) {
            String idempotencyKey = player.getUniqueId() + ":" + sourceId + ":" + (System.currentTimeMillis() / 1000L);
            ChargeResult chargeResult = economyGuard.tryCharge(player.getUniqueId(), price, idempotencyKey);
            if (!chargeResult.success()) {
                player.sendMessage("§cTransaction failed: " + chargeResult.receiptOrReason());
                return;
            }
            charged = true;
        }

        try {
            executeActionBody(player, boardId, sourceId, currentSceneId, actionKey, rawAction, sceneSwitchListener, charged, price, economyGuard);
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
}