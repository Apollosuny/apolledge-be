package com.apollosuny.apolledgebe.transaction.repository;

import com.apollosuny.apolledgebe.transaction.entity.EntryDirection;
import com.apollosuny.apolledgebe.transaction.entity.LedgerEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, UUID> {
    List<LedgerEntry> findAllByTransaction_Id(UUID transactionId);
    List<LedgerEntry> findAllByTransaction_IdIn(Collection<UUID> transactionIds);

    @Query("""
        select coalesce(
            sum(
                case 
                    when e.direction = :direction then e.amountVnd
                    else 0
                end
                ), 0
            )
        from LedgerEntry e
        where e.account.id = :accountId
    """)
    long sumAmountByAccountAndDirection(
            @Param("accountId") UUID accountId,
            @Param("direction")EntryDirection direction
    );
}
