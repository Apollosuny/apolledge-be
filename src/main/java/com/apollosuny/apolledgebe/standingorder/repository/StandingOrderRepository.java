package com.apollosuny.apolledgebe.standingorder.repository;

import com.apollosuny.apolledgebe.standingorder.entity.StandingOrder;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StandingOrderRepository extends JpaRepository<StandingOrder, UUID> {

    List<StandingOrder> findAllByUser_IdOrderByCreatedAtDesc(UUID userId);

    Optional<StandingOrder> findByIdAndUser_Id(UUID id, UUID userId);

    @Query("""
            select s.id from StandingOrder s
            where s.autoPost = true
              and s.pausedAt is null
              and s.nextRunOn <= :today
            """)
    List<UUID> findDueAutoPostIds(@Param("today") LocalDate today);

    /**
     * select ... for update: serialises concurrent runs (two instances, or the scheduler and a
     * manual run) so an occurrence is posted once and next_run_on advances once.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from StandingOrder s where s.id = :id")
    Optional<StandingOrder> findByIdForUpdate(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from StandingOrder s where s.id = :id and s.user.id = :userId")
    Optional<StandingOrder> findByIdAndUserIdForUpdate(
            @Param("id") UUID id,
            @Param("userId") UUID userId
    );
}
