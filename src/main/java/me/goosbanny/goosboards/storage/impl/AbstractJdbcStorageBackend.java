package me.goosbanny.goosboards.storage.impl;

import me.goosbanny.goosboards.storage.StorageBackend;

import com.zaxxer.hikari.HikariDataSource;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Base JDBC-backed storage implementation using HikariCP connection pooling.
 */
public abstract class AbstractJdbcStorageBackend implements StorageBackend {

    protected final HikariDataSource dataSource;
    protected final String tablePrefix;

    protected AbstractJdbcStorageBackend(HikariDataSource dataSource, String tablePrefix) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        if (tablePrefix != null) {
            String cleaned = tablePrefix.replaceAll("[^a-zA-Z0-9_]", "");
            this.tablePrefix = cleaned.substring(0, Math.min(cleaned.length(), 32));
        } else {
            this.tablePrefix = "";
        }
    }

    @Override
    public void init() throws SQLException {
        try (Connection conn = dataSource.getConnection()) {
            createTables(conn);
        }
    }

    protected void createTables(Connection conn) throws SQLException {
        String createScenesTable = "CREATE TABLE IF NOT EXISTS " + tablePrefix + "player_scenes (" +
                "player_uuid VARCHAR(36) NOT NULL, " +
                "board_id VARCHAR(64) NOT NULL, " +
                "scene_id VARCHAR(64) NOT NULL, " +
                "updated_at BIGINT NOT NULL, " +
                "PRIMARY KEY (player_uuid, board_id)" +
                ");";

        String createIdempotencyTable = "CREATE TABLE IF NOT EXISTS " + tablePrefix + "idempotency_transactions (" +
                "idempotency_key VARCHAR(128) NOT NULL PRIMARY KEY, " +
                "player_uuid VARCHAR(36) NOT NULL, " +
                "amount DOUBLE NOT NULL, " +
                "created_at BIGINT NOT NULL, " +
                "expires_at BIGINT NOT NULL" +
                ");";

        try (PreparedStatement stmt1 = conn.prepareStatement(createScenesTable);
             PreparedStatement stmt2 = conn.prepareStatement(createIdempotencyTable)) {
            stmt1.execute();
            stmt2.execute();
        }
    }

    protected abstract String getPlayerSceneUpsertSql();

    @Override
    public void savePlayerScene(UUID playerId, String boardId, String sceneId) throws SQLException {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(boardId, "boardId");
        Objects.requireNonNull(sceneId, "sceneId");

        String sql = getPlayerSceneUpsertSql();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, playerId.toString());
            stmt.setString(2, boardId);
            stmt.setString(3, sceneId);
            stmt.setLong(4, System.currentTimeMillis());
            stmt.executeUpdate();
        }
    }

    @Override
    public Optional<String> getPlayerScene(UUID playerId, String boardId) throws SQLException {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(boardId, "boardId");

        String sql = "SELECT scene_id FROM " + tablePrefix + "player_scenes WHERE player_uuid = ? AND board_id = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, playerId.toString());
            stmt.setString(2, boardId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(rs.getString("scene_id"));
                }
            }
        }
        return Optional.empty();
    }

    @Override
    public void recordTransaction(String idempotencyKey, UUID playerId, double amount, long expiryMs) throws SQLException {
        Objects.requireNonNull(idempotencyKey, "idempotencyKey");
        Objects.requireNonNull(playerId, "playerId");

        String sql = "INSERT INTO " + tablePrefix + "idempotency_transactions " +
                "(idempotency_key, player_uuid, amount, created_at, expires_at) VALUES (?, ?, ?, ?, ?)";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, idempotencyKey);
            stmt.setString(2, playerId.toString());
            stmt.setDouble(3, amount);
            stmt.setLong(4, System.currentTimeMillis());
            stmt.setLong(5, expiryMs);
            stmt.executeUpdate();
        }
    }

    @Override
    public boolean hasTransaction(String idempotencyKey, long nowMs) throws SQLException {
        Objects.requireNonNull(idempotencyKey, "idempotencyKey");

        String sql = "SELECT 1 FROM " + tablePrefix + "idempotency_transactions WHERE idempotency_key = ? AND expires_at > ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, idempotencyKey);
            stmt.setLong(2, nowMs);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        }
    }

    @Override
    public void purgeExpiredTransactions(long nowMs) throws SQLException {
        String sql = "DELETE FROM " + tablePrefix + "idempotency_transactions WHERE expires_at <= ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, nowMs);
            stmt.executeUpdate();
        }
    }

    @Override
    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }
}
