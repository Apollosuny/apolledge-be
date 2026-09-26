package com.apollosuny.apolledgebe.transaction.dto;

import com.apollosuny.apolledgebe.transaction.entity.EntryDirection;

import java.util.UUID;

public record LedgerEntryResponse(
        UUID id,
        UUID accountId,
        EntryDirection direction,
        Long amountVnd
) {
}
