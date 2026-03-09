package com.skyhigh.checkin.service;

import com.skyhigh.checkin.config.CheckInConfig;
import com.skyhigh.checkin.repository.AbuseAuditLogRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.ZSetOperations;

import java.time.Duration;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RateLimiterServiceTest {

    @Mock private RedisTemplate<String, Object> redisTemplate;
    @Mock private AbuseAuditLogRepository abuseAuditLogRepository;
    @Mock private CheckInConfig checkInConfig;
    @Mock private ZSetOperations<String, Object> zSetOperations;
    @Mock private ValueOperations<String, Object> valueOperations;

    private RateLimiterService rateLimiterService;

    @BeforeEach
    void setUp() {
        rateLimiterService = new RateLimiterService(redisTemplate, abuseAuditLogRepository,
                checkInConfig, new SimpleMeterRegistry());
    }

    @Test
    @DisplayName("Should allow requests under the rate limit")
    void shouldAllowRequestsUnderLimit() {
        when(redisTemplate.hasKey(anyString())).thenReturn(false);
        when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
        when(zSetOperations.zCard(anyString())).thenReturn(5L);
        when(checkInConfig.getRateLimitSeatMapWindowSeconds()).thenReturn(2);
        when(checkInConfig.getRateLimitSeatMapMaxRequests()).thenReturn(50);

        boolean result = rateLimiterService.isRateLimited("192.168.1.1", "seat-map",
                "192.168.1.1", null, "TestAgent");

        assertFalse(result);
    }

    @Test
    @DisplayName("Should block requests exceeding the rate limit")
    void shouldBlockRequestsExceedingLimit() {
        when(redisTemplate.hasKey(startsWith("blocked:"))).thenReturn(false);
        when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
        when(zSetOperations.zCard(anyString())).thenReturn(51L);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(checkInConfig.getRateLimitSeatMapWindowSeconds()).thenReturn(2);
        when(checkInConfig.getRateLimitSeatMapMaxRequests()).thenReturn(50);
        when(checkInConfig.getRateLimitBlockDurationMinutes()).thenReturn(5);
        when(abuseAuditLogRepository.save(any())).thenReturn(null);

        boolean result = rateLimiterService.isRateLimited("192.168.1.1", "seat-map",
                "192.168.1.1", null, "TestAgent");

        assertTrue(result);
        verify(valueOperations).set(eq("blocked:192.168.1.1"), eq("BLOCKED"), eq(Duration.ofMinutes(5)));
        verify(abuseAuditLogRepository).save(any());
    }

    @Test
    @DisplayName("Should detect already blocked source")
    void shouldDetectAlreadyBlockedSource() {
        when(redisTemplate.hasKey("blocked:192.168.1.1")).thenReturn(true);

        boolean result = rateLimiterService.isRateLimited("192.168.1.1", "seat-map",
                "192.168.1.1", null, "TestAgent");

        assertTrue(result);
    }

    @Test
    @DisplayName("Should fail open when Redis is unavailable")
    void shouldFailOpenWhenRedisDown() {
        when(redisTemplate.hasKey(anyString())).thenThrow(new RuntimeException("Redis unavailable"));

        boolean result = rateLimiterService.isRateLimited("192.168.1.1", "seat-map",
                "192.168.1.1", null, "TestAgent");

        assertFalse(result);
    }

    @Test
    @DisplayName("Should block source for specified duration")
    void shouldBlockSourceForDuration() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        rateLimiterService.blockSource("test-source", 10);

        verify(valueOperations).set("blocked:test-source", "BLOCKED", Duration.ofMinutes(10));
    }

    @Test
    @DisplayName("Should check if source is blocked")
    void shouldCheckIfSourceBlocked() {
        when(redisTemplate.hasKey("blocked:test-source")).thenReturn(true);

        assertTrue(rateLimiterService.isBlocked("test-source"));
    }
}

