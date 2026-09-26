package com.ticketbooking.booking.dto.request;

import jakarta.validation.constraints.NotBlank;

public record CheckInRequest(
        @NotBlank(message = "qrCodeData không được để trống")
        String qrCodeData
) {
}
