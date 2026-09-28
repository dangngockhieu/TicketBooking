package com.ticketbooking.notification.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * Nhật ký gửi email — dùng để tra soát (đã gửi/thất bại) và chống gửi trùng
 * cho E-Ticket (xem docs/development-plan.md GĐ4 mục 2, Consumer 3 —
 * "Lưu log vào MongoDB").
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "email_logs")
public class EmailLog {

    @Id
    private String id;

    /** {@code EMAIL_VERIFICATION} | {@code PASSWORD_RESET} | {@code TICKET_ISSUED}. */
    private String type;

    /** Khoá dedupe cho idempotent consumer — {@code bookingId} với TICKET_ISSUED, {@code null} với OTP (cho phép gửi lại OTP nhiều lần là hợp lệ). */
    private String dedupeKey;

    private String recipient;

    private String subject;

    private String status;

    private String errorMessage;

    private Instant sentAt;
}
