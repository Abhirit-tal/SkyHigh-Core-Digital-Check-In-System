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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SeatLockServiceTest {

    @Mock private RedisTemplate<String, Object> redisTemplate;
    @Mock private ValueOperations<String, Object> valueOperations;

    private SeatLockService seatLockService;

    private UUID flightId;
    private UUID passengerId;
    private String seatNumber;

    @BeforeEach
    void setUp() {
        seatLockService = new SeatLockService(redisTemplate);
        flightId = UUID.randomUUID();
        passengerId = UUID.randomUUID();
        seatNumber = "1A";
    }

    @Test
    @DisplayName("Should acquire lock successfully via SETNX")
    void shouldAcquireLock() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);

        boolean acquired = seatLockService.acquireLock(flightId, seatNumber, passengerId, 120);

        assertTrue(acquired);
        verify(valueOperations).setIfAbsent(
                "seat:lock:" + flightId + ":" + seatNumber,
                passengerId.toString(),
                Duration.ofSeconds(120));
    }

    @Test
    @DisplayName("Should fail to acquire lock when already held")
    void shouldFailWhenLockExists() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(false);

        boolean acquired = seatLockService.acquireLock(flightId, seatNumber, passengerId, 120);

        assertFalse(acquired);
    }

    @Test
    @DisplayName("Should release lock when owned by passenger")
    void shouldReleaseLockWhenOwned() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn(passengerId.toString());
        when(redisTemplate.delete(anyString())).thenReturn(true);

        boolean released = seatLockService.releaseLock(flightId, seatNumber, passengerId);

        assertTrue(released);
        verify(redisTemplate).delete("seat:lock:" + flightId + ":" + seatNumber);
    }

    @Test
    @DisplayName("Should not release lock when owned by another passenger")
    void shouldNotReleaseWhenNotOwned() {
        UUID otherPassengerId = UUID.randomUUID();
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn(otherPassengerId.toString());

        boolean released = seatLockService.releaseLock(flightId, seatNumber, passengerId);

        assertFalse(released);
        verify(redisTemplate, never()).delete(anyString());
    }

    @Test
    @DisplayName("Should check if seat is locked")
    void shouldCheckIfLocked() {
        when(redisTemplate.hasKey("seat:lock:" + flightId + ":" + seatNumber)).thenReturn(true);

        boolean locked = seatLockService.isLocked(flightId, seatNumber);

        assertTrue(locked);
    }

    @Test
    @DisplayName("Should verify lock holder")
    void shouldVerifyLockHolder() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn(passengerId.toString());

        boolean isOwner = seatLockService.isLockedByPassenger(flightId, seatNumber, passengerId);

        assertTrue(isOwner);
    }

    @Test
    @DisplayName("Should force release lock for cleanup")
    void shouldForceReleaseLock() {
        seatLockService.forceReleaseLock(flightId, seatNumber);

        verify(redisTemplate).delete("seat:lock:" + flightId + ":" + seatNumber);
    }

    @Test
    @DisplayName("Should handle Redis errors gracefully in acquireLock")
    void shouldHandleRedisErrorOnAcquire() {
        when(redisTemplate.opsForValue()).thenThrow(new RuntimeException("Redis down"));

        boolean acquired = seatLockService.acquireLock(flightId, seatNumber, passengerId, 120);

        assertFalse(acquired);
    }

    @Test
    @DisplayName("Should handle Redis errors gracefully in releaseLock")
    void shouldHandleRedisErrorOnRelease() {
        when(redisTemplate.opsForValue()).thenThrow(new RuntimeException("Redis down"));

        boolean released = seatLockService.releaseLock(flightId, seatNumber, passengerId);

        assertFalse(released);
    }
}

