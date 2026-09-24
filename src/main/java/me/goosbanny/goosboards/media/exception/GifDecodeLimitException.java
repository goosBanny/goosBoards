package me.goosbanny.goosboards.media.exception;

/**
 * Thrown when an animated GIF exceeds permitted frame count or dimension limits,
 * preventing decompression bombs and out-of-memory exploits.
 */
public class GifDecodeLimitException extends RuntimeException {

    public GifDecodeLimitException(String message) {
        super(message);
    }

    public GifDecodeLimitException(String message, Throwable cause) {
        super(message, cause);
    }
}
