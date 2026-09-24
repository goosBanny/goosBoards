package me.goosbanny.goosboards.interaction.economy.impl;

import me.goosbanny.goosboards.interaction.economy.ChargeResult;
import me.goosbanny.goosboards.interaction.economy.EconomyGuard;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.google.common.util.concurrent.Striped;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.locks.Lock;
import java.util.function.Function;
import java.util.function.LongSupplier;

/**
 * Vault-backed implementation of EconomyGuard with striped lock concurrency control
 * and idempotency tracking to prevent duplicate charges.
 */
public class VaultEconomyGuard implements EconomyGuard {

    private final Economy economy;
    private final long idempotencyWindowMs;
    private final LongSupplier timeSupplier;
    private final Function<UUID, OfflinePlayer> playerResolver;

    private final Striped<Lock> stripes = Striped.lock(64);
    private final Cache<String, Long> idempotencyLog;

    public VaultEconomyGuard(Economy economy) {
        this(economy, 1000L, System::currentTimeMillis, Bukkit::getOfflinePlayer);
    }

    public VaultEconomyGuard(Economy economy, long idempotencyWindowMs) {
        this(economy, idempotencyWindowMs, System::currentTimeMillis, Bukkit::getOfflinePlayer);
    }

    public VaultEconomyGuard(Economy economy, long idempotencyWindowMs, LongSupplier timeSupplier) {
        this(economy, idempotencyWindowMs, timeSupplier, Bukkit::getOfflinePlayer);
    }

    public VaultEconomyGuard(
            Economy economy,
            long idempotencyWindowMs,
            LongSupplier timeSupplier,
            Function<UUID, OfflinePlayer> playerResolver
    ) {
        this.economy = economy;
        this.idempotencyWindowMs = Math.max(0L, idempotencyWindowMs);
        this.timeSupplier = Objects.requireNonNull(timeSupplier, "timeSupplier");
        this.playerResolver = Objects.requireNonNull(playerResolver, "playerResolver");
        this.idempotencyLog = Caffeine.newBuilder()
                .maximumSize(10_000)
                .build();
    }

    @Override
    public ChargeResult tryCharge(UUID playerId, double amount, String idempotencyKey) {
        if (playerId == null) {
            return new ChargeResult(false, "invalid player");
        }

        final long now = timeSupplier.getAsLong();

        // Fast-path duplicate check
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            Long expiry = idempotencyLog.getIfPresent(idempotencyKey);
            if (expiry != null && expiry > now) {
                return new ChargeResult(false, "duplicate transaction");
            }
        }

        Lock lock = stripes.get(playerId);
        lock.lock();
        try {
            // Authoritative duplicate check inside lock
            final long lockNow = timeSupplier.getAsLong();
            if (idempotencyKey != null && !idempotencyKey.isBlank()) {
                Long expiry = idempotencyLog.getIfPresent(idempotencyKey);
                if (expiry != null && expiry > lockNow) {
                    return new ChargeResult(false, "duplicate transaction");
                }
            }

            if (economy == null) {
                return new ChargeResult(false, "economy unavailable");
            }

            if (amount <= 0.0) {
                return new ChargeResult(true, UUID.randomUUID().toString());
            }

            OfflinePlayer offlinePlayer = playerResolver.apply(playerId);
            if (!economy.has(offlinePlayer, amount)) {
                return new ChargeResult(false, "insufficient funds");
            }

            EconomyResponse response = economy.withdrawPlayer(offlinePlayer, amount);
            if (response == null || !response.transactionSuccess()) {
                String error = (response != null && response.errorMessage != null && !response.errorMessage.isBlank())
                        ? response.errorMessage
                        : "transaction failed";
                return new ChargeResult(false, error);
            }

            if (idempotencyKey != null && !idempotencyKey.isBlank() && idempotencyWindowMs > 0) {
                idempotencyLog.put(idempotencyKey, lockNow + idempotencyWindowMs);
            }

            return new ChargeResult(true, UUID.randomUUID().toString());
        } finally {
            lock.unlock();
        }
    }

    @Override
    public boolean refund(UUID playerId, double amount) {
        if (economy == null || playerId == null || amount <= 0.0) {
            return false;
        }
        Lock lock = stripes.get(playerId);
        lock.lock();
        try {
            OfflinePlayer offlinePlayer = playerResolver.apply(playerId);
            EconomyResponse response = economy.depositPlayer(offlinePlayer, amount);
            return response != null && response.transactionSuccess();
        } catch (Throwable t) {
            return false;
        } finally {
            lock.unlock();
        }
    }

    public void clearIdempotencyLog() {
        idempotencyLog.invalidateAll();
    }
}
