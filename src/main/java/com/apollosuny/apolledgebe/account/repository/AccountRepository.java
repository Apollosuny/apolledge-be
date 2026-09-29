package com.apollosuny.apolledgebe.account.repository;

import com.apollosuny.apolledgebe.account.entity.Account;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AccountRepository extends JpaRepository<Account, UUID> {
    List<Account> findAllByUser_Id(UUID userId);
    List<Account> findAllByUser_IdAndArchivedAtIsNull(UUID userId);
    Optional<Account> findByIdAndUser_Id(UUID id, UUID userId);
    List<Account> findAllByIdInAndUser_Id(Collection<UUID> ids, UUID userId);
}
