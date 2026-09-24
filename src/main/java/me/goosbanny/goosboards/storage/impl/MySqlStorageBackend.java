package me.goosbanny.goosboards.storage.impl;

import me.goosbanny.goosboards.GoosBoards;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

/**
 * MySQL and MariaDB storage backend using HikariCP connection pooling.
 */
public class MySqlStorageBackend extends AbstractJdbcStorageBackend {

    public MySqlStorageBackend(
            String host,
            int port,
            String database,
            String username,
            String password,
            String tablePrefix,
            int maxPoolSize,
            int minIdle,
            long connectionTimeoutMs,
            boolean isMariaDb
    ) {
        super(createDataSource(host, port, database, username, password, maxPoolSize, minIdle, connectionTimeoutMs, isMariaDb), tablePrefix);
    }

    private static HikariDataSource createDataSource(
            String host,
            int port,
            String database,
            String username,
            String password,
            int maxPoolSize,
            int minIdle,
            long connectionTimeoutMs,
            boolean isMariaDb
    ) {
        HikariConfig config = new HikariConfig();
        String protocol = isMariaDb ? "mariadb" : "mysql";
        String jdbcUrl = String.format("jdbc:%s://%s:%d/%s?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC&characterEncoding=utf8",
                protocol, host, port, database);

        config.setJdbcUrl(jdbcUrl);
        config.setUsername(username);
        config.setPassword(password);
        config.setPoolName(isMariaDb ? "GoosBoards-MariaDB-Pool" : "GoosBoards-MySQL-Pool");
        config.setMaximumPoolSize(Math.max(1, maxPoolSize));
        config.setMinimumIdle(Math.max(1, minIdle));
        config.setConnectionTimeout(connectionTimeoutMs > 0 ? connectionTimeoutMs : 10000);
        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "250");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        return new HikariDataSource(config);
    }

    @Override
    protected String getPlayerSceneUpsertSql() {
        return "INSERT INTO " + tablePrefix + "player_scenes (player_uuid, board_id, scene_id, updated_at) " +
                "VALUES (?, ?, ?, ?) ON DUPLICATE KEY UPDATE scene_id = VALUES(scene_id), updated_at = VALUES(updated_at)";
    }
}
