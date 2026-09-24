package me.goosbanny.goosboards.media.exception;

/**
 * Thrown when an outbound media or API request targets a forbidden IP address or range.
 */
public class SsrfViolationException extends RuntimeException {

    public SsrfViolationException(String message) {
        super(message);
    }

    public SsrfViolationException(String message, Throwable cause) {
        super(message, cause);
    }
}
