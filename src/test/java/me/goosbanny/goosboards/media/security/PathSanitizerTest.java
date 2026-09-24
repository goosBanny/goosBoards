package me.goosbanny.goosboards.media.security;

import me.goosbanny.goosboards.media.exception.PathTraversalException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class PathSanitizerTest {

    @TempDir
    Path tempImagesRoot;

    @Test
    @DisplayName("Test 6.9: Path traversal - directory escape rejected")
    void testDirectoryEscapeRejected() {
        Path escapePath = Path.of("../../../etc/passwd");
        assertThrows(PathTraversalException.class, () ->
                PathSanitizer.validateLocalPath(escapePath, tempImagesRoot)
        );

        Path sneakyEscape = Path.of("sub/../../outside.png");
        assertThrows(PathTraversalException.class, () ->
                PathSanitizer.validateLocalPath(sneakyEscape, tempImagesRoot)
        );
    }

    @Test
    @DisplayName("Test 6.10: Path traversal - valid path accepted")
    void testValidPathAccepted() throws IOException {
        Path validFile = tempImagesRoot.resolve("logo.png");
        Files.createFile(validFile);

        Path validated = assertDoesNotThrow(() ->
                PathSanitizer.validateLocalPath(Path.of("logo.png"), tempImagesRoot)
        );

        assertEquals(validFile.toRealPath(), validated);
    }

    @Test
    @DisplayName("Nested child directory path accepted")
    void testNestedChildDirectoryAccepted() throws IOException {
        Path subDir = Files.createDirectory(tempImagesRoot.resolve("icons"));
        Path subFile = Files.createFile(subDir.resolve("heart.png"));

        Path validated = assertDoesNotThrow(() ->
                PathSanitizer.validateLocalPath(Path.of("icons/heart.png"), tempImagesRoot)
        );

        assertEquals(subFile.toRealPath(), validated);
    }

    @Test
    @DisplayName("Path pointing to images root itself is rejected")
    void testRootItselfRejected() {
        assertThrows(PathTraversalException.class, () ->
                PathSanitizer.validateLocalPath(Path.of("."), tempImagesRoot)
        );
    }
}
