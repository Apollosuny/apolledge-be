package com.apollosuny.apolledgebe.budget.dto;

import java.time.YearMonth;
import java.util.UUID;

public record BudgetResponse(
        UUID accountId,
        YearMonth month,
        Long limitVnd
) {
}
