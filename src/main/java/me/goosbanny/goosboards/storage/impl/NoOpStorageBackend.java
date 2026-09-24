package me.goosbanny.goosboards.storage.impl;

import me.goosbanny.goosboards.storage.StorageBackend;

import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;

/**
 * Lightweight, zero-overhead storage backend used when database persistence is disabled.
 * Spawns zero background threads, opens zero files, and consumes zero database resources.
 */
public class NoOpStorageBackend implements StorageBackend {

    @Override
    public void init() throws SQLException {
        // No-op
    }

    @Override
    public void savePlayerScene(UUID playerId, String boardId, String sceneId) throws SQLException {
        // No-op
    }

    @Override
    public Optional<String> getPlayerScene(UUID playerId, String boardId) throws SQLException {
        return Optional.empty();
    }

    @Override
    public void recordTransaction(String idempotencyKey, UUID playerId, double amount, long expiryMs) throws SQLException {
        // No-op
    }

    @Override
    public boolean hasTransaction(String idempotencyKey, long nowMs) throws SQLException {
        return false;
    }

    @Override
    public void purgeExpiredTransactions(long nowMs) throws SQLException {
        // No-op
    }

    @Override
    public void close() {
        // No-op
    }
}
