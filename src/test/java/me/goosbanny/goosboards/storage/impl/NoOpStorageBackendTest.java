package me.goosbanny.goosboards.storage.impl;

import me.goosbanny.goosboards.storage.StorageManager;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NoOpStorageBackendTest {

    @Test
    @DisplayName("NoOpStorageBackend executes zero I/O and returns empty states without error")
    void testNoOpStorageOperations() throws SQLException {
        NoOpStorageBackend backend = new NoOpStorageBackend();
        assertDoesNotThrow(backend::init);

        UUID playerId = UUID.randomUUID();
        String boardId = "test-board";

        // Must return empty optional
        Optional<String> scene = backend.getPlayerScene(playerId, boardId);
        assertTrue(scene.isEmpty(), "NoOp backend must return empty scene");

        // Save operations must complete silently without exception
        assertDoesNotThrow(() -> backend.savePlayerScene(playerId, boardId, "gallery"));

        // Value must still be empty (zero storage)
        assertTrue(backend.getPlayerScene(playerId, boardId).isEmpty());

        // Close must complete cleanly
        assertDoesNotThrow(backend::close);
    }

    @Test
    @DisplayName("StorageManager with NoOp backend completes asynchronous futures instantly")
    void testStorageManagerWithNoOpBackend() throws Exception {
        NoOpStorageBackend backend = new NoOpStorageBackend();
        StorageManager storage = new StorageManager(backend, Logger.getLogger("NoOpTest"));

        UUID playerId = UUID.randomUUID();
        String boardId = "showcase";

        // Async get returns empty
        Optional<String> result = storage.getPlayerSceneAsync(playerId, boardId).get();
        assertTrue(result.isEmpty());

        // Async save returns completed future
        assertDoesNotThrow(() -> storage.savePlayerSceneAsync(playerId, boardId, "ranks").get());

        storage.close();
    }
}
