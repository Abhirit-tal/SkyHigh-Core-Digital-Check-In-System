package com.skyhigh.checkin.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests concurrent seat hold operations to verify that only one
 * passenger can successfully hold a seat at a time.
 */
@ExtendWith(MockitoExtension.class)
class ConcurrencySeatHoldTest {

    @Mock private RedisTemplate<String, Object> redisTemplate;
    @Mock private ValueOperations<String, Object> valueOperations;

    private SeatLockService seatLockService;

    @BeforeEach
    void setUp() {
        seatLockService = new SeatLockService(redisTemplate);
    }

    @Test
    @DisplayName("Only one of N concurrent passengers should acquire the lock")
    void onlyOnePassengerShouldAcquireLock() throws InterruptedException {
        UUID flightId = UUID.randomUUID();
        String seatNumber = "12A";
        int numPassengers = 10;

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        // First call succeeds, all others fail (simulating SETNX atomicity)
        AtomicInteger callCount = new AtomicInteger(0);
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenAnswer(invocation -> callCount.getAndIncrement() == 0);

        ExecutorService executor = Executors.newFixedThreadPool(numPassengers);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(numPassengers);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        for (int i = 0; i < numPassengers; i++) {
            UUID passengerId = UUID.randomUUID();
            executor.submit(() -> {
                try {
                    startLatch.await(); // All threads start simultaneously
                    boolean acquired = seatLockService.acquireLock(flightId, seatNumber, passengerId, 120);
                    if (acquired) {
                        successCount.incrementAndGet();
                    } else {
                        failCount.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown(); // Start all threads
        doneLatch.await(5, TimeUnit.SECONDS);
        executor.shutdown();

        assertEquals(1, successCount.get(), "Exactly one passenger should acquire the lock");
        assertEquals(numPassengers - 1, failCount.get(), "All other passengers should fail");
    }

    @Test
    @DisplayName("Lock holder should be correctly identified under concurrent access")
    void lockHolderShouldBeCorrectUnderConcurrency() {
        UUID flightId = UUID.randomUUID();
        String seatNumber = "12A";
        UUID winnerId = UUID.randomUUID();

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn(winnerId.toString());

        // Multiple passengers check who holds the lock
        for (int i = 0; i < 10; i++) {
            UUID checkerId = UUID.randomUUID();
            boolean isOwner = seatLockService.isLockedByPassenger(flightId, seatNumber, checkerId);
            assertFalse(isOwner, "Non-holder should not be identified as owner");
        }

        // Winner checks
        boolean isWinner = seatLockService.isLockedByPassenger(flightId, seatNumber, winnerId);
        assertTrue(isWinner, "Lock holder should be correctly identified");
    }
}

