package com.ticketbooking.booking.service;

import com.ticketbooking.booking.client.CatalogClient;
import com.ticketbooking.booking.client.dto.CatalogEventDto;
import com.ticketbooking.booking.dto.request.BookingItemRequest;
import com.ticketbooking.booking.dto.request.CreateBookingRequest;
import com.ticketbooking.booking.dto.response.BookingResponse;
import com.ticketbooking.booking.entity.Booking;
import com.ticketbooking.booking.entity.Ticket;
import com.ticketbooking.booking.enums.BookingStatus;
import com.ticketbooking.booking.enums.TicketStatus;
import com.ticketbooking.booking.event.BookingEventPublisher;
import com.ticketbooking.booking.repository.BookingRepository;
import com.ticketbooking.booking.service.impl.BookingServiceImpl;
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
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BookingServiceImplTest {

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private CatalogClient catalogClient;

    @Mock
    private SeatHoldService seatHoldService;

    @Mock
    private BookingEventPublisher eventPublisher;

    private BookingServiceImpl bookingService;

    private UUID customerId;
    private UUID eventId;
    private UUID vipClassId;
    private UUID gaClassId;

    @BeforeEach
    void setUp() {
        bookingService = new BookingServiceImpl(bookingRepository, catalogClient, seatHoldService, eventPublisher, 600);
        customerId = UUID.randomUUID();
        eventId = UUID.randomUUID();
        vipClassId = UUID.randomUUID();
        gaClassId = UUID.randomUUID();
    }

    private CatalogEventDto publishedEvent() {
        return new CatalogEventDto(eventId, "PUBLISHED", null, null, List.of(
                new CatalogEventDto.TicketClassDto(vipClassId, "VIP", new BigDecimal("1500000"), 200),
                new CatalogEventDto.TicketClassDto(gaClassId, "GA", new BigDecimal("500000"), 2000)));
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
                new BookingItemRequest(vipClassId, 2)));

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
                new BookingItemRequest(gaClassId, 5)));

        assertThrows(ConflictException.class, () -> bookingService.create(customerId, request));

        verify(seatHoldService).release(eventId, vipClassId, 2);
        verify(bookingRepository, never()).save(any());
    }

    @Test
    void create_throwsResourceNotFound_whenTicketClassNotInEvent() {
        when(catalogClient.getEvent(eventId)).thenReturn(publishedEvent());

        CreateBookingRequest request = new CreateBookingRequest(eventId, List.of(
                new BookingItemRequest(UUID.randomUUID(), 1)));

        assertThrows(ResourceNotFoundException.class, () -> bookingService.create(customerId, request));
        verifyNoInteractions(seatHoldService);
    }

    @Test
    void create_throwsConflict_whenSaleNotYetOpen() {
        CatalogEventDto event = new CatalogEventDto(eventId, "PUBLISHED",
                Instant.now().plusSeconds(3600), Instant.now().plusSeconds(7200), List.of());
        when(catalogClient.getEvent(eventId)).thenReturn(event);

        CreateBookingRequest request = new CreateBookingRequest(eventId, List.of(
                new BookingItemRequest(vipClassId, 1)));

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

        bookingService.confirmPayment(booking.getId());

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
    void confirmPayment_isNoop_whenBookingNotFound() {
        UUID bookingId = UUID.randomUUID();
        when(bookingRepository.findWithTicketsById(bookingId)).thenReturn(Optional.empty());

        bookingService.confirmPayment(bookingId);

        verify(bookingRepository, never()).markPaidIfPending(any());
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void confirmPayment_logsAndSkips_whenBookingAlreadyCancelled() {
        Booking booking = Booking.builder().id(UUID.randomUUID()).customerId(customerId)
                .eventId(eventId).status(BookingStatus.CANCELLED).build();
        when(bookingRepository.findWithTicketsById(booking.getId())).thenReturn(Optional.of(booking));

        bookingService.confirmPayment(booking.getId());

        verify(bookingRepository, never()).markPaidIfPending(any());
        verifyNoInteractions(eventPublisher, seatHoldService);
    }

    @Test
    void confirmPayment_isNoop_whenAlreadyProcessedByAnotherPath() {
        Booking booking = Booking.builder().id(UUID.randomUUID()).customerId(customerId)
                .eventId(eventId).status(BookingStatus.PENDING_PAYMENT).build();
        when(bookingRepository.findWithTicketsById(booking.getId())).thenReturn(Optional.of(booking));
        when(bookingRepository.markPaidIfPending(booking.getId())).thenReturn(0);

        bookingService.confirmPayment(booking.getId());

        verifyNoInteractions(eventPublisher, seatHoldService);
    }
}
