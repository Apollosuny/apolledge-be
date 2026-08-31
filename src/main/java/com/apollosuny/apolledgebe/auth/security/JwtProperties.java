package com.apollosuny.apolledgebe.auth.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "jwt")
public record JwtProperties(
        String accessSecret,
        String refreshSecret,
        Duration accessExpiration,
        Duration refreshExpiration
) {
}
