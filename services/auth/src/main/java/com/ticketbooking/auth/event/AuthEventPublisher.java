package com.ticketbooking.auth.event;

import com.ticketbooking.common.event.OrganizerWelcomeEvent;
import com.ticketbooking.common.event.OtpEmailEvent;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Producer cho Kafka topic {@code auth.otp-requested}/{@code auth.organizer-created}
 * — Notification Service lắng nghe để gửi email thật (xem docs/technical-flows.md §0,
 * docs/api-design.md §1.5).
 */
@Component
public class AuthEventPublisher {

    private static final String TOPIC_OTP_REQUESTED = "auth.otp-requested";
    private static final String TOPIC_ORGANIZER_CREATED = "auth.organizer-created";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public AuthEventPublisher(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publishOtpRequested(OtpEmailEvent event) {
        kafkaTemplate.send(TOPIC_OTP_REQUESTED, event.getEmail(), event);
    }

    public void publishOrganizerCreated(OrganizerWelcomeEvent event) {
        kafkaTemplate.send(TOPIC_ORGANIZER_CREATED, event.getEmail(), event);
    }
}
