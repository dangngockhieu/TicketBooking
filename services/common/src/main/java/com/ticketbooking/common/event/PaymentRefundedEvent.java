package com.ticketbooking.common.event;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Kafka topic {@code payment.refunded} — Saga Compensation hoàn tất (xem
 * docs/api-design.md §5.4): Payment Service bắn sau khi MoMo xác nhận hoàn
 * tiền thành công, Booking Service lắng nghe để chuyển booking sang REFUNDED.
 */
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentRefundedEvent extends BaseEvent {

    private UUID transactionId;
    private UUID bookingId;
    private BigDecimal amount;
    private String gatewayTransId;
    private Instant refundedAt;
}
