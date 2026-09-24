package me.goosbanny.goosboards.media.security;

import java.net.URI;
import java.util.concurrent.CompletableFuture;

/**
 * Interface for securely fetching external media with anti-SSRF protection,
 * memory guards, and timeout limits per PROJECT.md §5.
 */
public interface SafeMediaLoader {

    /**
     * Asynchronously loads media from a URI, enforcing SSRF validation, size limits,
     * and redirect checks.
     *
     * @param uri          the media URI (must use http or https)
     * @param maxSizeBytes the maximum permitted payload size in bytes
     * @param timeoutMs    network timeout in milliseconds
     * @return CompletableFuture completing with the downloaded bytes, or exceptionally on violation
     */
    CompletableFuture<byte[]> loadSecureImage(URI uri, int maxSizeBytes, int timeoutMs);
}
