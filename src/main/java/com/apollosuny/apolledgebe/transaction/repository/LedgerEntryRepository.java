package com.apollosuny.apolledgebe.transaction.repository;

import com.apollosuny.apolledgebe.transaction.entity.LedgerEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, UUID> {
    List<LedgerEntry> findAllByTransaction_Id(UUID transactionId);
    List<LedgerEntry> findAllByTransaction_IdIn(Collection<UUID> transactionIds);
}
