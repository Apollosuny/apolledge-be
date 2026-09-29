package com.apollosuny.apolledgebe.standingorder.entity;

import com.apollosuny.apolledgebe.account.entity.Account;
import com.apollosuny.apolledgebe.user.entity.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "standing_orders")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class StandingOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private String name;

    @Column(name = "amount_vnd", nullable = false)
    private Long amountVnd;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "debit_account_id", nullable = false)
    private Account debitAccount;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "credit_account_id", nullable = false)
    private Account creditAccount;

    @Column(name = "day_of_month", nullable = false)
    private Short dayOfMonth;

    @Column(name = "next_run_on", nullable = false)
    private LocalDate nextRunOn;

    @Column(name = "auto_post", nullable = false)
    private boolean autoPost;

    @Column(name = "paused_at")
    private Instant pausedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public boolean isPaused() {
        return pausedAt != null;
    }

    public void update(
            String name,
            long amountVnd,
            Account debitAccount,
            Account creditAccount,
            short dayOfMonth,
            boolean autoPost
    ) {
        this.name = name;
        this.amountVnd = amountVnd;
        this.debitAccount = debitAccount;
        this.creditAccount = creditAccount;
        this.dayOfMonth = dayOfMonth;
        this.autoPost = autoPost;
    }

    public void scheduleNextRunOn(LocalDate nextRunOn) {
        this.nextRunOn = nextRunOn;
    }

    public void pause(Instant pausedAt) {
        if (this.pausedAt == null) {
            this.pausedAt = pausedAt;
        }
    }

    public void resume() {
        this.pausedAt = null;
    }
}
