package com.apollosuny.apolledgebe.transaction.dto;

import com.apollosuny.apolledgebe.transaction.entity.EntryDirection;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.UUID;

public record CreateLedgerEntryRequest(
        @NotNull
        UUID accountId,

        @NotNull
        EntryDirection direction,

        @NotNull
        @Positive
        long amountVnd
) {
}
