package com.apollosuny.apolledgebe.auth.security;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private static final String ACCESS_SECRET = "access-secret-access-secret-access-secret";
    private static final String REFRESH_SECRET = "refresh-secret-refresh-secret-refresh-secret";

    private final UUID userId = UUID.randomUUID();
    private final JwtService jwtService = serviceWith(Duration.ofMinutes(15), Duration.ofDays(30));

    @Test
    void parseRefreshToken_shouldReturnUserIdAndIssuedAt() {
        RefreshTokenClaims claims = jwtService.parseRefreshToken(jwtService.generateRefreshToken(userId));

        assertThat(claims.userId()).isEqualTo(userId);
        assertThat(claims.issuedAt()).isBeforeOrEqualTo(Instant.now());
    }

    @Test
    void accessToken_shouldNotBeAcceptedAsRefreshToken() {
        String accessToken = jwtService.generateAccessToken(userId);

        assertThat(jwtService.isRefreshTokenValid(accessToken)).isFalse();
        assertThatThrownBy(() -> jwtService.parseRefreshToken(accessToken)).isInstanceOf(JwtException.class);
    }

    @Test
    void refreshToken_shouldNotBeAcceptedAsAccessToken() {
        String refreshToken = jwtService.generateRefreshToken(userId);

        assertThat(jwtService.isAccessTokenValid(refreshToken)).isFalse();
    }

    @Test
    void tamperedToken_shouldBeRejected() {
        String token = jwtService.generateRefreshToken(userId);
        String tampered = token.substring(0, token.length() - 2) + (token.endsWith("AA") ? "BB" : "AA");

        assertThatThrownBy(() -> jwtService.parseRefreshToken(tampered)).isInstanceOf(JwtException.class);
    }

    @Test
    void expiredToken_shouldBeRejected() {
        JwtService shortLived = serviceWith(Duration.ofMinutes(15), Duration.ofSeconds(-1));

        assertThatThrownBy(() -> jwtService.parseRefreshToken(shortLived.generateRefreshToken(userId)))
                .isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    void blankToken_shouldBeRejected() {
        assertThatThrownBy(() -> jwtService.parseRefreshToken(" ")).isInstanceOf(IllegalArgumentException.class);
    }

    private JwtService serviceWith(Duration accessExpiration, Duration refreshExpiration) {
        return new JwtService(new JwtProperties(
                ACCESS_SECRET, REFRESH_SECRET, accessExpiration, refreshExpiration
        ));
    }
}
