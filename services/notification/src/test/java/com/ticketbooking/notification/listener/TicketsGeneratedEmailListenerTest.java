package com.ticketbooking.notification.listener;

import com.ticketbooking.common.event.TicketsGeneratedEvent;
import com.ticketbooking.notification.repository.EmailLogRepository;
import com.ticketbooking.notification.service.EmailService;
import com.ticketbooking.notification.service.QrCodeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TicketsGeneratedEmailListenerTest {

    @Mock
    private EmailService emailService;

    @Mock
    private QrCodeService qrCodeService;

    @Mock
    private EmailLogRepository emailLogRepository;

    private TicketsGeneratedEmailListener listener;

    @BeforeEach
    void setUp() {
        listener = new TicketsGeneratedEmailListener(emailService, qrCodeService, emailLogRepository, "support@ticketbooking.local");
    }

    private TicketsGeneratedEvent.TicketDetail ticket() {
        return TicketsGeneratedEvent.TicketDetail.builder()
                .ticketId(UUID.randomUUID())
                .ticketClassName("VIP")
                .qrCodeData(UUID.randomUUID().toString())
                .build();
    }

    @Test
    void onTicketsGenerated_skips_whenCustomerEmailMissing() {
        TicketsGeneratedEvent event = TicketsGeneratedEvent.builder()
                .bookingId(UUID.randomUUID())
                .customerEmail(null)
                .tickets(List.of(ticket()))
                .build();

        listener.onTicketsGenerated(event);

        verifyNoInteractions(emailService, qrCodeService);
    }

    @Test
    void onTicketsGenerated_skips_whenAlreadySentBefore() {
        UUID bookingId = UUID.randomUUID();
        when(emailLogRepository.existsByTypeAndDedupeKeyAndStatus("TICKET_ISSUED", bookingId.toString(), "SENT"))
                .thenReturn(true);

        TicketsGeneratedEvent event = TicketsGeneratedEvent.builder()
                .bookingId(bookingId)
                .customerEmail("customer@example.com")
                .tickets(List.of(ticket()))
                .build();

        listener.onTicketsGenerated(event);

        verifyNoInteractions(emailService, qrCodeService);
    }

    @Test
    void onTicketsGenerated_sendsEmailWithInlineQrPerTicket() {
        UUID bookingId = UUID.randomUUID();
        when(emailLogRepository.existsByTypeAndDedupeKeyAndStatus(any(), any(), any())).thenReturn(false);
        when(qrCodeService.generatePng(anyString())).thenReturn(new byte[]{1, 2, 3});

        TicketsGeneratedEvent event = TicketsGeneratedEvent.builder()
                .bookingId(bookingId)
                .customerEmail("customer@example.com")
                .eventTitle("Concert ABC")
                .tickets(List.of(ticket(), ticket()))
                .build();

        listener.onTicketsGenerated(event);

        verify(qrCodeService, times(2)).generatePng(anyString());
        verify(emailService).sendTemplatedEmailWithInlineImages(
                eq("TICKET_ISSUED"), eq(bookingId.toString()), eq("customer@example.com"),
                anyString(), eq("ticket-issued"), anyMap(), argThat(images -> images.size() == 2));
    }
}
