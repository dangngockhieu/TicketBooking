package com.ticketbooking.booking.dto.response;

import java.time.Instant;
import java.util.UUID;

/** Xem docs/api-design.md §4.5 cho shape response chính xác. */
public record CheckInResponse(
        UUID ticketId,
        String ticketClass,
        String eventTitle,
        String customerName,
        String status,
        Instant checkedInAt
) {
}
