package me.goosbanny.goosboards.interaction.economy.impl;

import me.goosbanny.goosboards.interaction.economy.ChargeResult;

import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.OfflinePlayer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class VaultEconomyGuardTest {

    @Test
    @DisplayName("Test 5.3: 50 concurrent clicks on a $150 button yields exactly 1 charge")
    void testFiftyConcurrentClicksSingleCharge() throws Exception {
        Economy mockEconomy = mock(Economy.class);
        OfflinePlayer mockPlayer = mock(OfflinePlayer.class);
        UUID playerId = UUID.randomUUID();

        when(mockEconomy.has(any(OfflinePlayer.class), eq(150.0))).thenReturn(true);
        when(mockEconomy.withdrawPlayer(any(OfflinePlayer.class), eq(150.0))).thenReturn(
                new EconomyResponse(150.0, 0.0, EconomyResponse.ResponseType.SUCCESS, null)
        );

        VaultEconomyGuard guard = new VaultEconomyGuard(
                mockEconomy,
                5000L,
                System::currentTimeMillis,
                id -> mockPlayer
        );

        int threadCount = 50;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        List<ChargeResult> results = Collections.synchronizedList(new ArrayList<>());
        String sharedIdempotencyKey = "txn-50-clicks-test";

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    ChargeResult res = guard.tryCharge(playerId, 150.0, sharedIdempotencyKey);
                    results.add(res);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        // Release all 50 threads simultaneously
        startLatch.countDown();
        assertTrue(doneLatch.await(5, TimeUnit.SECONDS), "Threads did not complete in time");
        executor.shutdown();

        // Exactly 1 success, 49 duplicate rejections
        long successCount = results.stream().filter(ChargeResult::success).count();
        long duplicateCount = results.stream()
                .filter(r -> !r.success() && "duplicate transaction".equals(r.receiptOrReason()))
                .count();

        assertEquals(1, successCount, "Exactly one transaction must succeed");
        assertEquals(49, duplicateCount, "Remaining 49 transactions must be rejected as duplicate");
        verify(mockEconomy, times(1)).withdrawPlayer(any(OfflinePlayer.class), eq(150.0));
    }

    @Test
    @DisplayName("Test 5.4: Idempotency window prevents duplicate and accepts after expiry")
    void testIdempotencyWindowExpiry() {
        Economy mockEconomy = mock(Economy.class);
        OfflinePlayer mockPlayer = mock(OfflinePlayer.class);
        UUID playerId = UUID.randomUUID();

        when(mockEconomy.has(any(OfflinePlayer.class), anyDouble())).thenReturn(true);
        when(mockEconomy.withdrawPlayer(any(OfflinePlayer.class), anyDouble())).thenReturn(
                new EconomyResponse(10.0, 90.0, EconomyResponse.ResponseType.SUCCESS, null)
        );

        AtomicLong mockTime = new AtomicLong(10_000L);
        long windowMs = 1000L;
        VaultEconomyGuard guard = new VaultEconomyGuard(
                mockEconomy,
                windowMs,
                mockTime::get,
                id -> mockPlayer
        );

        String key = "abc123";

        // First charge succeeds
        ChargeResult r1 = guard.tryCharge(playerId, 10.0, key);
        assertTrue(r1.success(), "First charge should succeed");

        // Second charge within window fails as duplicate
        mockTime.addAndGet(500L); // T = 10500ms (< 11000ms expiry)
        ChargeResult r2 = guard.tryCharge(playerId, 10.0, key);
        assertFalse(r2.success(), "Second charge within window should fail");
        assertEquals("duplicate transaction", r2.receiptOrReason());

        // Advance clock past idempotency window (T = 11001ms)
        mockTime.addAndGet(501L);
        ChargeResult r3 = guard.tryCharge(playerId, 10.0, key);
        assertTrue(r3.success(), "Charge after window expiry should succeed");

        verify(mockEconomy, times(2)).withdrawPlayer(any(OfflinePlayer.class), eq(10.0));
    }

    @Test
    @DisplayName("Insufficient funds returns failure without recording idempotency")
    void testInsufficientFunds() {
        Economy mockEconomy = mock(Economy.class);
        OfflinePlayer mockPlayer = mock(OfflinePlayer.class);
        UUID playerId = UUID.randomUUID();

        when(mockEconomy.has(any(OfflinePlayer.class), eq(500.0))).thenReturn(false);

        VaultEconomyGuard guard = new VaultEconomyGuard(
                mockEconomy,
                1000L,
                System::currentTimeMillis,
                id -> mockPlayer
        );

        ChargeResult result = guard.tryCharge(playerId, 500.0, "key-insufficient");
        assertFalse(result.success());
        assertEquals("insufficient funds", result.receiptOrReason());
        verify(mockEconomy, never()).withdrawPlayer(any(OfflinePlayer.class), anyDouble());
    }
}
