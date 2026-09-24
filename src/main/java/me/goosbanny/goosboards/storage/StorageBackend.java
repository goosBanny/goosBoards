package me.goosbanny.goosboards.storage;

import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;

/**
 * Common storage backend abstraction supporting embedded (H2) and relational (MySQL, MariaDB) databases.
 */
public interface StorageBackend extends AutoCloseable {

    /**
     * Initializes database tables and schema migrations.
     */
    void init() throws SQLException;

    /**
     * Persists a player's active scene for a given board.
     */
    void savePlayerScene(UUID playerId, String boardId, String sceneId) throws SQLException;

    /**
     * Loads a player's active scene for a given board if previously persisted.
     */
    Optional<String> getPlayerScene(UUID playerId, String boardId) throws SQLException;

    /**
     * Persists an atomic transaction receipt with expiration timestamp.
     */
    void recordTransaction(String idempotencyKey, UUID playerId, double amount, long expiryMs) throws SQLException;

    /**
     * Checks whether an unexpired transaction with the given idempotency key exists.
     */
    boolean hasTransaction(String idempotencyKey, long nowMs) throws SQLException;

    /**
     * Purges expired idempotency transaction records.
     */
    void purgeExpiredTransactions(long nowMs) throws SQLException;

    @Override
    void close();
}
