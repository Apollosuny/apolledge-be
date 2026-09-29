package com.apollosuny.apolledgebe.auth.security;

import java.time.Instant;
import java.util.UUID;

public record RefreshTokenClaims(
        UUID userId,
        Instant issuedAt
) {
}
