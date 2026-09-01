package com.apollosuny.apolledgebe.account.dto;

import com.apollosuny.apolledgebe.account.entity.AccountType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.util.UUID;

public record CreateAccountRequest(
        @NotNull AccountType type,
        @NotBlank String name,
        String icon,
        UUID parentId,
        @Pattern(regexp = "^[A-Z]{3}$") String currency
) {
}
