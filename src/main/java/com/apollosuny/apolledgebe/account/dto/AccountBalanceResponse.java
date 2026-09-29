package com.apollosuny.apolledgebe.account.dto;

import java.util.UUID;

public record AccountBalanceResponse(
        UUID accountId,
        Long balanceVnd
) {
}
