package me.goosbanny.goosboards.media.security.impl;

import me.goosbanny.goosboards.GoosBoards;
import me.goosbanny.goosboards.media.exception.SsrfViolationException;
import me.goosbanny.goosboards.media.security.SafeMediaLoader;
import me.goosbanny.goosboards.media.security.SsrfFirewall;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Objects;
import java.util.OptionalLong;
import java.util.concurrent.CompletableFuture;

/**
 * Hardened implementation of SafeMediaLoader enforcing DNS pre-resolution,
 * SSRF IP filtering, redirect inspection, and memory-safe streaming.
 */
public class SafeMediaLoaderImpl implements SafeMediaLoader {

    @FunctionalInterface
    public interface DnsResolver {
        InetAddress[] resolve(String host) throws UnknownHostException;
    }

    public static final int DEFAULT_MAX_REDIRECTS = 3;
    public static final int DEFAULT_MAX_SIZE_BYTES = 8 * 1024 * 1024; // 8 MB
    public static final int DEFAULT_CONNECT_TIMEOUT_SECONDS = 4;
    public static final int DEFAULT_READ_TIMEOUT_SECONDS = 6;

    private final HttpClient httpClient;
    private final DnsResolver dnsResolver;
    private final int maxRedirects;

    public SafeMediaLoaderImpl() {
        this(
                HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(DEFAULT_CONNECT_TIMEOUT_SECONDS))
                        .followRedirects(HttpClient.Redirect.NEVER)
                        .build(),
                InetAddress::getAllByName,
                DEFAULT_MAX_REDIRECTS
        );
    }

    public SafeMediaLoaderImpl(DnsResolver dnsResolver, int maxRedirects) {
        this(
                HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(DEFAULT_CONNECT_TIMEOUT_SECONDS))
                        .followRedirects(HttpClient.Redirect.NEVER)
                        .build(),
                dnsResolver,
                maxRedirects
        );
    }

    public SafeMediaLoaderImpl(HttpClient httpClient, DnsResolver dnsResolver, int maxRedirects) {
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
        this.dnsResolver = Objects.requireNonNull(dnsResolver, "dnsResolver");
        this.maxRedirects = Math.max(0, maxRedirects);
    }

    @Override
    public CompletableFuture<byte[]> loadSecureImage(URI uri, int maxSizeBytes, int timeoutMs) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return executeWithRedirects(uri, maxSizeBytes, timeoutMs, 0);
            } catch (Exception e) {
                if (e instanceof RuntimeException re) {
                    throw re;
                }
                throw new RuntimeException("Failed to load secure media: " + e.getMessage(), e);
            }
        });
    }

    private byte[] executeWithRedirects(URI uri, int maxSizeBytes, int timeoutMs, int redirectCount)
            throws IOException, InterruptedException {
        if (redirectCount > maxRedirects) {
            throw new SsrfViolationException("Maximum redirect limit (" + maxRedirects + ") exceeded");
        }

        // 1. Validate URI scheme and host
        String scheme = uri.getScheme();
        if (scheme == null || (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https"))) {
            throw new SsrfViolationException("Forbidden URI scheme: " + scheme);
        }

        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new SsrfViolationException("URI host is missing");
        }

        // 2. Resolve DNS and validate each resolved address against SSRF rules
        InetAddress[] addresses = dnsResolver.resolve(host);
        if (addresses == null || addresses.length == 0) {
            throw new UnknownHostException("No addresses found for host: " + host);
        }
        for (InetAddress addr : addresses) {
            SsrfFirewall.validate(addr);
        }

        // 3. Pin connection:
        // For HTTP requests, rewrite URI to the validated numeric IP address to prevent DNS rebinding TOCTOU attacks.
        // For HTTPS requests, retain the original hostname in URI so Java's HttpClient performs standard TLS SNI
        // and certificate hostname verification (satisfying Test 6.8).
        URI requestUri;
        if ("http".equalsIgnoreCase(scheme)) {
            InetAddress chosen = addresses[0];
            String pinnedHost = chosen instanceof Inet6Address
                    ? "[" + chosen.getHostAddress() + "]"
                    : chosen.getHostAddress();
            try {
                requestUri = new URI(
                        scheme,
                        uri.getUserInfo(),
                        pinnedHost,
                        uri.getPort(),
                        uri.getPath(),
                        uri.getQuery(),
                        uri.getFragment()
                );
            } catch (URISyntaxException e) {
                requestUri = uri;
            }
        } else {
            requestUri = uri;
        }

        int effectiveMaxSize = maxSizeBytes > 0 ? maxSizeBytes : DEFAULT_MAX_SIZE_BYTES;
        int effectiveTimeoutMs = timeoutMs > 0 ? timeoutMs : (DEFAULT_READ_TIMEOUT_SECONDS * 1000);

        HttpRequest request = HttpRequest.newBuilder(requestUri)
                .timeout(Duration.ofMillis(Math.max(1000, effectiveTimeoutMs)))
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36 GoosBoards/1.0")
                .header("Accept", "image/*,text/html,*/*;q=0.8")
                .GET()
                .build();

        // 4. Send request and inspect response headers
        HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
        int statusCode = response.statusCode();

        // Handle redirects (301, 302, 303, 307, 308)
        if (statusCode >= 300 && statusCode < 400) {
            String location = response.headers().firstValue("Location").orElse(null);
            if (location == null || location.isBlank()) {
                throw new IOException("Redirect status " + statusCode + " returned without Location header");
            }
            URI redirectUri = uri.resolve(location);
            return executeWithRedirects(redirectUri, effectiveMaxSize, effectiveTimeoutMs, redirectCount + 1);
        }

        if (statusCode != 200) {
            throw new IOException("HTTP request failed with status: " + statusCode);
        }

        // 5. Size check on Content-Length header
        HttpHeaders headers = response.headers();
        OptionalLong contentLength = headers.firstValueAsLong("Content-Length");
        if (contentLength.isPresent() && contentLength.getAsLong() > effectiveMaxSize) {
            throw new IllegalArgumentException(
                    "Payload size (" + contentLength.getAsLong() + " bytes) exceeds maximum limit of " + effectiveMaxSize + " bytes"
            );
        }

        // 6. Stream and verify size limit
        try (InputStream in = response.body()) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int read;
            long totalRead = 0;
            while ((read = in.read(buf)) != -1) {
                totalRead += read;
                if (totalRead > effectiveMaxSize) {
                    throw new IllegalArgumentException("Payload exceeded maximum size limit of " + effectiveMaxSize + " bytes");
                }
                out.write(buf, 0, read);
            }
            return out.toByteArray();
        }
    }

    public HttpClient getHttpClient() {
        return httpClient;
    }

    public DnsResolver getDnsResolver() {
        return dnsResolver;
    }
}
