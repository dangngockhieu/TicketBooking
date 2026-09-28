package com.ticketbooking.auth.event;

import com.ticketbooking.common.event.OtpEmailEvent;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Producer cho Kafka topic {@code auth.otp-requested} — Notification Service
 * lắng nghe để gửi email OTP thật (xem docs/technical-flows.md §0, mục "Còn
 * thiếu" trong docs/development-plan.md GĐ1).
 */
@Component
public class AuthEventPublisher {

    private static final String TOPIC_OTP_REQUESTED = "auth.otp-requested";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public AuthEventPublisher(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publishOtpRequested(OtpEmailEvent event) {
        kafkaTemplate.send(TOPIC_OTP_REQUESTED, event.getEmail(), event);
    }
}
