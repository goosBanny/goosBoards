package me.goosbanny.goosboards.interaction;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

class InteractionRateLimiterTest {

    @Test
    @DisplayName("Test 5.1: 8 clicks allowed per second, 9th rejected")
    void testEightClicksAllowedNinthRejected() {
        AtomicLong mockNanos = new AtomicLong(1_000_000_000L);
        InteractionRateLimiter limiter = new InteractionRateLimiter(8, 20, mockNanos::get);
        UUID playerId = UUID.randomUUID();

        // 8 clicks at the same timestamp
        for (int i = 1; i <= 8; i++) {
            assertTrue(limiter.tryConsumeClick(playerId), "Click " + i + " should be allowed");
        }

        // 9th click within the same second should be rejected
        assertFalse(limiter.tryConsumeClick(playerId), "9th click should be rejected by rate limiter");
    }

    @Test
    @DisplayName("Test 5.2: Token refill after 1 second")
    void testTokenRefillAfterOneSecond() {
        AtomicLong mockNanos = new AtomicLong(1_000_000_000L);
        InteractionRateLimiter limiter = new InteractionRateLimiter(8, 20, mockNanos::get);
        UUID playerId = UUID.randomUUID();

        // Exhaust all 8 tokens
        for (int i = 0; i < 8; i++) {
            assertTrue(limiter.tryConsumeClick(playerId));
        }
        assertFalse(limiter.tryConsumeClick(playerId));

        // Advance clock by 1 second (1_000_000_000 ns)
        mockNanos.addAndGet(1_000_000_000L);

        // Next click is accepted
        assertTrue(limiter.tryConsumeClick(playerId), "Click after 1s refill should be accepted");
    }

    @Test
    @DisplayName("Hover rate limiting: 20 hovers per second, 21st rejected")
    void testHoverRateLimiting() {
        AtomicLong mockNanos = new AtomicLong(1_000_000_000L);
        InteractionRateLimiter limiter = new InteractionRateLimiter(8, 20, mockNanos::get);
        UUID playerId = UUID.randomUUID();

        for (int i = 1; i <= 20; i++) {
            assertTrue(limiter.tryConsumeHover(playerId), "Hover " + i + " should be allowed");
        }

        assertFalse(limiter.tryConsumeHover(playerId), "21st hover should be rejected");

        // Advance half a second (0.5s -> 10 tokens refilled)
        mockNanos.addAndGet(500_000_000L);
        assertTrue(limiter.tryConsumeHover(playerId));
    }

    @Test
    @DisplayName("Reset clears player tokens")
    void testReset() {
        AtomicLong mockNanos = new AtomicLong(1_000_000_000L);
        InteractionRateLimiter limiter = new InteractionRateLimiter(8, 20, mockNanos::get);
        UUID playerId = UUID.randomUUID();

        for (int i = 0; i < 8; i++) {
            limiter.tryConsumeClick(playerId);
        }
        assertFalse(limiter.tryConsumeClick(playerId));

        limiter.reset(playerId);
        assertTrue(limiter.tryConsumeClick(playerId), "Should allow click after reset");
    }
}
