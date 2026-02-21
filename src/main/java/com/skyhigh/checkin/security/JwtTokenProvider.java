package com.skyhigh.checkin.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
@Slf4j
public class JwtTokenProvider {

    private final SecretKey secretKey;
    private final long accessTokenExpiration;
    private final long refreshTokenExpiration;

    public JwtTokenProvider(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.access-token-expiration}") long accessTokenExpiration,
            @Value("${jwt.refresh-token-expiration}") long refreshTokenExpiration) {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTokenExpiration = accessTokenExpiration;
        this.refreshTokenExpiration = refreshTokenExpiration;
    }

    public String generateAccessToken(PassengerPrincipal principal) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + accessTokenExpiration);

        return Jwts.builder()
                .subject(principal.getPassengerId().toString())
                .claim("email", principal.getEmail())
                .claim("firstName", principal.getFirstName())
                .claim("lastName", principal.getLastName())
                .claim("flightIds", principal.getFlightIds().stream()
                        .map(UUID::toString)
                        .collect(Collectors.toList()))
                .issuedAt(now)
                .expiration(expiryDate)
                .issuer("skyhigh-core")
                .signWith(secretKey)
                .compact();
    }

    public String generateRefreshToken(PassengerPrincipal principal) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + refreshTokenExpiration);

        return Jwts.builder()
                .subject(principal.getPassengerId().toString())
                .claim("type", "refresh")
                .issuedAt(now)
                .expiration(expiryDate)
                .issuer("skyhigh-core")
                .signWith(secretKey)
                .compact();
    }

    public PassengerPrincipal parseToken(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();

        List<String> flightIdStrings = claims.get("flightIds", List.class);
        List<UUID> flightIds = flightIdStrings != null
                ? flightIdStrings.stream().map(UUID::fromString).collect(Collectors.toList())
                : List.of();

        return PassengerPrincipal.builder()
                .passengerId(UUID.fromString(claims.getSubject()))
                .email(claims.get("email", String.class))
                .firstName(claims.get("firstName", String.class))
                .lastName(claims.get("lastName", String.class))
                .flightIds(flightIds)
                .build();
    }

    public UUID getPassengerIdFromRefreshToken(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();

        String type = claims.get("type", String.class);
        if (!"refresh".equals(type)) {
            throw new JwtException("Invalid token type");
        }

        return UUID.fromString(claims.getSubject());
    }

    public boolean validateToken(String token) {
        try {
            Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("Invalid JWT token: {}", e.getMessage());
            return false;
        }
    }

    public long getAccessTokenExpiration() {
        return accessTokenExpiration;
    }
}

