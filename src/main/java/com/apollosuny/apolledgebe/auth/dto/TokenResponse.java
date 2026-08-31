package com.apollosuny.apolledgebe.auth.dto;

import com.apollosuny.apolledgebe.user.dto.UserResponse;

public record TokenResponse(
        String accessToken,
        String refreshToken,
        UserResponse user
) {
}
