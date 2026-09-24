package me.goosbanny.goosboards.media.security;

import me.goosbanny.goosboards.media.exception.SsrfViolationException;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * Firewall validating resolved IP addresses against private, link-local, loopback,
 * and cloud-metadata network ranges to prevent Server-Side Request Forgery (SSRF).
 */
public final class SsrfFirewall {

    private SsrfFirewall() {}

    /**
     * Validates that the provided IP address is safe for outbound external communication.
     *
     * @param addr the resolved IP address to validate
     * @throws SsrfViolationException if the address falls into any blocked or private range
     */
    public static void validate(InetAddress addr) throws SsrfViolationException {
        if (addr == null) {
            throw new SsrfViolationException("Target address is null");
        }

        if (addr.isLoopbackAddress()) {
            throw new SsrfViolationException("Loopback address blocked: " + addr.getHostAddress());
        }
        if (addr.isSiteLocalAddress()) {
            throw new SsrfViolationException("Private RFC1918 site-local address blocked: " + addr.getHostAddress());
        }
        if (addr.isLinkLocalAddress()) {
            throw new SsrfViolationException("Link-local address blocked: " + addr.getHostAddress());
        }
        if (addr.isMulticastAddress()) {
            throw new SsrfViolationException("Multicast address blocked: " + addr.getHostAddress());
        }
        if (addr.isAnyLocalAddress()) {
            throw new SsrfViolationException("Any-local / wildcard address blocked: " + addr.getHostAddress());
        }

        byte[] raw = addr.getAddress();

        // Handle IPv4-mapped IPv6 address (::ffff:x.x.x.x)
        if (raw.length == 16 && isIpv4Mapped(raw)) {
            byte[] ipv4 = new byte[4];
            System.arraycopy(raw, 12, ipv4, 0, 4);
            try {
                validate(InetAddress.getByAddress(ipv4));
                return;
            } catch (UnknownHostException e) {
                throw new SsrfViolationException("Invalid IPv4-mapped address: " + addr.getHostAddress(), e);
            }
        }

        // IPv4 explicit prefix checks
        if (raw.length == 4) {
            int b0 = raw[0] & 0xFF;
            int b1 = raw[1] & 0xFF;

            // 0.0.0.0/8 - Current network
            if (b0 == 0) {
                throw new SsrfViolationException("Current network address blocked: " + addr.getHostAddress());
            }
            // 10.0.0.0/8 - RFC1918 Class A
            if (b0 == 10) {
                throw new SsrfViolationException("RFC1918 Class A address blocked: " + addr.getHostAddress());
            }
            // 127.0.0.0/8 - Loopback
            if (b0 == 127) {
                throw new SsrfViolationException("Loopback IPv4 address blocked: " + addr.getHostAddress());
            }
            // 100.64.0.0/10 - Shared address space (CGNAT)
            if (b0 == 100 && (b1 & 0xC0) == 64) {
                throw new SsrfViolationException("Carrier-grade NAT address blocked: " + addr.getHostAddress());
            }
            // 169.254.0.0/16 - Link-Local / AWS / GCP / Azure Cloud Metadata
            if (b0 == 169 && b1 == 254) {
                throw new SsrfViolationException("Cloud metadata / Link-local address blocked: " + addr.getHostAddress());
            }
            // 172.16.0.0/12 - RFC1918 Class B
            if (b0 == 172 && (b1 & 0xF0) == 16) {
                throw new SsrfViolationException("RFC1918 Class B address blocked: " + addr.getHostAddress());
            }
            // 192.168.0.0/16 - RFC1918 Class C
            if (b0 == 192 && b1 == 168) {
                throw new SsrfViolationException("RFC1918 Class C address blocked: " + addr.getHostAddress());
            }
            // 224.0.0.0/4 - Multicast
            if ((b0 & 0xF0) == 224) {
                throw new SsrfViolationException("Multicast address blocked: " + addr.getHostAddress());
            }
            // 240.0.0.0/4 - Reserved
            if ((b0 & 0xF0) == 240) {
                throw new SsrfViolationException("Reserved address blocked: " + addr.getHostAddress());
            }
        }

        // IPv6 explicit checks
        if (raw.length == 16) {
            int b0 = raw[0] & 0xFF;
            int b1 = raw[1] & 0xFF;

            // ::1 / 128 - Loopback
            if (isIpv6Loopback(raw)) {
                throw new SsrfViolationException("IPv6 loopback blocked: " + addr.getHostAddress());
            }
            // :: / 128 - Unspecified
            if (isAllZero(raw)) {
                throw new SsrfViolationException("IPv6 unspecified address blocked: " + addr.getHostAddress());
            }
            // fc00::/7 - Unique Local IPv6 (RFC 4193)
            if ((b0 & 0xFE) == 0xFC) {
                throw new SsrfViolationException("IPv6 unique local address blocked: " + addr.getHostAddress());
            }
            // fe80::/10 - Link-Local IPv6
            if (b0 == 0xFE && (b1 & 0xC0) == 0x80) {
                throw new SsrfViolationException("IPv6 link-local address blocked: " + addr.getHostAddress());
            }
            // ff00::/8 - Multicast
            if (b0 == 0xFF) {
                throw new SsrfViolationException("IPv6 multicast address blocked: " + addr.getHostAddress());
            }
        }
    }

    private static boolean isIpv4Mapped(byte[] raw) {
        for (int i = 0; i < 10; i++) {
            if (raw[i] != 0) return false;
        }
        return (raw[10] & 0xFF) == 0xFF && (raw[11] & 0xFF) == 0xFF;
    }

    private static boolean isIpv6Loopback(byte[] raw) {
        for (int i = 0; i < 15; i++) {
            if (raw[i] != 0) return false;
        }
        return raw[15] == 1;
    }

    private static boolean isAllZero(byte[] raw) {
        for (byte b : raw) {
            if (b != 0) return false;
        }
        return true;
    }
}
