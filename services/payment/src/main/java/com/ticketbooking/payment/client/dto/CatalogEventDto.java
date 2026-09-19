package com.ticketbooking.payment.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Bản sao rút gọn của {@code EventResponse} bên Catalog Service — chỉ giữ
 * các trường Payment Service cần để tính tiền vào ví Organizer và tạo payout
 * tự động (xem docs/development-plan.md GĐ4 mục 4).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CatalogEventDto(
        UUID id,
        UUID organizerId,
        String status,
        Instant endTime,
        BigDecimal commissionRate,
        BigDecimal flatFeePerTicket
) {
}
