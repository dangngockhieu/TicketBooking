package com.ticketbooking.booking.service;

import com.ticketbooking.booking.client.CatalogClient;
import com.ticketbooking.booking.client.QueueClient;
import com.ticketbooking.booking.client.UserClient;
import com.ticketbooking.booking.client.dto.CatalogEventDto;
import com.ticketbooking.booking.client.dto.QueueAccessDto;
import com.ticketbooking.booking.dto.request.BookingItemRequest;
import com.ticketbooking.booking.dto.request.CheckInRequest;
import com.ticketbooking.booking.dto.request.CreateBookingRequest;
import com.ticketbooking.booking.dto.response.BookingResponse;
import com.ticketbooking.booking.dto.response.CheckInResponse;
import com.ticketbooking.booking.dto.response.EventReportResponse;
import com.ticketbooking.booking.entity.Booking;
import com.ticketbooking.booking.entity.Ticket;
import com.ticketbooking.booking.enums.BookingStatus;
import com.ticketbooking.booking.enums.TicketStatus;
import com.ticketbooking.booking.event.BookingEventPublisher;
import com.ticketbooking.booking.dto.response.TicketClassReportRow;
import com.ticketbooking.booking.repository.BookingRepository;
import com.ticketbooking.booking.repository.TicketRepository;
import com.ticketbooking.booking.service.impl.BookingServiceImpl;
import com.ticketbooking.common.event.BookingRefundRequestedEvent;
import com.ticketbooking.common.exception.ConflictException;
import com.ticketbooking.common.exception.ForbiddenException;
import com.ticketbooking.common.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BookingServiceImplTest {

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private TicketRepository ticketRepository;

    @Mock
    private CatalogClient catalogClient;

    @Mock
    private QueueClient queueClient;

    @Mock
    private UserClient userClient;

    @Mock
    private SeatHoldService seatHoldService;

    @Mock
    private BookingEventPublisher eventPublisher;

    private BookingServiceImpl bookingService;

    private UUID customerId;
    private UUID organizerId;
    private UUID eventId;
    private UUID vipClassId;
    private UUID gaClassId;

    @BeforeEach
    void setUp() {
        bookingService = new BookingServiceImpl(bookingRepository, ticketRepository, catalogClient, queueClient,
                userClient, seatHoldService, eventPublisher, 600);
        lenient().when(queueClient.checkAccess(any(), any(), any())).thenReturn(new QueueAccessDto(false, true));
        customerId = UUID.randomUUID();
        organizerId = UUID.randomUUID();
        eventId = UUID.randomUUID();
        vipClassId = UUID.randomUUID();
        gaClassId = UUID.randomUUID();
    }

    private CatalogEventDto publishedEvent() {
        return new CatalogEventDto(eventId, organizerId, "Concert ABC", "PUBLISHED", null, null, List.of(
                new CatalogEventDto.TicketClassDto(vipClassId, "VIP", new BigDecimal("1500000"), 200, 200),
                new CatalogEventDto.TicketClassDto(gaClassId, "GA", new BigDecimal("500000"), 2000, 2000)));
    }

    @Test
    void create_succeeds_andPersistsBookingWithLockedTickets() {
        when(catalogClient.getEvent(eventId)).thenReturn(publishedEvent());
        when(bookingRepository.save(any(Booking.class))).thenAnswer(invocation -> {
            Booking booking = invocation.getArgument(0);
            booking.setId(UUID.randomUUID());
            return booking;
        });

        CreateBookingRequest request = new CreateBookingRequest(eventId, List.of(
                new BookingItemRequest(vipClassId, 2)), null);

        BookingResponse response = bookingService.create(customerId, request);

        assertEquals(BookingStatus.PENDING_PAYMENT, response.status());
        assertEquals(new BigDecimal("3000000"), response.totalAmount());
        assertEquals(1, response.items().size());
        assertEquals(2, response.items().getFirst().quantity());

        verify(seatHoldService).hold(eventId, vipClassId, 2, 200);
        verify(seatHoldService).markHeld(eq(eventId), eq(vipClassId), eq(customerId), any(), eq(2), any());
    }

    @Test
    void create_rollsBackEarlierHolds_whenLaterItemFails() {
        when(catalogClient.getEvent(eventId)).thenReturn(publishedEvent());
        doNothing().when(seatHoldService).hold(eventId, vipClassId, 2, 200);
        doThrow(new ConflictException("Hạng vé đã hết vé."))
                .when(seatHoldService).hold(eventId, gaClassId, 5, 2000);

        CreateBookingRequest request = new CreateBookingRequest(eventId, List.of(
                new BookingItemRequest(vipClassId, 2),
                new BookingItemRequest(gaClassId, 5)), null);

        assertThrows(ConflictException.class, () -> bookingService.create(customerId, request));

        verify(seatHoldService).release(eventId, vipClassId, 2);
        verify(bookingRepository, never()).save(any());
    }

    @Test
    void create_throwsResourceNotFound_whenTicketClassNotInEvent() {
        when(catalogClient.getEvent(eventId)).thenReturn(publishedEvent());

        CreateBookingRequest request = new CreateBookingRequest(eventId, List.of(
                new BookingItemRequest(UUID.randomUUID(), 1)), null);

        assertThrows(ResourceNotFoundException.class, () -> bookingService.create(customerId, request));
        verifyNoInteractions(seatHoldService);
    }

    @Test
    void create_throwsConflict_whenSaleNotYetOpen() {
        CatalogEventDto event = new CatalogEventDto(eventId, organizerId, "Concert ABC", "PUBLISHED",
                Instant.now().plusSeconds(3600), Instant.now().plusSeconds(7200), List.of());
        when(catalogClient.getEvent(eventId)).thenReturn(event);

        CreateBookingRequest request = new CreateBookingRequest(eventId, List.of(
                new BookingItemRequest(vipClassId, 1)), null);

        assertThrows(ConflictException.class, () -> bookingService.create(customerId, request));
    }

    @Test
    void getDetail_throwsForbidden_whenNotOwner() {
        Booking booking = Booking.builder().id(UUID.randomUUID()).customerId(UUID.randomUUID())
                .eventId(eventId).status(BookingStatus.PENDING_PAYMENT).build();
        when(bookingRepository.findWithTicketsById(booking.getId())).thenReturn(Optional.of(booking));

        assertThrows(ForbiddenException.class, () -> bookingService.getDetail(customerId, booking.getId()));
    }

    @Test
    void cancel_throwsConflict_whenNotPending() {
        Booking booking = Booking.builder().id(UUID.randomUUID()).customerId(customerId)
                .eventId(eventId).status(BookingStatus.PAID).build();
        when(bookingRepository.findWithTicketsById(booking.getId())).thenReturn(Optional.of(booking));

        assertThrows(ConflictException.class, () -> bookingService.cancel(customerId, booking.getId()));
        verify(bookingRepository, never()).cancelIfPending(any());
    }

    @Test
    void cancel_releasesSeatHold_forEachTicketClass() {
        Booking booking = Booking.builder().id(UUID.randomUUID()).customerId(customerId)
                .eventId(eventId).status(BookingStatus.PENDING_PAYMENT).build();
        booking.addTicket(Ticket.builder().ticketClassId(vipClassId).ticketClassName("VIP")
                .unitPrice(new BigDecimal("1500000")).qrCodeData(UUID.randomUUID().toString())
                .status(TicketStatus.LOCKED).build());
        booking.addTicket(Ticket.builder().ticketClassId(vipClassId).ticketClassName("VIP")
                .unitPrice(new BigDecimal("1500000")).qrCodeData(UUID.randomUUID().toString())
                .status(TicketStatus.LOCKED).build());
        when(bookingRepository.findWithTicketsById(booking.getId())).thenReturn(Optional.of(booking));
        when(bookingRepository.cancelIfPending(booking.getId())).thenReturn(1);

        bookingService.cancel(customerId, booking.getId());

        verify(seatHoldService).release(eventId, vipClassId, 2);
        verify(seatHoldService).clearHeld(eventId, vipClassId, customerId);
        assertEquals(BookingStatus.CANCELLED, booking.getStatus());
        assertTrue(booking.getTickets().stream().allMatch(t -> t.getStatus() == TicketStatus.CANCELLED));
    }

    @Test
    void releaseExpiredBooking_isNoop_whenAlreadyProcessedByAnotherPath() {
        Booking booking = Booking.builder().id(UUID.randomUUID()).customerId(customerId)
                .eventId(eventId).status(BookingStatus.PENDING_PAYMENT).build();
        when(bookingRepository.findWithTicketsById(booking.getId())).thenReturn(Optional.of(booking));
        when(bookingRepository.cancelIfPending(booking.getId())).thenReturn(0);

        bookingService.releaseExpiredBooking(booking.getId());

        verifyNoInteractions(seatHoldService);
    }

    @Test
    void confirmPayment_marksPaidAndIssuesTickets_thenPublishesTicketsGenerated() {
        Booking booking = Booking.builder().id(UUID.randomUUID()).customerId(customerId)
                .eventId(eventId).status(BookingStatus.PENDING_PAYMENT).build();
        booking.addTicket(Ticket.builder().ticketClassId(vipClassId).ticketClassName("VIP")
                .unitPrice(new BigDecimal("1500000")).qrCodeData(UUID.randomUUID().toString())
                .status(TicketStatus.LOCKED).build());
        booking.addTicket(Ticket.builder().ticketClassId(vipClassId).ticketClassName("VIP")
                .unitPrice(new BigDecimal("1500000")).qrCodeData(UUID.randomUUID().toString())
                .status(TicketStatus.LOCKED).build());
        when(bookingRepository.findWithTicketsById(booking.getId())).thenReturn(Optional.of(booking));
        when(bookingRepository.markPaidIfPending(booking.getId())).thenReturn(1);

        UUID transactionId = UUID.randomUUID();
        bookingService.confirmPayment(booking.getId(), transactionId, new BigDecimal("3000000"), "4123456789");

        assertEquals(BookingStatus.PAID, booking.getStatus());
        assertTrue(booking.getTickets().stream().allMatch(t -> t.getStatus() == TicketStatus.ISSUED));
        verify(seatHoldService).release(eventId, vipClassId, 2);
        verify(seatHoldService).clearHeld(eventId, vipClassId, customerId);
        verify(eventPublisher).publishTicketsGenerated(argThat(event ->
                event.getBookingId().equals(booking.getId())
                        && event.getCatalogEventId().equals(eventId)
                        && event.getItems().size() == 1
                        && event.getItems().getFirst().getQuantity() == 2));
    }

    @Test
    void confirmPayment_requestsRefund_whenBookingNotFound() {
        UUID bookingId = UUID.randomUUID();
        UUID transactionId = UUID.randomUUID();
        when(bookingRepository.findWithTicketsById(bookingId)).thenReturn(Optional.empty());

        bookingService.confirmPayment(bookingId, transactionId, new BigDecimal("3000000"), "4123456789");

        verify(bookingRepository, never()).markPaidIfPending(any());
        verify(eventPublisher).publishRefundRequested(argThat(event ->
                event.getBookingId().equals(bookingId)
                        && event.getTransactionId().equals(transactionId)
                        && event.getGatewayTransId().equals("4123456789")));
        verifyNoInteractions(seatHoldService);
    }

    @Test
    void confirmPayment_requestsRefund_whenBookingAlreadyCancelled() {
        Booking booking = Booking.builder().id(UUID.randomUUID()).customerId(customerId)
                .eventId(eventId).status(BookingStatus.CANCELLED).build();
        UUID transactionId = UUID.randomUUID();
        when(bookingRepository.findWithTicketsById(booking.getId())).thenReturn(Optional.of(booking));

        bookingService.confirmPayment(booking.getId(), transactionId, new BigDecimal("3000000"), "4123456789");

        verify(bookingRepository, never()).markPaidIfPending(any());
        verify(eventPublisher).publishRefundRequested(any(BookingRefundRequestedEvent.class));
        verifyNoInteractions(seatHoldService);
    }

    @Test
    void confirmPayment_isNoop_whenAlreadyProcessedByAnotherPath() {
        Booking booking = Booking.builder().id(UUID.randomUUID()).customerId(customerId)
                .eventId(eventId).status(BookingStatus.PENDING_PAYMENT).build();
        when(bookingRepository.findWithTicketsById(booking.getId())).thenReturn(Optional.of(booking));
        when(bookingRepository.markPaidIfPending(booking.getId())).thenReturn(0);

        bookingService.confirmPayment(booking.getId(), UUID.randomUUID(), new BigDecimal("3000000"), "4123456789");

        verifyNoInteractions(eventPublisher, seatHoldService);
    }

    @Test
    void markRefunded_isNoop_whenBookingNotFound() {
        UUID bookingId = UUID.randomUUID();
        when(bookingRepository.findWithTicketsById(bookingId)).thenReturn(Optional.empty());

        bookingService.markRefunded(bookingId);

        verify(bookingRepository, never()).markRefundedIfNotAlready(any());
        verifyNoInteractions(seatHoldService);
    }

    @Test
    void markRefunded_isIdempotent_whenAlreadyRefunded() {
        Booking booking = Booking.builder().id(UUID.randomUUID()).customerId(customerId)
                .eventId(eventId).status(BookingStatus.REFUNDED).build();
        when(bookingRepository.findWithTicketsById(booking.getId())).thenReturn(Optional.of(booking));
        when(bookingRepository.markRefundedIfNotAlready(booking.getId())).thenReturn(0);

        bookingService.markRefunded(booking.getId());

        verifyNoInteractions(seatHoldService);
    }

    @Test
    void markRefunded_flipsCancelledBookingToRefunded_withoutReReleasingSeatHold() {
        Booking booking = Booking.builder().id(UUID.randomUUID()).customerId(customerId)
                .eventId(eventId).status(BookingStatus.CANCELLED).build();
        when(bookingRepository.findWithTicketsById(booking.getId())).thenReturn(Optional.of(booking));
        when(bookingRepository.markRefundedIfNotAlready(booking.getId())).thenReturn(1);

        bookingService.markRefunded(booking.getId());

        assertEquals(BookingStatus.REFUNDED, booking.getStatus());
        verifyNoInteractions(seatHoldService);
    }

    @Test
    void markRefunded_releasesSeatHold_whenStillPendingPayment() {
        Booking booking = Booking.builder().id(UUID.randomUUID()).customerId(customerId)
                .eventId(eventId).status(BookingStatus.PENDING_PAYMENT).build();
        booking.addTicket(Ticket.builder().ticketClassId(vipClassId).ticketClassName("VIP")
                .unitPrice(new BigDecimal("1500000")).qrCodeData(UUID.randomUUID().toString())
                .status(TicketStatus.LOCKED).build());
        when(bookingRepository.findWithTicketsById(booking.getId())).thenReturn(Optional.of(booking));
        when(bookingRepository.markRefundedIfNotAlready(booking.getId())).thenReturn(1);

        bookingService.markRefunded(booking.getId());

        assertEquals(BookingStatus.REFUNDED, booking.getStatus());
        assertTrue(booking.getTickets().stream().allMatch(t -> t.getStatus() == TicketStatus.CANCELLED));
        verify(seatHoldService).release(eventId, vipClassId, 1);
        verify(seatHoldService).clearHeld(eventId, vipClassId, customerId);
    }

    @Test
    void checkIn_success_transitionsTicketAndReturnsDetails() {
        Booking booking = Booking.builder().id(UUID.randomUUID()).customerId(customerId)
                .eventId(eventId).status(BookingStatus.PAID).build();
        Ticket ticket = Ticket.builder().id(UUID.randomUUID()).ticketClassId(vipClassId).ticketClassName("VIP")
                .unitPrice(new BigDecimal("1500000")).qrCodeData("qr-abc").status(TicketStatus.ISSUED).build();
        booking.addTicket(ticket);

        when(ticketRepository.findByQrCodeData("qr-abc")).thenReturn(Optional.of(ticket));
        when(catalogClient.getEvent(eventId)).thenReturn(publishedEventWithTitle("Concert ABC"));
        when(ticketRepository.checkInIfIssued(eq(ticket.getId()), any())).thenReturn(1);
        when(userClient.findFullName(customerId)).thenReturn("Nguyễn Văn A");

        CheckInResponse response = bookingService.checkIn(organizerId, new CheckInRequest("qr-abc"));

        assertEquals(ticket.getId(), response.ticketId());
        assertEquals("VIP", response.ticketClass());
        assertEquals("Concert ABC", response.eventTitle());
        assertEquals("Nguyễn Văn A", response.customerName());
        assertEquals("CHECKED_IN", response.status());
    }

    @Test
    void checkIn_throwsResourceNotFound_whenQrCodeUnknown() {
        when(ticketRepository.findByQrCodeData("bad-qr")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> bookingService.checkIn(organizerId, new CheckInRequest("bad-qr")));
    }

    @Test
    void checkIn_throwsForbidden_whenEventBelongsToAnotherOrganizer() {
        Booking booking = Booking.builder().id(UUID.randomUUID()).customerId(customerId)
                .eventId(eventId).status(BookingStatus.PAID).build();
        Ticket ticket = Ticket.builder().id(UUID.randomUUID()).ticketClassId(vipClassId).ticketClassName("VIP")
                .unitPrice(new BigDecimal("1500000")).qrCodeData("qr-abc").status(TicketStatus.ISSUED).build();
        booking.addTicket(ticket);

        when(ticketRepository.findByQrCodeData("qr-abc")).thenReturn(Optional.of(ticket));
        when(catalogClient.getEvent(eventId)).thenReturn(publishedEventWithTitle("Concert ABC"));

        assertThrows(ForbiddenException.class,
                () -> bookingService.checkIn(UUID.randomUUID(), new CheckInRequest("qr-abc")));
        verify(ticketRepository, never()).checkInIfIssued(any(), any());
    }

    @Test
    void checkIn_throwsConflict_whenAlreadyCheckedIn() {
        Booking booking = Booking.builder().id(UUID.randomUUID()).customerId(customerId)
                .eventId(eventId).status(BookingStatus.PAID).build();
        Ticket ticket = Ticket.builder().id(UUID.randomUUID()).ticketClassId(vipClassId).ticketClassName("VIP")
                .unitPrice(new BigDecimal("1500000")).qrCodeData("qr-abc")
                .status(TicketStatus.CHECKED_IN).checkedInAt(Instant.now()).build();
        booking.addTicket(ticket);

        when(ticketRepository.findByQrCodeData("qr-abc")).thenReturn(Optional.of(ticket));
        when(catalogClient.getEvent(eventId)).thenReturn(publishedEventWithTitle("Concert ABC"));

        assertThrows(ConflictException.class, () -> bookingService.checkIn(organizerId, new CheckInRequest("qr-abc")));
        verify(ticketRepository, never()).checkInIfIssued(any(), any());
    }

    @Test
    void checkIn_throwsConflict_whenTicketNotYetIssued() {
        Booking booking = Booking.builder().id(UUID.randomUUID()).customerId(customerId)
                .eventId(eventId).status(BookingStatus.PENDING_PAYMENT).build();
        Ticket ticket = Ticket.builder().id(UUID.randomUUID()).ticketClassId(vipClassId).ticketClassName("VIP")
                .unitPrice(new BigDecimal("1500000")).qrCodeData("qr-abc").status(TicketStatus.LOCKED).build();
        booking.addTicket(ticket);

        when(ticketRepository.findByQrCodeData("qr-abc")).thenReturn(Optional.of(ticket));
        when(catalogClient.getEvent(eventId)).thenReturn(publishedEventWithTitle("Concert ABC"));

        assertThrows(ConflictException.class, () -> bookingService.checkIn(organizerId, new CheckInRequest("qr-abc")));
    }

    @Test
    void checkIn_throwsConflict_onConcurrentDoubleScanRace() {
        Booking booking = Booking.builder().id(UUID.randomUUID()).customerId(customerId)
                .eventId(eventId).status(BookingStatus.PAID).build();
        Ticket ticket = Ticket.builder().id(UUID.randomUUID()).ticketClassId(vipClassId).ticketClassName("VIP")
                .unitPrice(new BigDecimal("1500000")).qrCodeData("qr-abc").status(TicketStatus.ISSUED).build();
        booking.addTicket(ticket);

        when(ticketRepository.findByQrCodeData("qr-abc")).thenReturn(Optional.of(ticket));
        when(catalogClient.getEvent(eventId)).thenReturn(publishedEventWithTitle("Concert ABC"));
        when(ticketRepository.checkInIfIssued(eq(ticket.getId()), any())).thenReturn(0);

        assertThrows(ConflictException.class, () -> bookingService.checkIn(organizerId, new CheckInRequest("qr-abc")));
    }

    @Test
    void getEventReport_computesRevenueAndRates_forOwningOrganizer() {
        when(catalogClient.getEvent(eventId)).thenReturn(publishedEvent());
        when(ticketRepository.aggregateSoldByTicketClass(eventId)).thenReturn(List.of(
                new TicketClassReportRow(vipClassId, "VIP", 150L, 100L, new BigDecimal("225000000")),
                new TicketClassReportRow(gaClassId, "GA", 1000L, 400L, new BigDecimal("500000000"))));

        EventReportResponse report = bookingService.getEventReport(eventId, organizerId);

        assertEquals(new BigDecimal("725000000"), report.totalRevenue());
        assertEquals(1150L, report.totalTicketsSold());
        assertEquals(2200L, report.totalCapacity());
        assertEquals(500L, report.totalCheckedIn());
        assertEquals(2, report.byTicketClass().size());

        EventReportResponse.TicketClassReport vipReport = report.byTicketClass().stream()
                .filter(r -> r.ticketClassId().equals(vipClassId)).findFirst().orElseThrow();
        assertEquals(150L, vipReport.sold());
        assertEquals(0.75, vipReport.fillRate(), 0.0001);
        assertEquals(100.0 / 150, vipReport.checkInRate(), 0.0001);
    }

    @Test
    void getEventReport_fillsZeroes_forTicketClassWithNoSales() {
        when(catalogClient.getEvent(eventId)).thenReturn(publishedEvent());
        when(ticketRepository.aggregateSoldByTicketClass(eventId)).thenReturn(List.of());

        EventReportResponse report = bookingService.getEventReport(eventId, organizerId);

        assertEquals(BigDecimal.ZERO, report.totalRevenue());
        assertEquals(0L, report.totalTicketsSold());
        assertEquals(0d, report.fillRate());
        assertEquals(0d, report.checkInRate());
    }

    @Test
    void getEventReport_throwsForbidden_whenRequestedByNonOwningOrganizer() {
        when(catalogClient.getEvent(eventId)).thenReturn(publishedEvent());

        assertThrows(ForbiddenException.class, () -> bookingService.getEventReport(eventId, UUID.randomUUID()));
        verifyNoInteractions(ticketRepository);
    }

    @Test
    void getEventReport_bypassesOwnershipCheck_whenOrganizerIdIsNull() {
        when(catalogClient.getEvent(eventId)).thenReturn(publishedEvent());
        when(ticketRepository.aggregateSoldByTicketClass(eventId)).thenReturn(List.of());

        assertDoesNotThrow(() -> bookingService.getEventReport(eventId, null));
    }

    private CatalogEventDto publishedEventWithTitle(String title) {
        return new CatalogEventDto(eventId, organizerId, title, "PUBLISHED", null, null, List.of());
    }
}
