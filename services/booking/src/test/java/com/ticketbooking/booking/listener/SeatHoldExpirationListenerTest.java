package com.ticketbooking.booking.listener;

import com.ticketbooking.booking.entity.Booking;
import com.ticketbooking.booking.enums.BookingStatus;
import com.ticketbooking.booking.repository.BookingRepository;
import com.ticketbooking.booking.service.BookingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.connection.DefaultMessage;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SeatHoldExpirationListenerTest {

    private static final byte[] CHANNEL = "__keyevent@0__:expired".getBytes(StandardCharsets.UTF_8);

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private BookingService bookingService;

    private SeatHoldExpirationListener listener;

    @BeforeEach
    void setUp() {
        listener = new SeatHoldExpirationListener(bookingRepository, bookingService);
    }

    @Test
    void onMessage_releasesMatchingBooking_whenSeatHoldKeyExpires() {
        UUID eventId = UUID.randomUUID();
        UUID ticketClassId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();

        Booking booking = Booking.builder().id(bookingId).customerId(customerId)
                .eventId(eventId).status(BookingStatus.PENDING_PAYMENT).build();
        when(bookingRepository.findFirstByCustomerIdAndEventIdAndStatus(customerId, eventId, BookingStatus.PENDING_PAYMENT))
                .thenReturn(Optional.of(booking));

        String key = "seat_hold:" + eventId + ":" + ticketClassId + ":" + customerId;
        listener.onMessage(new DefaultMessage(CHANNEL, key.getBytes(StandardCharsets.UTF_8)), null);

        verify(bookingService).releaseExpiredBooking(bookingId);
    }

    @Test
    void onMessage_ignoresKeysWithOtherPrefixes() {
        String key = "hold_count:" + UUID.randomUUID() + ":" + UUID.randomUUID();
        listener.onMessage(new DefaultMessage(CHANNEL, key.getBytes(StandardCharsets.UTF_8)), null);

        verifyNoInteractions(bookingRepository, bookingService);
    }

    @Test
    void onMessage_ignoresMalformedKey() {
        String key = "seat_hold:not-a-valid-uuid";
        listener.onMessage(new DefaultMessage(CHANNEL, key.getBytes(StandardCharsets.UTF_8)), null);

        verifyNoInteractions(bookingRepository, bookingService);
    }

    @Test
    void onMessage_noop_whenNoPendingBookingFound() {
        UUID eventId = UUID.randomUUID();
        UUID ticketClassId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        when(bookingRepository.findFirstByCustomerIdAndEventIdAndStatus(eq(customerId), eq(eventId), eq(BookingStatus.PENDING_PAYMENT)))
                .thenReturn(Optional.empty());

        String key = "seat_hold:" + eventId + ":" + ticketClassId + ":" + customerId;
        listener.onMessage(new DefaultMessage(CHANNEL, key.getBytes(StandardCharsets.UTF_8)), null);

        verifyNoInteractions(bookingService);
    }
}
