package com.skyhigh.checkin.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class SeatLockService {

    private static final String SEAT_LOCK_PREFIX = "seat:lock:";

    private final RedisTemplate<String, Object> redisTemplate;

    /**
     * Attempts to acquire a distributed lock for a seat.
     * Uses Redis SETNX (SET if Not eXists) with TTL.
     *
     * @param flightId    The flight ID
     * @param seatNumber  The seat number
     * @param passengerId The passenger attempting to hold the seat
     * @param ttlSeconds  The time-to-live for the lock
     * @return true if lock acquired, false if seat is already locked
     */
    public boolean acquireLock(UUID flightId, String seatNumber, UUID passengerId, int ttlSeconds) {
        String lockKey = buildLockKey(flightId, seatNumber);
        String lockValue = passengerId.toString();

        try {
            Boolean success = redisTemplate.opsForValue()
                    .setIfAbsent(lockKey, lockValue, Duration.ofSeconds(ttlSeconds));

            if (Boolean.TRUE.equals(success)) {
                log.info("Seat lock acquired: {} by passenger {}", lockKey, passengerId);
                return true;
            }

            log.debug("Seat lock already exists: {}", lockKey);
            return false;
        } catch (Exception e) {
            log.error("Error acquiring seat lock: {}", e.getMessage());
            // Fallback to database-only locking if Redis fails
            return false;
        }
    }

    /**
     * Releases a seat lock. Only the owner can release the lock.
     *
     * @param flightId    The flight ID
     * @param seatNumber  The seat number
     * @param passengerId The passenger releasing the hold
     * @return true if lock released, false otherwise
     */
    public boolean releaseLock(UUID flightId, String seatNumber, UUID passengerId) {
        String lockKey = buildLockKey(flightId, seatNumber);

        try {
            Object currentHolder = redisTemplate.opsForValue().get(lockKey);

            if (currentHolder != null && currentHolder.toString().equals(passengerId.toString())) {
                redisTemplate.delete(lockKey);
                log.info("Seat lock released: {} by passenger {}", lockKey, passengerId);
                return true;
            }

            log.warn("Cannot release lock: {} - not owned by passenger {}", lockKey, passengerId);
            return false;
        } catch (Exception e) {
            log.error("Error releasing seat lock: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Checks if a seat is currently locked.
     *
     * @param flightId   The flight ID
     * @param seatNumber The seat number
     * @return true if locked, false otherwise
     */
    public boolean isLocked(UUID flightId, String seatNumber) {
        String lockKey = buildLockKey(flightId, seatNumber);
        try {
            return Boolean.TRUE.equals(redisTemplate.hasKey(lockKey));
        } catch (Exception e) {
            log.error("Error checking seat lock: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Gets the passenger ID holding the lock.
     *
     * @param flightId   The flight ID
     * @param seatNumber The seat number
     * @return The passenger ID or null if not locked
     */
    public UUID getLockHolder(UUID flightId, String seatNumber) {
        String lockKey = buildLockKey(flightId, seatNumber);
        try {
            Object value = redisTemplate.opsForValue().get(lockKey);
            if (value != null) {
                return UUID.fromString(value.toString());
            }
            return null;
        } catch (Exception e) {
            log.error("Error getting lock holder: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Verifies if a specific passenger holds the lock.
     *
     * @param flightId    The flight ID
     * @param seatNumber  The seat number
     * @param passengerId The passenger ID to check
     * @return true if the passenger holds the lock
     */
    public boolean isLockedByPassenger(UUID flightId, String seatNumber, UUID passengerId) {
        UUID holder = getLockHolder(flightId, seatNumber);
        return holder != null && holder.equals(passengerId);
    }

    /**
     * Force releases a lock (for cleanup purposes).
     */
    public void forceReleaseLock(UUID flightId, String seatNumber) {
        String lockKey = buildLockKey(flightId, seatNumber);
        try {
            redisTemplate.delete(lockKey);
            log.info("Seat lock force released: {}", lockKey);
        } catch (Exception e) {
            log.error("Error force releasing seat lock: {}", e.getMessage());
        }
    }

    private String buildLockKey(UUID flightId, String seatNumber) {
        return SEAT_LOCK_PREFIX + flightId.toString() + ":" + seatNumber;
    }
}

