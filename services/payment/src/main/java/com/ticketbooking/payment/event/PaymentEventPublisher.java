package com.ticketbooking.payment.event;

import com.ticketbooking.common.event.PaymentFailedEvent;
import com.ticketbooking.common.event.PaymentRefundedEvent;
import com.ticketbooking.common.event.PaymentSuccessEvent;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Producer cho Kafka Choreography.
 * Key = bookingId để mọi event của cùng 1 booking luôn nằm cùng partition,
 * giữ đúng thứ tự xử lý phía consumer (Booking Service).
 */
@Component
public class PaymentEventPublisher {

    private static final String TOPIC_PAYMENT_SUCCESS = "payment.success";
    private static final String TOPIC_PAYMENT_FAILED = "payment.failed";
    private static final String TOPIC_PAYMENT_REFUNDED = "payment.refunded";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public PaymentEventPublisher(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publishSuccess(PaymentSuccessEvent event) {
        kafkaTemplate.send(TOPIC_PAYMENT_SUCCESS, event.getBookingId().toString(), event);
    }

    public void publishFailed(PaymentFailedEvent event) {
        kafkaTemplate.send(TOPIC_PAYMENT_FAILED, event.getBookingId().toString(), event);
    }

    public void publishRefunded(PaymentRefundedEvent event) {
        kafkaTemplate.send(TOPIC_PAYMENT_REFUNDED, event.getBookingId().toString(), event);
    }
}
