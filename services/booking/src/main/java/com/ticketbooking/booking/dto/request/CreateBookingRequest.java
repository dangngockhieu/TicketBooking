package com.ticketbooking.booking.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record CreateBookingRequest(
        @NotNull(message = "Sự kiện không được để trống")
        UUID eventId,

        @NotEmpty(message = "Danh sách vé không được để trống")
        @Valid
        List<BookingItemRequest> items
) {
}
