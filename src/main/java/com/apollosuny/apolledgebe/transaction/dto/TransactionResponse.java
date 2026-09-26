package com.apollosuny.apolledgebe.transaction.dto;

import com.apollosuny.apolledgebe.transaction.entity.TransactionSource;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record TransactionResponse(
        UUID id,
        Instant occurredAt,
        Instant postedAt,
        String note,
        String receiptUrl,
        TransactionSource source,
        List<LedgerEntryResponse> entries
) {
}
