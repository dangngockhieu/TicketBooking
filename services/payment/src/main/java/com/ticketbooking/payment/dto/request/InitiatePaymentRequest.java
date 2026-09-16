package com.ticketbooking.payment.dto.request;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record InitiatePaymentRequest(
        @NotNull(message = "Booking không được để trống")
        UUID bookingId,

        /** Frontend URL để MoMo redirect trình duyệt sau khi thanh toán — nếu bỏ trống, dùng {@code momo.redirect-url} mặc định. */
        String returnUrl
) {
}
