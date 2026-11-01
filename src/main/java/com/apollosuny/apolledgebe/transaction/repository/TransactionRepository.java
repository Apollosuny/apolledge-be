package com.apollosuny.apolledgebe.transaction.repository;

import com.apollosuny.apolledgebe.transaction.entity.Transaction;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface TransactionRepository extends JpaRepository<Transaction, UUID> {
    /**
     * Transactions that occurred in [from, to). With activeOnly, reversals and the transactions
     * they cancel are both left out, so what remains is what the user still considers recorded.
     */
    @Query("""
        select t from Transaction t
        where t.user.id = :userId
          and t.occurredAt >= :from
          and t.occurredAt < :to
          and (:activeOnly = false or (
                t.reverses is null
                and not exists (select 1 from Transaction r where r.reverses = t)
          ))
    """)
    Page<Transaction> findInPeriod(
            @Param("userId") UUID userId,
            @Param("from") Instant from,
            @Param("to") Instant to,
            @Param("activeOnly") boolean activeOnly,
            Pageable pageable
    );

    boolean existsByReverses_Id(UUID reversesId);
    Optional<Transaction> findByIdAndUser_Id(UUID id, UUID userId);
    Optional<Transaction> findByUser_IdAndIdempotencyKey(UUID userId, String idempotencyKey);
}
