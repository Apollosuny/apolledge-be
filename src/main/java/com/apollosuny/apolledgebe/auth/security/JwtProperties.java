package com.apollosuny.apolledgebe.auth.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

@ConfigurationProperties(prefix = "jwt")
public record JwtProperties(
        String accessSecret,
        String refreshSecret,
        Duration accessExpiration,
        Duration refreshExpiration
) {

    private static final int MINIMUM_SECRET_BYTES = 32;

    /**
     * An unset JWT_*_SECRET reaches this record as the literal "${...}" placeholder, which would
     * otherwise boot fine and only fail on the first login. Refuse to start instead.
     */
    public JwtProperties {
        requireStrongSecret("jwt.access-secret", accessSecret);
        requireStrongSecret("jwt.refresh-secret", refreshSecret);
    }

    private static void requireStrongSecret(String property, String secret) {
        if (secret == null
                || secret.startsWith("${")
                || secret.getBytes(StandardCharsets.UTF_8).length < MINIMUM_SECRET_BYTES) {
            throw new IllegalArgumentException(
                    property + " must be set to at least " + MINIMUM_SECRET_BYTES + " bytes"
            );
        }
    }
}
