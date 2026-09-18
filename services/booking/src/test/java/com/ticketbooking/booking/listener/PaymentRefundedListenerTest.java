package com.ticketbooking.booking.listener;

import com.ticketbooking.booking.service.BookingService;
import com.ticketbooking.common.event.PaymentRefundedEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PaymentRefundedListenerTest {

    @Mock
    private BookingService bookingService;

    @Test
    void onPaymentRefunded_delegatesToBookingService() {
        PaymentRefundedListener listener = new PaymentRefundedListener(bookingService);
        UUID bookingId = UUID.randomUUID();
        PaymentRefundedEvent event = PaymentRefundedEvent.builder()
                .eventType("payment.refunded")
                .transactionId(UUID.randomUUID())
                .bookingId(bookingId)
                .amount(new BigDecimal("100000"))
                .gatewayTransId("123")
                .refundedAt(Instant.now())
                .build();

        listener.onPaymentRefunded(event);

        verify(bookingService).markRefunded(bookingId);
    }
}
