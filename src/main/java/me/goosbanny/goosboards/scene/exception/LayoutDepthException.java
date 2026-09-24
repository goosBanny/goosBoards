package me.goosbanny.goosboards.scene.exception;

/**
 * Thrown when the scene hierarchy exceeds the maximum supported recursion depth (16).
 */
public class LayoutDepthException extends RuntimeException {
    public LayoutDepthException(String message) {
        super(message);
    }
}
