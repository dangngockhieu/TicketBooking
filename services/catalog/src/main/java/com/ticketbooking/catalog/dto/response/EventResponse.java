package com.ticketbooking.catalog.dto.response;

import com.ticketbooking.catalog.entity.Category;
import com.ticketbooking.catalog.entity.Event;
import com.ticketbooking.catalog.entity.TicketClass;
import com.ticketbooking.catalog.enums.EventStatus;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record EventResponse(
        UUID id,
        CategoryRef category,
        UUID organizerId,
        String title,
        String description,
        String location,
        String venueName,
        String bannerUrl,
        Instant startTime,
        Instant endTime,
        Instant saleStartTime,
        Instant saleEndTime,
        EventStatus status,
        BigDecimal commissionRate,
        BigDecimal flatFeePerTicket,
        List<TicketClassSummary> ticketClasses,
        Instant createdAt,
        Instant updatedAt
) implements Serializable {

    public record CategoryRef(UUID id, String name) implements Serializable {
        static CategoryRef from(Category category) {
            return category == null ? null : new CategoryRef(category.getId(), category.getName());
        }
    }

    public record TicketClassSummary(UUID id, String name, BigDecimal price, Integer availableQuantity)
            implements Serializable {
        static TicketClassSummary from(TicketClass ticketClass) {
            return new TicketClassSummary(
                    ticketClass.getId(),
                    ticketClass.getName(),
                    ticketClass.getPrice(),
                    ticketClass.getAvailableQuantity());
        }
    }

    public static EventResponse from(Event event) {
        return new EventResponse(
                event.getId(),
                CategoryRef.from(event.getCategory()),
                event.getOrganizerId(),
                event.getTitle(),
                event.getDescription(),
                event.getLocation(),
                event.getVenueName(),
                event.getBannerUrl(),
                event.getStartTime(),
                event.getEndTime(),
                event.getSaleStartTime(),
                event.getSaleEndTime(),
                event.getStatus(),
                event.getCommissionRate(),
                event.getFlatFeePerTicket(),
                event.getTicketClasses().stream().map(TicketClassSummary::from).toList(),
                event.getCreatedAt(),
                event.getUpdatedAt());
    }
}
