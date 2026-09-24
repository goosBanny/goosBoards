package me.goosbanny.goosboards.storage.impl;

import me.goosbanny.goosboards.GoosBoards;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.io.File;

/**
 * Embedded H2 database storage backend requiring zero external database configuration.
 */
public class H2StorageBackend extends AbstractJdbcStorageBackend {

    public H2StorageBackend(File dataFolder, String tablePrefix) {
        super(createDataSource(dataFolder), tablePrefix);
    }

    private static HikariDataSource createDataSource(File dataFolder) {
        File storageDir = new File(dataFolder, "storage");
        if (!storageDir.exists()) {
            storageDir.mkdirs();
        }

        File dbFile = new File(storageDir, "data");
        HikariConfig config = new HikariConfig();
        config.setDriverClassName("org.h2.Driver");
        config.setJdbcUrl("jdbc:h2:file:" + dbFile.getAbsolutePath().replace('\\', '/') + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        config.setPoolName("GoosBoards-H2-Pool");
        config.setMaximumPoolSize(5);
        config.setMinimumIdle(1);
        config.setConnectionTimeout(5000);
        return new HikariDataSource(config);
    }

    @Override
    protected String getPlayerSceneUpsertSql() {
        return "MERGE INTO " + tablePrefix + "player_scenes (player_uuid, board_id, scene_id, updated_at) " +
                "KEY (player_uuid, board_id) VALUES (?, ?, ?, ?)";
    }
}
