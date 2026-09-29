package com.apollosuny.apolledgebe.budget.entity;

import com.apollosuny.apolledgebe.account.entity.Account;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "budgets")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Budget {

    @EmbeddedId
    private BudgetId id;

    @MapsId("accountId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @Column(name = "limit_vnd", nullable = false)
    private Long limitVnd;
}
