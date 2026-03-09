package com.skyhigh.checkin.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.skyhigh.checkin.dto.response.ErrorResponse;
import com.skyhigh.checkin.service.RateLimiterService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * HTTP filter that intercepts seat-map GET requests and enforces rate limiting.
 * Uses Redis sliding-window via RateLimiterService.
 *
 * Detection rule: If a single source accesses 50+ different seat maps within 2 seconds,
 * the source is temporarily blocked and the event is recorded for audit.
 *
 * Runs BEFORE JwtAuthenticationFilter so that bots are blocked before JWT parsing.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RateLimitingFilter extends OncePerRequestFilter {

    private final RateLimiterService rateLimiterService;
    private final ObjectMapper objectMapper;

    private static final String SEAT_MAP_PATH_PATTERN = "/api/v1/flights/";
    private static final String SEAT_MAP_PATH_SUFFIX = "/seats";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        // Only rate-limit GET requests to seat map endpoints
        if (!isSeatMapRequest(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        String sourceIdentifier = buildSourceIdentifier(request);
        String ipAddress = getClientIp(request);
        UUID passengerId = getPassengerId();
        String userAgent = request.getHeader("User-Agent");

        if (rateLimiterService.isRateLimited(sourceIdentifier, "seat-map", ipAddress, passengerId, userAgent)) {
            log.warn("Rate limit triggered for source={}, ip={}, uri={}", sourceIdentifier, ipAddress, request.getRequestURI());

            long retryAfter = rateLimiterService.getBlockRemainingSeconds(sourceIdentifier);
            if (retryAfter <= 0) retryAfter = 300; // default 5 minutes

            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setHeader("Retry-After", String.valueOf(retryAfter));

            ErrorResponse errorResponse = ErrorResponse.builder()
                    .error(ErrorResponse.ErrorDetail.builder()
                            .code("RATE_LIMIT_EXCEEDED")
                            .message("Too many requests detected. You have been temporarily blocked.")
                            .retryable(true)
                            .retryAfterSeconds((int) retryAfter)
                            .build())
                    .meta(ErrorResponse.Meta.builder()
                            .timestamp(LocalDateTime.now())
                            .path(request.getRequestURI())
                            .build())
                    .build();

            objectMapper.writeValue(response.getWriter(), errorResponse);
            return;
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Determines if the request is a seat map GET request.
     * Pattern: GET /api/v1/flights/{flightId}/seats
     */
    private boolean isSeatMapRequest(HttpServletRequest request) {
        if (!"GET".equalsIgnoreCase(request.getMethod())) {
            return false;
        }
        String uri = request.getRequestURI();
        return uri.contains(SEAT_MAP_PATH_PATTERN) && uri.endsWith(SEAT_MAP_PATH_SUFFIX);
    }

    /**
     * Builds a composite source identifier from IP and passenger ID.
     * Format: {IP}:{passengerId} or {IP}:anonymous
     */
    private String buildSourceIdentifier(HttpServletRequest request) {
        String ip = getClientIp(request);
        UUID passengerId = getPassengerId();
        if (passengerId != null) {
            return ip + ":" + passengerId;
        }
        return ip + ":anonymous";
    }

    /**
     * Extracts the real client IP, supporting X-Forwarded-For for load balancers.
     */
    private String getClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isEmpty()) {
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    /**
     * Extracts the passenger ID from the current security context, if authenticated.
     */
    private UUID getPassengerId() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getPrincipal() instanceof PassengerPrincipal principal) {
                return principal.getPassengerId();
            }
        } catch (Exception e) {
            // Ignore — unauthenticated request
        }
        return null;
    }
}

