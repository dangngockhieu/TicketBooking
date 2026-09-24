package com.ticketbooking.common.event;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Kafka topic {@code payment.success} */
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentSuccessEvent extends BaseEvent {

    private UUID transactionId;
    private UUID bookingId;
    private BigDecimal amount;
    private String gatewayTransId;
    private Instant paidAt;
}
