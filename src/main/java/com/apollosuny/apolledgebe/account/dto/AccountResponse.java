package com.apollosuny.apolledgebe.account.dto;

import com.apollosuny.apolledgebe.account.entity.AccountType;

import java.time.Instant;
import java.util.UUID;

public record AccountResponse(
        UUID id,
        AccountType type,
        String name,
        String icon,
        UUID parentId,
        String currency,
        Instant archivedAt,
        Instant createdAt
) {
}
