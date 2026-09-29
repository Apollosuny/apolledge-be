package com.apollosuny.apolledgebe.transaction.repository;

import com.apollosuny.apolledgebe.transaction.entity.Transaction;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface TransactionRepository extends JpaRepository<Transaction, UUID> {
    Page<Transaction> findAllByUser_Id(UUID userId, Pageable pageable);
    Optional<Transaction> findByIdAndUser_Id(UUID id, UUID userId);
    Optional<Transaction> findByUser_IdAndIdempotencyKey(UUID userId, String idempotencyKey);
}
