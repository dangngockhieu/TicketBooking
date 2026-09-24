package com.ticketbooking.booking.listener;

import com.ticketbooking.booking.service.BookingService;
import com.ticketbooking.common.event.PaymentSuccessEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consumer Kafka Choreography —
 * lắng nghe {@code payment.success} do Payment Service bắn sau khi xác nhận
 * IPN từ MoMo, xác nhận booking sang PAID/ISSUED.
 */
@Slf4j
@Component
public class PaymentEventListener {

    private final BookingService bookingService;

    public PaymentEventListener(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    @KafkaListener(topics = "payment.success")
    public void onPaymentSuccess(PaymentSuccessEvent event) {
        log.info("Nhận payment.success cho booking {} (transactionId={})",
                event.getBookingId(), event.getTransactionId());
        bookingService.confirmPayment(event.getBookingId(), event.getTransactionId(),
                event.getAmount(), event.getGatewayTransId());
    }
}
