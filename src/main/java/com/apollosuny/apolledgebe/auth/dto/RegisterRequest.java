package com.apollosuny.apolledgebe.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @Size(min = 3, max = 50) String username,
        @Email @NotBlank @Size(max = 254) String email,
        // 72 is BCrypt's hard input limit
        @NotBlank @Size(min = 8, max = 72) String password
) {
}
