package com.apollosuny.apolledgebe.budget.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record SetBudgetRequest(
        @NotNull
        @Positive
        Long limitVnd
) {
}
