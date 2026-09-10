package com.ticketbooking.booking.dto.response;

import com.ticketbooking.booking.entity.Booking;
import com.ticketbooking.booking.entity.Ticket;
import com.ticketbooking.booking.enums.BookingStatus;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record BookingResponse(
        UUID id,
        UUID eventId,
        BookingStatus status,
        BigDecimal totalAmount,
        List<ItemSummary> items,
        Instant expiredAt,
        /** Chưa khả dụng — cần Payment Service (GĐ4) sinh payUrl. */
        String paymentUrl,
        Instant createdAt
) implements Serializable {

    public record ItemSummary(
            UUID ticketClassId,
            String ticketClassName,
            int quantity,
            BigDecimal unitPrice,
            BigDecimal subtotal
    ) implements Serializable {
    }

    public static BookingResponse from(Booking booking) {
        Map<UUID, List<Ticket>> byTicketClass = new LinkedHashMap<>();
        for (Ticket ticket : booking.getTickets()) {
            byTicketClass.computeIfAbsent(ticket.getTicketClassId(), k -> new java.util.ArrayList<>()).add(ticket);
        }

        List<ItemSummary> items = byTicketClass.values().stream()
                .map(tickets -> {
                    Ticket sample = tickets.getFirst();
                    BigDecimal subtotal = sample.getUnitPrice().multiply(BigDecimal.valueOf(tickets.size()));
                    return new ItemSummary(sample.getTicketClassId(), sample.getTicketClassName(),
                            tickets.size(), sample.getUnitPrice(), subtotal);
                })
                .sorted(Comparator.comparing(ItemSummary::ticketClassName))
                .toList();

        return new BookingResponse(
                booking.getId(),
                booking.getEventId(),
                booking.getStatus(),
                booking.getTotalAmount(),
                items,
                booking.getExpiredAt(),
                null,
                booking.getCreatedAt());
    }
}
