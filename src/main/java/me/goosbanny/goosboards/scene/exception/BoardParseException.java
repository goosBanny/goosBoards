package me.goosbanny.goosboards.scene.exception;

/**
 * Thrown when a board YAML configuration contains invalid syntax or structure.
 */
public class BoardParseException extends RuntimeException {
    public BoardParseException(String message) {
        super(message);
    }

    public BoardParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
