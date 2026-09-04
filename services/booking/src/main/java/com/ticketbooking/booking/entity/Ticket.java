package com.ticketbooking.booking.entity;

import com.ticketbooking.booking.enums.TicketStatus;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "tickets")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Ticket {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booking_id", nullable = false)
    private Booking booking;

    /** 🔗 Soft Key → Catalog Service ticket_classes.id. */
    @Column(name = "ticket_class_id", nullable = false)
    private UUID ticketClassId;

    /** Snapshot tên hạng vé tại thời điểm đặt — tránh gọi lại Catalog Service khi hiển thị vé. */
    @Column(name = "ticket_class_name", nullable = false, length = 100)
    private String ticketClassName;

    /** Snapshot đơn giá tại thời điểm đặt — giá vé không đổi dù Organizer sửa giá sau đó. */
    @Column(name = "unit_price", nullable = false, precision = 15, scale = 2)
    private BigDecimal unitPrice;

    @Column(name = "qr_code_data", nullable = false, unique = true, length = 255)
    private String qrCodeData;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private TicketStatus status = TicketStatus.LOCKED;

    @Column(name = "checked_in_at")
    private Instant checkedInAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
