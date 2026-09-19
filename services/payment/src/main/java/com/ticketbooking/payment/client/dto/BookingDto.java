package com.ticketbooking.payment.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Bản sao rút gọn của {@code BookingResponse} bên Booking Service — chỉ giữ
 * lại các trường Payment Service cần để xác thực trước khi khởi tạo thanh
 * toán (xem docs/api-design.md §5.1).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record BookingDto(
        UUID id,
        UUID eventId,
        String status,
        BigDecimal totalAmount,
        int quantity,
        Instant expiredAt
) {
}
