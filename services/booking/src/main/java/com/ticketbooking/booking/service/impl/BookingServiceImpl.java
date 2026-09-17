package com.ticketbooking.booking.service.impl;

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
import com.ticketbooking.booking.service.BookingService;
import com.ticketbooking.booking.service.SeatHoldService;
import com.ticketbooking.common.dto.PageResponse;
import com.ticketbooking.common.event.TicketsGeneratedEvent;
import com.ticketbooking.common.exception.ConflictException;
import com.ticketbooking.common.exception.ForbiddenException;
import com.ticketbooking.common.exception.ResourceNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@Transactional
public class BookingServiceImpl implements BookingService {

    private final BookingRepository bookingRepository;
    private final CatalogClient catalogClient;
    private final SeatHoldService seatHoldService;
    private final BookingEventPublisher eventPublisher;
    private final long holdTtlSeconds;

    public BookingServiceImpl(
            BookingRepository bookingRepository,
            CatalogClient catalogClient,
            SeatHoldService seatHoldService,
            BookingEventPublisher eventPublisher,
            @Value("${booking.hold.ttl-seconds:600}") long holdTtlSeconds) {
        this.bookingRepository = bookingRepository;
        this.catalogClient = catalogClient;
        this.seatHoldService = seatHoldService;
        this.eventPublisher = eventPublisher;
        this.holdTtlSeconds = holdTtlSeconds;
    }

