package com.ticketbooking.booking.dto.response;

import java.math.BigDecimal;
import java.util.UUID;

/** Kết quả thô của {@code TicketRepository#aggregateSoldByTicketClass} — số vé đã bán/check-in/doanh thu theo từng hạng vé. */
public record TicketClassReportRow(
        UUID ticketClassId,
        String ticketClassName,
        Long soldCount,
        Long checkedInCount,
        BigDecimal revenue
) {
}
