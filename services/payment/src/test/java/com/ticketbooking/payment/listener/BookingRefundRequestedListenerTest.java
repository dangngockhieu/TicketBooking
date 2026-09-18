package com.ticketbooking.payment.listener;

import com.ticketbooking.common.event.BookingRefundRequestedEvent;
import com.ticketbooking.payment.service.PaymentService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.UUID;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class BookingRefundRequestedListenerTest {

    @Mock
    private PaymentService paymentService;

    @Test
    void onBookingRefundRequested_delegatesToPaymentService() {
        BookingRefundRequestedListener listener = new BookingRefundRequestedListener(paymentService);
        BookingRefundRequestedEvent event = BookingRefundRequestedEvent.builder()
                .eventType("booking.refund-requested")
                .bookingId(UUID.randomUUID())
                .transactionId(UUID.randomUUID())
                .amount(new BigDecimal("100000"))
                .gatewayTransId("123")
                .reason("test")
                .build();

        listener.onBookingRefundRequested(event);

        verify(paymentService).processRefund(event);
    }
}
