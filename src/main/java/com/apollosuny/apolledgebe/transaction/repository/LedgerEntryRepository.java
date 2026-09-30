package com.apollosuny.apolledgebe.transaction.repository;

import com.apollosuny.apolledgebe.transaction.entity.EntryDirection;
import com.apollosuny.apolledgebe.transaction.entity.LedgerEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, UUID> {
    List<LedgerEntry> findAllByTransaction_Id(UUID transactionId);
    List<LedgerEntry> findAllByTransaction_IdIn(Collection<UUID> transactionIds);

    /**
     * Debits minus credits over every entry of the account, in one pass.
     * Includes future-dated entries, matching what the account currently holds on the books.
     */
    @Query("""
        select coalesce(sum(case when e.direction = :debit then e.amountVnd else -e.amountVnd end), 0)
        from LedgerEntry e
        where e.account.id = :accountId
    """)
    long sumNetDebitByAccount(
            @Param("accountId") UUID accountId,
            @Param("debit") EntryDirection debit
    );

    /**
     * Debits minus credits for entries that occurred at or before {@code asOf}.
     * Filters on occurredAt, so backdated entries and reversals are reflected on their business date.
     */
    @Query("""
        select coalesce(sum(case when e.direction = :debit then e.amountVnd else -e.amountVnd end), 0)
        from LedgerEntry e
        where e.account.id = :accountId
          and e.occurredAt <= :asOf
    """)
    long sumNetDebitByAccountAsOf(
            @Param("accountId") UUID accountId,
            @Param("asOf") Instant asOf,
            @Param("debit") EntryDirection debit
    );

    /**
     * Net spend per expense account: debits raise it, credits (reversals, refunds) lower it.
     * Uses occurredAt because reports follow when money was spent, not when it was recorded.
     */
    @Query("""
        select new com.apollosuny.apolledgebe.transaction.repository.AccountSpent(
            e.account.id,
            sum(case when e.direction = :debit then e.amountVnd else -e.amountVnd end)
        )
        from LedgerEntry e
        where e.account.user.id = :userId
          and e.account.type = com.apollosuny.apolledgebe.account.entity.AccountType.EXPENSE
          and e.occurredAt >= :from
          and e.occurredAt < :to
        group by e.account.id
    """)
    List<AccountSpent> sumSpentByExpenseAccount(
            @Param("userId") UUID userId,
            @Param("from") Instant from,
            @Param("to") Instant to,
            @Param("debit") EntryDirection debit
    );
}
