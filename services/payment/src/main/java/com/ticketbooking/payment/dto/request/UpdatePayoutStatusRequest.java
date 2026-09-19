package com.ticketbooking.payment.dto.request;

import com.ticketbooking.payment.enums.PayoutStatus;
import jakarta.validation.constraints.NotNull;

/** {@code reason} bắt buộc khi {@code status} là REJECTED hoặc HOLD (xem docs/api-design.md §6.5). */
public record UpdatePayoutStatusRequest(
        @NotNull(message = "Trạng thái không được để trống")
        PayoutStatus status,
        String reason
) {
}
