package com.apollosuny.apolledgebe.standingorder.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record StandingOrderRequest(
        @NotBlank @Size(max = 100)
        String name,

        @NotNull @Positive
        Long amountVnd,

        @NotNull
        UUID debitAccountId,

        @NotNull
        UUID creditAccountId,

        // 29-31 are clamped to the last day of shorter months
        @NotNull @Min(1) @Max(31)
        Integer dayOfMonth,

        // null means true; false requires the user to confirm each occurrence
        Boolean autoPost
) {
}
