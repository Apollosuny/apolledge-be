package com.apollosuny.apolledgebe.transaction.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.List;

public record CreateTransactionRequest(
        @NotNull
        Instant occurredAt,

        String note,

        String receiptUrl,

        String idempotencyKey,

        @NotEmpty
        List<@Valid CreateLedgerEntryRequest> entries
) {
}
