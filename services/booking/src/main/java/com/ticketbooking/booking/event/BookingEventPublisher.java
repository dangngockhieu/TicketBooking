package com.ticketbooking.booking.event;

import com.ticketbooking.common.event.BookingRefundRequestedEvent;
import com.ticketbooking.common.event.TicketsGeneratedEvent;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Producer cho Kafka Choreography (xem docs/development-plan.md GĐ4 mục 2-3).
 * Key = bookingId để mọi event của cùng 1 booking luôn nằm cùng partition.
 */
@Component
public class BookingEventPublisher {

    private static final String TOPIC_TICKETS_GENERATED = "tickets.generated";
    private static final String TOPIC_BOOKING_REFUND_REQUESTED = "booking.refund-requested";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public BookingEventPublisher(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publishTicketsGenerated(TicketsGeneratedEvent event) {
        kafkaTemplate.send(TOPIC_TICKETS_GENERATED, event.getBookingId().toString(), event);
    }

    public void publishRefundRequested(BookingRefundRequestedEvent event) {
        kafkaTemplate.send(TOPIC_BOOKING_REFUND_REQUESTED, event.getBookingId().toString(), event);
    }
}