    @Override
    public BookingResponse create(UUID customerId, CreateBookingRequest request) {
        CatalogEventDto event = catalogClient.getEvent(request.eventId());

        Instant now = Instant.now();
        if (event.saleStartTime() != null && now.isBefore(event.saleStartTime())) {
            throw new ConflictException("Sự kiện chưa mở bán vé.");
        }
        if (event.saleEndTime() != null && now.isAfter(event.saleEndTime())) {
            throw new ConflictException("Sự kiện đã đóng bán vé.");
        }

        List<ResolvedItem> resolvedItems = resolveItems(event, request.items());

        List<ResolvedItem> held = new ArrayList<>();
        try {
            for (ResolvedItem item : resolvedItems) {
                seatHoldService.hold(event.id(), item.ticketClass().id(), item.quantity(),
                        item.ticketClass().availableQuantity());
                held.add(item);
            }
        } catch (RuntimeException ex) {
            releaseHeld(event.id(), held);
            throw ex;
        }

        try {
            Booking booking = buildBooking(customerId, event.id(), resolvedItems, now);
            Booking saved = bookingRepository.save(booking);

            for (ResolvedItem item : resolvedItems) {
                seatHoldService.markHeld(event.id(), item.ticketClass().id(), customerId, saved.getId(),
                        item.quantity(), Duration.ofSeconds(holdTtlSeconds));
            }
            return BookingResponse.from(saved);
        } catch (RuntimeException ex) {
            releaseHeld(event.id(), resolvedItems);
            throw ex;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public BookingResponse getDetail(UUID customerId, UUID bookingId) {
        Booking booking = bookingRepository.findWithTicketsById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn hàng."));
        requireOwner(booking, customerId);
        return BookingResponse.from(booking);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<BookingResponse> listMine(UUID customerId, BookingStatus status, Pageable pageable) {
        Page<Booking> page = status != null
                ? bookingRepository.findByCustomerIdAndStatus(customerId, status, pageable)
                : bookingRepository.findByCustomerId(customerId, pageable);

        return PageResponse.<BookingResponse>builder()
                .items(page.getContent().stream().map(BookingResponse::from).toList())
                .page(page.getNumber() + 1)
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .hasNext(page.hasNext())
                .hasPrevious(page.hasPrevious())
                .build();
    }

    @Override
    public void cancel(UUID customerId, UUID bookingId) {
        Booking booking = bookingRepository.findWithTicketsById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn hàng."));
        requireOwner(booking, customerId);
        if (booking.getStatus() != BookingStatus.PENDING_PAYMENT) {
            throw new ConflictException("Chỉ có thể hủy đơn khi đang chờ thanh toán.");
        }
        release(booking);
    }

    @Override
    public void releaseExpiredBooking(UUID bookingId) {
        bookingRepository.findWithTicketsById(bookingId).ifPresent(this::release);
    }

    @Override
    public void confirmPayment(UUID bookingId) {
        Booking booking = bookingRepository.findWithTicketsById(bookingId).orElse(null);
        if (booking == null) {
            log.warn("Không tìm thấy booking {} khi xử lý payment.success, bỏ qua.", bookingId);
            return;
        }
        if (booking.getStatus() == BookingStatus.CANCELLED) {
            // TODO(GĐ4 Saga): khách đã thanh toán nhưng booking đã bị auto-release do
            // hết hạn giữ chỗ (race hiếm) — cần bắn booking.refund-requested để Payment
            // Service tự động hoàn tiền qua MoMo. Chưa triển khai, log để theo dõi thủ công.
            log.error("Booking {} đã thanh toán thành công nhưng đã bị CANCELLED trước đó — cần hoàn tiền thủ công.",
                    bookingId);
            return;
        }

        int affected = bookingRepository.markPaidIfPending(bookingId);
        if (affected == 0) {
            log.info("Booking {} không còn PENDING_PAYMENT (hiện tại: {}), bỏ qua payment.success trùng lặp.",
                    bookingId, booking.getStatus());
            return;
        }
        booking.setStatus(BookingStatus.PAID);

        Map<UUID, Integer> quantityByTicketClass = new LinkedHashMap<>();
        for (Ticket ticket : booking.getTickets()) {
            if (ticket.getStatus() == TicketStatus.LOCKED) {
                ticket.setStatus(TicketStatus.ISSUED);
                quantityByTicketClass.merge(ticket.getTicketClassId(), 1, Integer::sum);
            }
        }
        bookingRepository.save(booking);

        quantityByTicketClass.forEach((ticketClassId, quantity) -> {
            seatHoldService.release(booking.getEventId(), ticketClassId, quantity);
            seatHoldService.clearHeld(booking.getEventId(), ticketClassId, booking.getCustomerId());
        });

        List<TicketsGeneratedEvent.TicketClassQuantity> items = quantityByTicketClass.entrySet().stream()
                .map(entry -> TicketsGeneratedEvent.TicketClassQuantity.builder()
                        .ticketClassId(entry.getKey())
                        .quantity(entry.getValue())
                        .build())
                .toList();
        eventPublisher.publishTicketsGenerated(TicketsGeneratedEvent.builder()
                .eventType("tickets.generated")
                .bookingId(booking.getId())
                .catalogEventId(booking.getEventId())
                .items(items)
                .build());
    }

    private List<ResolvedItem> resolveItems(CatalogEventDto event, List<BookingItemRequest> items) {
        List<ResolvedItem> resolved = new ArrayList<>();
        for (BookingItemRequest item : items) {
            CatalogEventDto.TicketClassDto ticketClass = event.ticketClasses().stream()
                    .filter(tc -> tc.id().equals(item.ticketClassId()))
                    .findFirst()
                    .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy hạng vé."));
            resolved.add(new ResolvedItem(ticketClass, item.quantity()));
        }
        return resolved;
    }

    private Booking buildBooking(UUID customerId, UUID eventId, List<ResolvedItem> items, Instant now) {
        Booking booking = Booking.builder()
                .customerId(customerId)
                .eventId(eventId)
                .status(BookingStatus.PENDING_PAYMENT)
                .expiredAt(now.plusSeconds(holdTtlSeconds))
                .build();

        BigDecimal totalAmount = BigDecimal.ZERO;
        int totalQuantity = 0;
        for (ResolvedItem item : items) {
            for (int i = 0; i < item.quantity(); i++) {
                booking.addTicket(Ticket.builder()
                        .ticketClassId(item.ticketClass().id())
                        .ticketClassName(item.ticketClass().name())
                        .unitPrice(item.ticketClass().price())
                        .qrCodeData(UUID.randomUUID().toString())
                        .status(TicketStatus.LOCKED)
                        .build());
            }
            totalAmount = totalAmount.add(item.ticketClass().price().multiply(BigDecimal.valueOf(item.quantity())));
            totalQuantity += item.quantity();
        }
        booking.setTotalAmount(totalAmount);
        booking.setQuantity(totalQuantity);
        return booking;
    }

    private void releaseHeld(UUID eventId, List<ResolvedItem> held) {
        for (ResolvedItem item : held) {
            seatHoldService.release(eventId, item.ticketClass().id(), item.quantity());
        }
    }

    /**
     * Chuyển booking sang CANCELLED (guard atomic qua {@code cancelIfPending} —
     * an toàn khi bị gọi trùng bởi cancel thủ công, scheduled job và Redis
     * keyspace notification listener) rồi trả lại vé đã giữ trên Redis.
     */
    private void release(Booking booking) {
        int affected = bookingRepository.cancelIfPending(booking.getId());
        if (affected == 0) {
            return;
        }
        booking.setStatus(BookingStatus.CANCELLED);

        Map<UUID, Integer> quantityByTicketClass = new LinkedHashMap<>();
        for (Ticket ticket : booking.getTickets()) {
            if (ticket.getStatus() == TicketStatus.LOCKED) {
                ticket.setStatus(TicketStatus.CANCELLED);
                quantityByTicketClass.merge(ticket.getTicketClassId(), 1, Integer::sum);
            }
        }
        bookingRepository.save(booking);

        quantityByTicketClass.forEach((ticketClassId, quantity) -> {
            seatHoldService.release(booking.getEventId(), ticketClassId, quantity);
            seatHoldService.clearHeld(booking.getEventId(), ticketClassId, booking.getCustomerId());
        });
    }

    private void requireOwner(Booking booking, UUID customerId) {
        if (!booking.getCustomerId().equals(customerId)) {
            throw new ForbiddenException("Bạn không có quyền thao tác trên đơn hàng này.");
        }
    }

    private record ResolvedItem(CatalogEventDto.TicketClassDto ticketClass, int quantity) {
    }
}
