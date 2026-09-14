package com.ticketbooking.payment.entity;

import com.ticketbooking.payment.enums.PayoutSource;
import com.ticketbooking.payment.enums.PayoutStatus;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payout_requests")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PayoutRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** 🔗 Soft Key → Auth Service accounts.id. */
    @Column(name = "organizer_id", nullable = false)
    private UUID organizerId;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    /**
     * Snapshot tự động từ {@code organizer_bank_accounts} (User Service) tại
     * thời điểm tạo request — KHÔNG phải field client tự điền (xem
     * docs/database-schema.md §5).
     */
    @Column(name = "bank_name", nullable = false, length = 100)
    private String bankName;

    @Column(name = "bank_account_number", nullable = false, length = 50)
    private String bankAccountNumber;

    @Column(name = "bank_account_holder", nullable = false, length = 255)
    private String bankAccountHolder;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private PayoutStatus status = PayoutStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private PayoutSource source;

    /** 🔗 Soft Key → Catalog Service events.id — chỉ khi {@code source = AUTO}. */
    @Column(name = "event_id")
    private UUID eventId;

    @Column(columnDefinition = "TEXT")
    private String reason;

    @Column(name = "momo_disbursement_id", unique = true, length = 255)
    private String momoDisbursementId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "processed_at")
    private Instant processedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = Instant.now();
    }
}
