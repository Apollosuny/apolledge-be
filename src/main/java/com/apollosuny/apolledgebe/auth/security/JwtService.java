package com.apollosuny.apolledgebe.auth.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class JwtService {

    private final JwtProperties jwtProperties;

    public String generateAccessToken(UUID userId) {
        Instant now = Instant.now();
        Instant expiredAt = now.plus(jwtProperties.accessExpiration());

        return Jwts.builder()
                .subject(userId.toString())
                .claim("type", "ACCESS")
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiredAt))
                .signWith(getAccessKey())
                .compact();
    }

    public String generateRefreshToken(UUID userId) {
        Instant now = Instant.now();
        Instant expiredAt = now.plus(jwtProperties.refreshExpiration());

        return Jwts.builder()
                .subject(userId.toString())
                .claim("type", "REFRESH")
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiredAt))
                .signWith(getRefreshKey())
                .compact();
    }

    private SecretKey getAccessKey() {
        return Keys.hmacShaKeyFor(
                jwtProperties.accessSecret()
                        .getBytes(StandardCharsets.UTF_8)
        );
    }

    private SecretKey getRefreshKey() {
        return Keys.hmacShaKeyFor(
                jwtProperties.refreshSecret().getBytes(StandardCharsets.UTF_8)
        );
    }

    public UUID extractUserIdFromAccessToken(String token) {
        Claims claims = parseToken(token, getAccessKey());

        validateTokenType(claims, "ACCESS");

        return UUID.fromString(claims.getSubject());
    }

    public UUID extractUserIdFromRefreshToken(String token) {
        Claims claims = parseToken(token, getRefreshKey());

        validateTokenType(claims, "REFRESH");

        return UUID.fromString(claims.getSubject());
    }

    public boolean isAccessTokenValid(String token) {
        try {
            Claims claims = parseToken(token, getAccessKey());
            validateTokenType(claims, "ACCESS");
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    public boolean isRefreshTokenValid(String token) {
        try {
            Claims claims = parseToken(token, getRefreshKey());
            validateTokenType(claims, "REFRESH");
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    private Claims parseToken(
            String token,
            SecretKey key
    ) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private void validateTokenType(
            Claims claims,
            String expectedType
    ) {
        String type = claims.get("type", String.class);
        if (!expectedType.equals(type)) {
            throw new JwtException("Invalid token type");
        }
    }
}
