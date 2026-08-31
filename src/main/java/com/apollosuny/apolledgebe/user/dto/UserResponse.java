package com.apollosuny.apolledgebe.user.dto;

import com.apollosuny.apolledgebe.user.entity.UserProvider;

import java.util.UUID;

public record UserResponse(
        UUID id,
        String username,
        String email,
        UserProvider provider
) {
}
