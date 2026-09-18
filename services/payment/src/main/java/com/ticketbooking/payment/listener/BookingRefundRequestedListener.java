package com.ticketbooking.payment.listener;

import com.ticketbooking.common.event.BookingRefundRequestedEvent;
import com.ticketbooking.payment.service.PaymentService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consumer Saga Compensation (xem docs/api-design.md §5.4, docs/development-plan.md
 * GĐ4 mục 3) — lắng nghe {@code booking.refund-requested} do Booking Service
 * bắn khi khách đã bị trừ tiền nhưng không thể hoàn tất sinh vé.
 */
@Slf4j
@Component
public class BookingRefundRequestedListener {

    private final PaymentService paymentService;

    public BookingRefundRequestedListener(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @KafkaListener(topics = "booking.refund-requested")
    public void onBookingRefundRequested(BookingRefundRequestedEvent event) {
        log.info("Nhận booking.refund-requested cho booking {} (transactionId={}, reason={})",
                event.getBookingId(), event.getTransactionId(), event.getReason());
        paymentService.processRefund(event);
    }
}
