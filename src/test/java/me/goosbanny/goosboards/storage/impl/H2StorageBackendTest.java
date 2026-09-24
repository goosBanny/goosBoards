package me.goosbanny.goosboards.storage.impl;

import me.goosbanny.goosboards.storage.StorageManager;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

class H2StorageBackendTest {

    @TempDir
    File tempDir;

    private H2StorageBackend backend;
    private StorageManager storageManager;

    @BeforeEach
    void setUp() throws SQLException {
        backend = new H2StorageBackend(tempDir, "test_");
        backend.init();
        storageManager = new StorageManager(backend, Logger.getLogger("H2Test"));
    }

    @AfterEach
    void tearDown() {
        if (storageManager != null) {
            storageManager.close();
        }
    }

    @Test
    @DisplayName("H2 backend successfully persists, retrieves, and upserts player scenes")
    void testPlayerScenePersistenceAndUpsert() throws SQLException {
        UUID playerId = UUID.randomUUID();
        String boardId = "spawn-board";

        // Initial check: empty
        Optional<String> initial = backend.getPlayerScene(playerId, boardId);
        assertTrue(initial.isEmpty());

        // First save
        backend.savePlayerScene(playerId, boardId, "scene-alpha");
        Optional<String> afterSave = backend.getPlayerScene(playerId, boardId);
        assertTrue(afterSave.isPresent());
        assertEquals("scene-alpha", afterSave.get());

        // Upsert / overwrite with new scene
        backend.savePlayerScene(playerId, boardId, "scene-beta");
        Optional<String> afterUpsert = backend.getPlayerScene(playerId, boardId);
        assertTrue(afterUpsert.isPresent());
        assertEquals("scene-beta", afterUpsert.get());
    }

    @Test
    @DisplayName("Idempotency transactions support unexpired lookup and expiration purging")
    void testIdempotencyTransactionsAndPurge() throws SQLException {
        UUID playerId = UUID.randomUUID();
        String txnKey = "txn-12345";
        long now = System.currentTimeMillis();
        long expiry = now + 5000L; // expires in 5s

        assertFalse(backend.hasTransaction(txnKey, now));

        backend.recordTransaction(txnKey, playerId, 150.0, expiry);
        assertTrue(backend.hasTransaction(txnKey, now));

        // When queried with timestamp after expiry
        assertFalse(backend.hasTransaction(txnKey, expiry + 1000L));

        // Purge expired
        backend.purgeExpiredTransactions(expiry + 1000L);
        assertFalse(backend.hasTransaction(txnKey, now));
    }

    @Test
    @DisplayName("StorageManager executes queries asynchronously without blocking")
    void testAsyncStorageManager() throws ExecutionException, InterruptedException {
        UUID playerId = UUID.randomUUID();
        String boardId = "hub-board";

        storageManager.savePlayerSceneAsync(playerId, boardId, "shop-scene").get();

        Optional<String> result = storageManager.getPlayerSceneAsync(playerId, boardId).get();
        assertTrue(result.isPresent());
        assertEquals("shop-scene", result.get());

        String key = "async-key-999";
        long now = System.currentTimeMillis();
        storageManager.recordTransactionAsync(key, playerId, 50.0, now + 10000L).get();

        Boolean hasTxn = storageManager.hasTransactionAsync(key, now).get();
        assertTrue(hasTxn);
    }
}
