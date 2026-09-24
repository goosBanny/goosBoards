package me.goosbanny.goosboards.media.security;

import me.goosbanny.goosboards.media.exception.PathTraversalException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Utility sanitizing and validating local file paths to prevent directory traversal exploits.
 */
public final class PathSanitizer {

    private PathSanitizer() {}

    /**
     * Validates that the requested path resides strictly within the imagesRoot directory.
     *
     * @param requestedPath the relative or user-provided path
     * @param imagesRoot    the allowed base directory
     * @return the normalized absolute Path
     * @throws PathTraversalException if the path attempts to escape imagesRoot
     */
    public static Path validateLocalPath(Path requestedPath, Path imagesRoot) throws PathTraversalException {
        if (requestedPath == null) {
            throw new PathTraversalException("Requested path is null");
        }
        if (imagesRoot == null) {
            throw new PathTraversalException("Images root directory is null");
        }

        try {
            Path rootNorm = Files.exists(imagesRoot)
                    ? imagesRoot.toRealPath()
                    : imagesRoot.toAbsolutePath().normalize();

            Path resolved = rootNorm.resolve(requestedPath).normalize();

            // RealPath check if resolved file exists
            if (Files.exists(resolved)) {
                resolved = resolved.toRealPath();
            }

            if (!resolved.startsWith(rootNorm) || resolved.equals(rootNorm)) {
                throw new PathTraversalException("Path traversal attempt detected: " + requestedPath);
            }

            return resolved;
        } catch (IOException e) {
            throw new PathTraversalException("Failed to canonicalize path: " + requestedPath, e);
        }
    }
}
