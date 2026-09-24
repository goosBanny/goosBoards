package me.goosbanny.goosboards.storage;

import me.goosbanny.goosboards.GoosBoards;
import me.goosbanny.goosboards.storage.impl.H2StorageBackend;
import me.goosbanny.goosboards.storage.impl.MySqlStorageBackend;
import me.goosbanny.goosboards.storage.impl.NoOpStorageBackend;

import org.bukkit.configuration.ConfigurationSection;

import java.io.File;
import java.sql.SQLException;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * High-level storage manager orchestrating backend lifecycle and off-thread asynchronous queries.
 */
public class StorageManager implements AutoCloseable {

    private final StorageBackend backend;
    private final ExecutorService dbExecutor;
    private final Logger logger;
    private final CompletableFuture<Void> initFuture;

    public StorageManager(StorageBackend backend, Logger logger) {
        this.backend = Objects.requireNonNull(backend, "backend");
        this.logger = logger != null ? logger : Logger.getLogger("GoosBoards");
        this.dbExecutor = Executors.newFixedThreadPool(2, r -> {
            Thread t = new Thread(r, "GoosBoards-DB-Worker");
            t.setDaemon(true);
            return t;
        });
        this.initFuture = CompletableFuture.runAsync(() -> {
            try {
                backend.init();
            } catch (SQLException e) {
                this.logger.log(Level.SEVERE, "Failed to initialize storage backend asynchronously", e);
            }
        }, dbExecutor);
    }

    public CompletableFuture<Void> initAsync() {
        return initFuture;
    }

    /**
     * Factory creating a StorageManager from plugin configuration.
     */
    public static StorageManager fromConfig(File dataFolder, ConfigurationSection dbConfig, Logger logger) throws SQLException {
        boolean enabled = dbConfig != null && dbConfig.getBoolean("enabled", false);
        String typeStr = dbConfig != null ? dbConfig.getString("type", "H2").toUpperCase() : "H2";

        if (!enabled || "NONE".equals(typeStr) || "DISABLED".equals(typeStr)) {
            return new StorageManager(new NoOpStorageBackend(), logger);
        }

        String prefix = dbConfig != null ? dbConfig.getString("table-prefix", "goosboards_") : "goosboards_";

        StorageBackend backend;
        switch (typeStr) {
            case "MYSQL", "MARIADB" -> {
                boolean isMaria = "MARIADB".equals(typeStr);
                String host = dbConfig.getString("host", "localhost");
                int port = dbConfig.getInt("port", 3306);
                String database = dbConfig.getString("database", "goosboards");
                String username = dbConfig.getString("username", "root");
                String password = dbConfig.getString("password", "");
                int maxPool = dbConfig.getInt("pool.maximum-pool-size", 10);
                int minIdle = dbConfig.getInt("pool.minimum-idle", 2);
                long connTimeout = dbConfig.getLong("pool.connection-timeout-ms", 10000L);

                backend = new MySqlStorageBackend(host, port, database, username, password, prefix, maxPool, minIdle, connTimeout, isMaria);
            }
            default -> backend = new H2StorageBackend(dataFolder, prefix);
        }

        return new StorageManager(backend, logger);
    }

    public StorageBackend getBackend() {
        return backend;
    }

    public CompletableFuture<Void> savePlayerSceneAsync(UUID playerId, String boardId, String sceneId) {
        return initFuture.thenRunAsync(() -> {
            try {
                backend.savePlayerScene(playerId, boardId, sceneId);
            } catch (SQLException e) {
                logger.log(Level.SEVERE, "Failed to persist scene for player " + playerId + " on board " + boardId, e);
            }
        }, dbExecutor);
    }

    public CompletableFuture<Optional<String>> getPlayerSceneAsync(UUID playerId, String boardId) {
        return initFuture.thenApplyAsync(v -> {
            try {
                return backend.getPlayerScene(playerId, boardId);
            } catch (SQLException e) {
                logger.log(Level.SEVERE, "Failed to retrieve scene for player " + playerId + " on board " + boardId, e);
                return Optional.empty();
            }
        }, dbExecutor);
    }

    public CompletableFuture<Void> recordTransactionAsync(String idempotencyKey, UUID playerId, double amount, long expiryMs) {
        return initFuture.thenRunAsync(() -> {
            try {
                backend.recordTransaction(idempotencyKey, playerId, amount, expiryMs);
            } catch (SQLException e) {
                logger.log(Level.SEVERE, "Failed to record idempotency transaction: " + idempotencyKey, e);
            }
        }, dbExecutor);
    }

    public CompletableFuture<Boolean> hasTransactionAsync(String idempotencyKey, long nowMs) {
        return initFuture.thenApplyAsync(v -> {
            try {
                return backend.hasTransaction(idempotencyKey, nowMs);
            } catch (SQLException e) {
                logger.log(Level.SEVERE, "Failed to query idempotency transaction: " + idempotencyKey, e);
                return false;
            }
        }, dbExecutor);
    }

    public CompletableFuture<Void> purgeExpiredTransactionsAsync(long nowMs) {
        return initFuture.thenRunAsync(() -> {
            try {
                backend.purgeExpiredTransactions(nowMs);
            } catch (SQLException e) {
                logger.log(Level.WARNING, "Failed to purge expired idempotency transactions", e);
            }
        }, dbExecutor);
    }

    @Override
    public void close() {
        dbExecutor.shutdown();
        try {
            if (!dbExecutor.awaitTermination(3, TimeUnit.SECONDS)) {
                dbExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            dbExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        backend.close();
    }
}
