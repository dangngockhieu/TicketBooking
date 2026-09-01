package com.ticketbooking.catalog.dto.request;

import java.time.Instant;

/** Tập hợp query param của `GET /api/events` — không phải request body. */
public record EventSearchFilter(
        String categorySlug,
        Instant startFrom,
        Instant startTo,
        String location,
        String keyword
) {
}
