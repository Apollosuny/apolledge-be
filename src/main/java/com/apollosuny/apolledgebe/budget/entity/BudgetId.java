package com.apollosuny.apolledgebe.budget.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.UUID;

@Embeddable
@Getter
@EqualsAndHashCode
@NoArgsConstructor
@AllArgsConstructor
public class BudgetId implements Serializable {

    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    @Column(name = "period_month", nullable = false)
    private LocalDate periodMonth;
}
