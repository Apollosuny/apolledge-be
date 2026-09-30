package com.apollosuny.apolledgebe.account.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * @param asOf the cut-off the balance was computed at, or null for the balance over all entries
 */
public record AccountBalanceResponse(
        UUID accountId,
        Long balanceVnd,
        Instant asOf
) {
}
