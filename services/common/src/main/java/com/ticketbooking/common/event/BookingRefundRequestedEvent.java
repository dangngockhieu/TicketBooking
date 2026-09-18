package com.ticketbooking.common.event;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Kafka topic {@code booking.refund-requested} — Saga Compensation (xem
 * docs/api-design.md §5.4, docs/development-plan.md GĐ4 mục 3): Booking
 * Service bắn khi khách đã bị trừ tiền (payment.success) nhưng không thể
 * hoàn tất sinh vé (booking không tồn tại, đã bị auto-release do hết hạn giữ
 * chỗ, hoặc lỗi hệ thống giữa chừng).
 */
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class BookingRefundRequestedEvent extends BaseEvent {

    private UUID bookingId;
    private UUID transactionId;
    private BigDecimal amount;

    /** transId gốc bên MoMo — bắt buộc để gọi Refund API. */
    private String gatewayTransId;

    private String reason;
}
