package com.apollosuny.apolledgebe.standingorder.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record StandingOrderResponse(
        UUID id,
        String name,
        Long amountVnd,
        UUID debitAccountId,
        UUID creditAccountId,
        Integer dayOfMonth,
        LocalDate nextRunOn,
        boolean autoPost,
        Instant pausedAt,
        Instant createdAt
) {
}
