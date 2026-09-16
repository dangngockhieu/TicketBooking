package com.ticketbooking.payment.dto.response;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PaymentInitiateResponse(
        UUID transactionId,
        UUID bookingId,
        BigDecimal amount,
        String paymentUrl,
        Instant expiredAt
) implements Serializable {
}
