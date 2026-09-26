package com.apollosuny.apolledgebe.transaction.entity;

import com.apollosuny.apolledgebe.user.entity.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "transactions",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uq_transactions_user_idempotency",
                        columnNames = {"user_id", "idempotency_key"}
                )
        }
)
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Transaction {

    @Id

    @GeneratedValue(strategy = GenerationType.UUID)

    @Column(name = "id", nullable = false, updatable = false)

    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)

    @JoinColumn(name = "user_id", nullable = false)

    private User user;

    @Column(name = "occurred_at", nullable = false)

    private Instant occurredAt;

    @CreationTimestamp

    @Column(name = "posted_at", nullable = false, updatable = false)

    private Instant postedAt;

    @Column(name = "note")

    private String note;

    @Column(name = "receipt_url")

    private String receiptUrl;

    @Enumerated(EnumType.STRING)

    @Column(name = "source", nullable = false, length = 30)

    @Builder.Default

    private TransactionSource source = TransactionSource.MANUAL;

    @Column(name = "standing_order_id")

    private UUID standingOrderId;

    @ManyToOne(fetch = FetchType.LAZY)

    @JoinColumn(name = "reverses_id")

    private Transaction reverses;

    @Column(name = "idempotency_key")

    private String idempotencyKey;
}
