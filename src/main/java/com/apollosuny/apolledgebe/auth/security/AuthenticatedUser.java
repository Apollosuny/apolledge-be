package com.apollosuny.apolledgebe.auth.security;

import java.util.UUID;

public record AuthenticatedUser(
        UUID id,
        String username
) {
}
