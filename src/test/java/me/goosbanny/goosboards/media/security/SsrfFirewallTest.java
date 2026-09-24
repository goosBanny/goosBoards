package me.goosbanny.goosboards.media.security;

import me.goosbanny.goosboards.media.exception.SsrfViolationException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;

import static org.junit.jupiter.api.Assertions.*;

class SsrfFirewallTest {

    @Test
    @DisplayName("Test 6.1: SSRF - loopback IPv4 (127.0.0.1) rejected")
    void testLoopbackIpv4Rejected() throws Exception {
        InetAddress addr = InetAddress.getByName("127.0.0.1");
        assertThrows(SsrfViolationException.class, () -> SsrfFirewall.validate(addr));
    }

    @Test
    @DisplayName("Test 6.2: SSRF - RFC1918 10.x.x.x rejected")
    void testRfc1918ClassARejected() throws Exception {
        InetAddress addr = InetAddress.getByName("10.0.0.1");
        assertThrows(SsrfViolationException.class, () -> SsrfFirewall.validate(addr));
    }

    @Test
    @DisplayName("Test 6.3: SSRF - RFC1918 192.168.x.x rejected")
    void testRfc1918ClassCRejected() throws Exception {
        InetAddress addr = InetAddress.getByName("192.168.1.1");
        assertThrows(SsrfViolationException.class, () -> SsrfFirewall.validate(addr));
    }

    @Test
    @DisplayName("Test 6.4: SSRF - link-local / AWS cloud metadata 169.254.169.254 rejected")
    void testAwsMetadataRejected() throws Exception {
        InetAddress addr = InetAddress.getByName("169.254.169.254");
        assertThrows(SsrfViolationException.class, () -> SsrfFirewall.validate(addr));
    }

    @Test
    @DisplayName("Test 6.5: SSRF - IPv6 loopback ::1 rejected")
    void testIpv6LoopbackRejected() throws Exception {
        InetAddress addr = InetAddress.getByName("::1");
        assertThrows(SsrfViolationException.class, () -> SsrfFirewall.validate(addr));
    }

    @Test
    @DisplayName("Test 6.7: SSRF - valid public IP accepted")
    void testValidPublicIpAccepted() throws Exception {
        InetAddress googleDns = InetAddress.getByName("8.8.8.8");
        assertDoesNotThrow(() -> SsrfFirewall.validate(googleDns));

        InetAddress cloudflareDns = InetAddress.getByName("1.1.1.1");
        assertDoesNotThrow(() -> SsrfFirewall.validate(cloudflareDns));
    }

    @Test
    @DisplayName("SSRF - RFC1918 Class B (172.16.x.x) rejected")
    void testRfc1918ClassBRejected() throws Exception {
        InetAddress addr = InetAddress.getByName("172.16.0.1");
        assertThrows(SsrfViolationException.class, () -> SsrfFirewall.validate(addr));

        InetAddress addrUpper = InetAddress.getByName("172.31.255.255");
        assertThrows(SsrfViolationException.class, () -> SsrfFirewall.validate(addrUpper));
    }

    @Test
    @DisplayName("SSRF - Carrier-grade NAT (100.64.0.1) rejected")
    void testCarrierGradeNatRejected() throws Exception {
        InetAddress addr = InetAddress.getByName("100.64.0.1");
        assertThrows(SsrfViolationException.class, () -> SsrfFirewall.validate(addr));
    }

    @Test
    @DisplayName("SSRF - IPv6 unique local (fc00::1) rejected")
    void testIpv6UniqueLocalRejected() throws Exception {
        InetAddress addr = InetAddress.getByName("fc00::1");
        assertThrows(SsrfViolationException.class, () -> SsrfFirewall.validate(addr));
    }

    @Test
    @DisplayName("SSRF - Current network 0.0.0.0 rejected")
    void testCurrentNetworkRejected() throws Exception {
        InetAddress addr = InetAddress.getByName("0.0.0.0");
        assertThrows(SsrfViolationException.class, () -> SsrfFirewall.validate(addr));
    }
}
