package com.apollosuny.apolledgebe.budget.dto;

import java.util.UUID;

public record BudgetSummaryResponse(
        UUID accountId,
        String name,
        String icon,
        long spentVnd,
        Long limitVnd,
        Long remainingVnd
) {
}
