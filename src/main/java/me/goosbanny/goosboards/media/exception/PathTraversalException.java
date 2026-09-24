package me.goosbanny.goosboards.media.exception;

/**
 * Thrown when a file path contains illegal path traversal sequences escaping the base directory.
 */
public class PathTraversalException extends RuntimeException {

    public PathTraversalException(String message) {
        super(message);
    }

    public PathTraversalException(String message, Throwable cause) {
        super(message, cause);
    }
}
