package com.ticketbooking.booking.listener;

import com.ticketbooking.booking.service.BookingService;
import com.ticketbooking.common.event.PaymentRefundedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consumer Saga Compensation hoàn tất (xem docs/api-design.md §5.4) — lắng
 * nghe {@code payment.refunded} do Payment Service bắn sau khi MoMo xác nhận
 * hoàn tiền thành công, chuyển booking sang REFUNDED.
 */
@Slf4j
@Component
public class PaymentRefundedListener {

    private final BookingService bookingService;

    public PaymentRefundedListener(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    @KafkaListener(topics = "payment.refunded")
    public void onPaymentRefunded(PaymentRefundedEvent event) {
        log.info("Nhận payment.refunded cho booking {} (transactionId={})",
                event.getBookingId(), event.getTransactionId());
        bookingService.markRefunded(event.getBookingId());
    }
}
