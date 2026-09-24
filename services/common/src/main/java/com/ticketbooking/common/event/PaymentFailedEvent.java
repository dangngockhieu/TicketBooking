package com.ticketbooking.common.event;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.util.UUID;

/** Kafka topic {@code payment.failed} */
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentFailedEvent extends BaseEvent {

    private UUID transactionId;
    private UUID bookingId;
    private String reason;
}
