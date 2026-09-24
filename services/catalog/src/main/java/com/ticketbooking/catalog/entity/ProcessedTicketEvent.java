package com.ticketbooking.catalog.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Đánh dấu một {@code bookingId} đã được xử lý cho Kafka topic
 * {@code tickets.generated} — dùng làm chốt chặn idempotent consumer, chống
 * trừ vĩnh viễn {@code available_quantity} 2 lần khi Kafka redeliver message
 */
@Entity
@Table(name = "processed_ticket_events")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ProcessedTicketEvent {

    @Id
    @Column(name = "booking_id")
    private UUID bookingId;

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;

    public ProcessedTicketEvent(UUID bookingId) {
        this.bookingId = bookingId;
    }

    @PrePersist
    protected void onCreate() {
        this.processedAt = Instant.now();
    }
}
