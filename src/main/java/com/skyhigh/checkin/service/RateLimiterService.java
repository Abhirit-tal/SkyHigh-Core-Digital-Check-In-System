package com.skyhigh.checkin.service;

import com.skyhigh.checkin.config.CheckInConfig;
import com.skyhigh.checkin.model.entity.AbuseAuditLog;
import com.skyhigh.checkin.repository.AbuseAuditLogRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Redis-based sliding window rate limiter for abuse/bot detection.
 * Tracks request counts per source using Redis sorted sets with timestamps as scores.
 * Detects patterns like "50+ seat map accesses in 2 seconds" and blocks offending sources.
 */
@Service
@Slf4j
public class RateLimiterService {

    private static final String RATE_LIMIT_PREFIX = "ratelimit:";
    private static final String BLOCK_PREFIX = "blocked:";

    private final RedisTemplate<String, Object> redisTemplate;
    private final AbuseAuditLogRepository abuseAuditLogRepository;
    private final CheckInConfig checkInConfig;
    private final Counter rateLimitBlockCounter;

    public RateLimiterService(RedisTemplate<String, Object> redisTemplate,
                              AbuseAuditLogRepository abuseAuditLogRepository,
                              CheckInConfig checkInConfig,
                              MeterRegistry meterRegistry) {
        this.redisTemplate = redisTemplate;
        this.abuseAuditLogRepository = abuseAuditLogRepository;
        this.checkInConfig = checkInConfig;
        this.rateLimitBlockCounter = Counter.builder("skyhigh.ratelimit.blocks")
                .description("Number of sources blocked by rate limiter")
                .register(meterRegistry);
    }

    /**
     * Checks if a source is rate-limited using a sliding window algorithm.
     * Records the current request and checks if the source has exceeded the threshold.
     *
     * @param sourceIdentifier Composite key (IP + passenger ID or IP only)
     * @param endpoint         The endpoint being accessed
     * @param ipAddress        Client IP address
     * @param passengerId      Passenger ID (may be null for unauthenticated)
     * @param userAgent        Client user agent
     * @return true if the source is rate-limited and should be blocked
     */
    public boolean isRateLimited(String sourceIdentifier, String endpoint,
                                 String ipAddress, UUID passengerId, String userAgent) {
        try {
            // Check if source is already blocked
            if (isBlocked(sourceIdentifier)) {
                return true;
            }

            String key = RATE_LIMIT_PREFIX + endpoint + ":" + sourceIdentifier;
            long now = System.currentTimeMillis();
            long windowMs = checkInConfig.getRateLimitSeatMapWindowSeconds() * 1000L;
            long windowStart = now - windowMs;

            // Add current request timestamp to sorted set
            redisTemplate.opsForZSet().add(key, String.valueOf(now), now);

            // Remove entries outside the window
            redisTemplate.opsForZSet().removeRangeByScore(key, 0, windowStart);

            // Set expiry on the key to auto-cleanup
            redisTemplate.expire(key, Duration.ofSeconds(checkInConfig.getRateLimitSeatMapWindowSeconds() * 2L));

            // Count requests in window
            Long count = redisTemplate.opsForZSet().zCard(key);
            int requestCount = count != null ? count.intValue() : 0;

            if (requestCount > checkInConfig.getRateLimitSeatMapMaxRequests()) {
                log.warn("Rate limit exceeded: source={}, count={}, window={}s, endpoint={}",
                        sourceIdentifier, requestCount,
                        checkInConfig.getRateLimitSeatMapWindowSeconds(), endpoint);

                // Block the source
                blockSource(sourceIdentifier, checkInConfig.getRateLimitBlockDurationMinutes());

                // Record the abuse event
                recordAbuseEvent(sourceIdentifier, endpoint, requestCount, ipAddress, passengerId, userAgent);

                rateLimitBlockCounter.increment();
                return true;
            }

            return false;
        } catch (Exception e) {
            log.error("Error checking rate limit for {}: {}", sourceIdentifier, e.getMessage());
            // Fail open — don't block if Redis is unavailable
            return false;
        }
    }

    /**
     * Checks if a source is currently blocked.
     */
    public boolean isBlocked(String sourceIdentifier) {
        try {
            String blockKey = BLOCK_PREFIX + sourceIdentifier;
            return Boolean.TRUE.equals(redisTemplate.hasKey(blockKey));
        } catch (Exception e) {
            log.error("Error checking block status for {}: {}", sourceIdentifier, e.getMessage());
            return false;
        }
    }

    /**
     * Blocks a source for a specified duration.
     */
    public void blockSource(String sourceIdentifier, int durationMinutes) {
        try {
            String blockKey = BLOCK_PREFIX + sourceIdentifier;
            redisTemplate.opsForValue().set(blockKey, "BLOCKED", Duration.ofMinutes(durationMinutes));
            log.warn("Source blocked: {} for {} minutes", sourceIdentifier, durationMinutes);
        } catch (Exception e) {
            log.error("Error blocking source {}: {}", sourceIdentifier, e.getMessage());
        }
    }

    /**
     * Gets the remaining block time in seconds.
     */
    public long getBlockRemainingSeconds(String sourceIdentifier) {
        try {
            String blockKey = BLOCK_PREFIX + sourceIdentifier;
            Long ttl = redisTemplate.getExpire(blockKey);
            return ttl != null && ttl > 0 ? ttl : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * Records an abuse event for audit and review.
     */
    private void recordAbuseEvent(String sourceIdentifier, String endpoint, int requestCount,
                                  String ipAddress, UUID passengerId, String userAgent) {
        try {
            LocalDateTime blockedUntil = LocalDateTime.now()
                    .plusMinutes(checkInConfig.getRateLimitBlockDurationMinutes());

            AbuseAuditLog auditLog = AbuseAuditLog.builder()
                    .sourceIdentifier(sourceIdentifier)
                    .eventType("RATE_LIMIT_EXCEEDED")
                    .endpoint(endpoint)
                    .requestCount(requestCount)
                    .windowSeconds(checkInConfig.getRateLimitSeatMapWindowSeconds())
                    .details(String.format("Source made %d requests to %s in %d seconds (limit: %d)",
                            requestCount, endpoint, checkInConfig.getRateLimitSeatMapWindowSeconds(),
                            checkInConfig.getRateLimitSeatMapMaxRequests()))
                    .blockedUntil(blockedUntil)
                    .passengerId(passengerId)
                    .ipAddress(ipAddress)
                    .userAgent(userAgent)
                    .build();

            abuseAuditLogRepository.save(auditLog);
            log.info("Abuse event recorded: source={}, type=RATE_LIMIT_EXCEEDED", sourceIdentifier);
        } catch (Exception e) {
            log.error("Error recording abuse event: {}", e.getMessage());
        }
    }
}

