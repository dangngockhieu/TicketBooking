package com.ticketbooking.payment.dto.response;

import com.ticketbooking.payment.entity.PayoutRequest;
import com.ticketbooking.payment.enums.PayoutSource;
import com.ticketbooking.payment.enums.PayoutStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PayoutResponse(
        UUID id,
        BigDecimal amount,
        String bankName,
        String bankAccountNumber,
        String bankAccountHolder,
        PayoutStatus status,
        PayoutSource source,
        UUID eventId,
        String reason,
        Instant createdAt,
        Instant processedAt
) {
    public static PayoutResponse from(PayoutRequest entity) {
        return new PayoutResponse(
                entity.getId(), entity.getAmount(), entity.getBankName(), entity.getBankAccountNumber(),
                entity.getBankAccountHolder(), entity.getStatus(), entity.getSource(), entity.getEventId(),
                entity.getReason(), entity.getCreatedAt(), entity.getProcessedAt());
    }
}
