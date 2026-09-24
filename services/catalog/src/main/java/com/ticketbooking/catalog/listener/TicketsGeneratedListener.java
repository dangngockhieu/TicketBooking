package com.ticketbooking.catalog.listener;

import com.ticketbooking.catalog.service.EventService;
import com.ticketbooking.common.event.TicketsGeneratedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consumer Kafka Choreography,
 * Consumer 2) — lắng nghe {@code tickets.generated} do Booking Service bắn
 * sau khi xác nhận thanh toán, trừ vĩnh viễn {@code available_quantity}.
 */
@Slf4j
@Component
public class TicketsGeneratedListener {

    private final EventService eventService;

    public TicketsGeneratedListener(EventService eventService) {
        this.eventService = eventService;
    }

    @KafkaListener(topics = "tickets.generated")
    public void onTicketsGenerated(TicketsGeneratedEvent event) {
        log.info("Nhận tickets.generated cho booking {} (event={})", event.getBookingId(), event.getCatalogEventId());
        eventService.processTicketsGenerated(event);
    }
}
