package me.goosbanny.goosboards.media.security;

import me.goosbanny.goosboards.media.exception.SsrfViolationException;
import me.goosbanny.goosboards.media.security.impl.SafeMediaLoaderImpl;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SafeMediaLoaderTest {

    @Test
    @DisplayName("Test 6.6: SSRF - redirect-to-private rejected and final URL never fetched")
    void testRedirectToPrivateRejected() throws Exception {
        HttpClient mockClient = mock(HttpClient.class);
        SafeMediaLoaderImpl.DnsResolver mockDns = host -> {
            if ("example.com".equals(host)) {
                return new InetAddress[]{InetAddress.getByName("93.184.216.34")}; // Public
            } else if ("10.0.0.1".equals(host)) {
                return new InetAddress[]{InetAddress.getByName("10.0.0.1")}; // Private RFC1918
            }
            return new InetAddress[]{InetAddress.getByName("8.8.8.8")};
        };

        // First response redirects to http://10.0.0.1/secret
        HttpResponse<InputStream> redirectResponse = mock(HttpResponse.class);
        when(redirectResponse.statusCode()).thenReturn(302);
        HttpHeaders redirectHeaders = HttpHeaders.of(
                Map.of("Location", List.of("http://10.0.0.1/secret")),
                (k, v) -> true
        );
        when(redirectResponse.headers()).thenReturn(redirectHeaders);

        when(mockClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(redirectResponse);

        SafeMediaLoader loader = new SafeMediaLoaderImpl(mockClient, mockDns, 3);
        CompletableFuture<byte[]> future = loader.loadSecureImage(
                URI.create("https://example.com/redirect"),
                1024 * 1024,
                5000
        );

        ExecutionException ex = assertThrows(ExecutionException.class, future::get);
        assertTrue(ex.getCause() instanceof SsrfViolationException,
                "Expected cause to be SsrfViolationException, but was: " + ex.getCause());
        assertTrue(ex.getCause().getMessage().contains("10.0.0.1")
                || ex.getCause().getMessage().contains("RFC1918"));

        // Verify the client was called only ONCE (the redirect target was NEVER requested)
        verify(mockClient, times(1)).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    }

    @Test
    @DisplayName("Test 6.8: TLS hostname verification - original hostname used for SNI")
    void testTlsOriginalHostnamePreserved() throws Exception {
        HttpClient mockClient = mock(HttpClient.class);
        SafeMediaLoaderImpl.DnsResolver mockDns = host -> new InetAddress[]{InetAddress.getByName("93.184.216.34")};

        HttpResponse<InputStream> mockResponse = mock(HttpResponse.class);
        when(mockResponse.statusCode()).thenReturn(200);
        when(mockResponse.headers()).thenReturn(HttpHeaders.of(Map.of(), (k, v) -> true));
        when(mockResponse.body()).thenReturn(new ByteArrayInputStream(new byte[]{1, 2, 3, 4}));

        ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
        when(mockClient.send(captor.capture(), any(HttpResponse.BodyHandler.class)))
                .thenReturn(mockResponse);

        SafeMediaLoader loader = new SafeMediaLoaderImpl(mockClient, mockDns, 3);
        byte[] bytes = loader.loadSecureImage(
                URI.create("https://example.com/img.png"),
                1024,
                5000
        ).get();

        assertArrayEquals(new byte[]{1, 2, 3, 4}, bytes);

        // Verify the sent request retains the original hostname "example.com" for TLS SNI
        HttpRequest sentRequest = captor.getValue();
        assertEquals("example.com", sentRequest.uri().getHost(),
                "Request host must be original hostname, not an IP address");
        assertEquals("https", sentRequest.uri().getScheme());
        assertEquals("/img.png", sentRequest.uri().getPath());
    }

    @Test
    @DisplayName("HTTP requests pin resolved IP into URI to prevent DNS rebinding SSRF")
    void testHttpPinsResolvedIp() throws Exception {
        HttpClient mockClient = mock(HttpClient.class);
        SafeMediaLoaderImpl.DnsResolver mockDns = host -> new InetAddress[]{InetAddress.getByName("93.184.216.34")};

        HttpResponse<InputStream> mockResponse = mock(HttpResponse.class);
        when(mockResponse.statusCode()).thenReturn(200);
        when(mockResponse.headers()).thenReturn(HttpHeaders.of(Map.of(), (k, v) -> true));
        when(mockResponse.body()).thenReturn(new ByteArrayInputStream(new byte[]{5, 6, 7}));

        ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
        when(mockClient.send(captor.capture(), any(HttpResponse.BodyHandler.class)))
                .thenReturn(mockResponse);

        SafeMediaLoader loader = new SafeMediaLoaderImpl(mockClient, mockDns, 3);
        byte[] bytes = loader.loadSecureImage(
                URI.create("http://example.com/img.png"),
                1024,
                5000
        ).get();

        assertArrayEquals(new byte[]{5, 6, 7}, bytes);

        HttpRequest sentRequest = captor.getValue();
        assertEquals("93.184.216.34", sentRequest.uri().getHost(),
                "HTTP request must pin the resolved IP address to prevent DNS rebinding");
        assertEquals("http", sentRequest.uri().getScheme());
        assertEquals("/img.png", sentRequest.uri().getPath());
    }

    @Test
    @DisplayName("Exceeding maxSizeBytes via Content-Length header is rejected")
    void testContentLengthExceededRejected() throws Exception {
        HttpClient mockClient = mock(HttpClient.class);
        SafeMediaLoaderImpl.DnsResolver mockDns = host -> new InetAddress[]{InetAddress.getByName("93.184.216.34")};

        HttpResponse<InputStream> mockResponse = mock(HttpResponse.class);
        when(mockResponse.statusCode()).thenReturn(200);
        HttpHeaders headers = HttpHeaders.of(
                Map.of("Content-Length", List.of("5000")),
                (k, v) -> true
        );
        when(mockResponse.headers()).thenReturn(headers);

        when(mockClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(mockResponse);

        SafeMediaLoader loader = new SafeMediaLoaderImpl(mockClient, mockDns, 3);
        CompletableFuture<byte[]> future = loader.loadSecureImage(
                URI.create("https://example.com/huge.png"),
                1000, // max 1000 bytes
                5000
        );

        ExecutionException ex = assertThrows(ExecutionException.class, future::get);
        assertTrue(ex.getCause() instanceof IllegalArgumentException);
        assertTrue(ex.getCause().getMessage().contains("exceeds maximum limit"));
    }

    @Test
    @DisplayName("Streaming payload exceeding maxSizeBytes is aborted")
    void testStreamingSizeLimitAborts() throws Exception {
        HttpClient mockClient = mock(HttpClient.class);
        SafeMediaLoaderImpl.DnsResolver mockDns = host -> new InetAddress[]{InetAddress.getByName("93.184.216.34")};

        HttpResponse<InputStream> mockResponse = mock(HttpResponse.class);
        when(mockResponse.statusCode()).thenReturn(200);
        when(mockResponse.headers()).thenReturn(HttpHeaders.of(Map.of(), (k, v) -> true));
        byte[] oversized = new byte[2000];
        when(mockResponse.body()).thenReturn(new ByteArrayInputStream(oversized));

        when(mockClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(mockResponse);

        SafeMediaLoader loader = new SafeMediaLoaderImpl(mockClient, mockDns, 3);
        CompletableFuture<byte[]> future = loader.loadSecureImage(
                URI.create("https://example.com/stream.png"),
                500, // max 500 bytes
                5000
        );

        ExecutionException ex = assertThrows(ExecutionException.class, future::get);
        assertTrue(ex.getCause() instanceof IllegalArgumentException);
        assertTrue(ex.getCause().getMessage().contains("exceeded maximum size limit"));
    }

    @Test
    @DisplayName("Exceeding maximum 3 redirect hops throws SsrfViolationException")
    void testRedirectLimitExceededThrows() throws Exception {
        HttpClient mockClient = mock(HttpClient.class);
        SafeMediaLoaderImpl.DnsResolver mockDns = host -> new InetAddress[]{InetAddress.getByName("93.184.216.34")};

        // Chain of 4 redirects: 1 -> 2 -> 3 -> 4
        HttpResponse<InputStream> r1 = mock(HttpResponse.class);
        when(r1.statusCode()).thenReturn(302);
        when(r1.headers()).thenReturn(HttpHeaders.of(Map.of("Location", List.of("https://example.com/step2")), (k, v) -> true));

        HttpResponse<InputStream> r2 = mock(HttpResponse.class);
        when(r2.statusCode()).thenReturn(302);
        when(r2.headers()).thenReturn(HttpHeaders.of(Map.of("Location", List.of("https://example.com/step3")), (k, v) -> true));

        HttpResponse<InputStream> r3 = mock(HttpResponse.class);
        when(r3.statusCode()).thenReturn(302);
        when(r3.headers()).thenReturn(HttpHeaders.of(Map.of("Location", List.of("https://example.com/step4")), (k, v) -> true));

        HttpResponse<InputStream> r4 = mock(HttpResponse.class);
        when(r4.statusCode()).thenReturn(302);
        when(r4.headers()).thenReturn(HttpHeaders.of(Map.of("Location", List.of("https://example.com/step5")), (k, v) -> true));

        when(mockClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(r1)
                .thenReturn(r2)
                .thenReturn(r3)
                .thenReturn(r4);

        SafeMediaLoader loader = new SafeMediaLoaderImpl(mockClient, mockDns, 3);
        CompletableFuture<byte[]> future = loader.loadSecureImage(
                URI.create("https://example.com/step1"),
                1024 * 1024,
                5000
        );

        ExecutionException ex = assertThrows(ExecutionException.class, future::get);
        assertTrue(ex.getCause() instanceof SsrfViolationException,
                "Expected SsrfViolationException for exceeding redirect limit");
        assertTrue(ex.getCause().getMessage().contains("Maximum redirect limit"));
    }

    @Test
    @DisplayName("Cloud metadata IP 169.254.169.254 and IPv6 loopback ::1 are strictly rejected")
    void testCloudMetadataAndIpv6LoopbackBlocked() {
        assertThrows(SsrfViolationException.class, () ->
                SsrfFirewall.validate(InetAddress.getByName("169.254.169.254")));
        assertThrows(SsrfViolationException.class, () ->
                SsrfFirewall.validate(InetAddress.getByName("127.0.0.1")));
        assertThrows(SsrfViolationException.class, () ->
                SsrfFirewall.validate(InetAddress.getByName("::1")));
    }

    @Test
    @DisplayName("SafeMediaLoaderImpl exposes default 4s connect, 6s read, and 8MB constants")
    void testDefaultConstants() {
        assertEquals(3, SafeMediaLoaderImpl.DEFAULT_MAX_REDIRECTS);
        assertEquals(8 * 1024 * 1024, SafeMediaLoaderImpl.DEFAULT_MAX_SIZE_BYTES);
        assertEquals(4, SafeMediaLoaderImpl.DEFAULT_CONNECT_TIMEOUT_SECONDS);
        assertEquals(6, SafeMediaLoaderImpl.DEFAULT_READ_TIMEOUT_SECONDS);
    }
}
