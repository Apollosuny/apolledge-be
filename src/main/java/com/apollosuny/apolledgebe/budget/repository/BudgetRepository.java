package com.apollosuny.apolledgebe.budget.repository;

import com.apollosuny.apolledgebe.budget.entity.Budget;
import com.apollosuny.apolledgebe.budget.entity.BudgetId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface BudgetRepository extends JpaRepository<Budget, BudgetId> {

    @Query("""
            select b from Budget b
            join fetch b.account a
            where a.user.id = :userId
              and b.id.periodMonth = :periodMonth
            """)
    List<Budget> findAllByUserAndMonth(
            @Param("userId") UUID userId,
            @Param("periodMonth") LocalDate periodMonth
    );

    /**
     * Single atomic statement: a read-then-write upsert would fail on the primary key
     * when two requests set the same budget concurrently.
     */
    @Modifying
    @Query(value = """
            insert into budgets (account_id, period_month, limit_vnd)
            values (:accountId, :periodMonth, :limitVnd)
            on conflict (account_id, period_month)
            do update set limit_vnd = excluded.limit_vnd
            """, nativeQuery = true)
    void upsert(
            @Param("accountId") UUID accountId,
            @Param("periodMonth") LocalDate periodMonth,
            @Param("limitVnd") long limitVnd
    );
}
