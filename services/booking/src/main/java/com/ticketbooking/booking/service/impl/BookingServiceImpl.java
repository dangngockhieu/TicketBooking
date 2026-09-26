package com.ticketbooking.booking.service.impl;

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
import com.ticketbooking.booking.dto.response.TicketClassReportRow;
import com.ticketbooking.booking.entity.Booking;
import com.ticketbooking.booking.entity.Ticket;
import com.ticketbooking.booking.enums.BookingStatus;
import com.ticketbooking.booking.enums.TicketStatus;
import com.ticketbooking.booking.event.BookingEventPublisher;
import com.ticketbooking.booking.repository.BookingRepository;
import com.ticketbooking.booking.repository.TicketRepository;
import com.ticketbooking.booking.service.BookingService;
import com.ticketbooking.booking.service.SeatHoldService;
import com.ticketbooking.common.dto.PageResponse;
import com.ticketbooking.common.event.BookingRefundRequestedEvent;
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
import java.util.stream.Collectors;

@Slf4j
@Service
@Transactional
public class BookingServiceImpl implements BookingService {

    private final BookingRepository bookingRepository;
    private final TicketRepository ticketRepository;
    private final CatalogClient catalogClient;
    private final QueueClient queueClient;
    private final UserClient userClient;
    private final SeatHoldService seatHoldService;
    private final BookingEventPublisher eventPublisher;
    private final long holdTtlSeconds;

    public BookingServiceImpl(
            BookingRepository bookingRepository,
            TicketRepository ticketRepository,
            CatalogClient catalogClient,
            QueueClient queueClient,
            UserClient userClient,
            SeatHoldService seatHoldService,
            BookingEventPublisher eventPublisher,
            @Value("${booking.hold.ttl-seconds:600}") long holdTtlSeconds) {
        this.bookingRepository = bookingRepository;
        this.ticketRepository = ticketRepository;
        this.catalogClient = catalogClient;
        this.queueClient = queueClient;
        this.userClient = userClient;
        this.seatHoldService = seatHoldService;
        this.eventPublisher = eventPublisher;
        this.holdTtlSeconds = holdTtlSeconds;
    }

    @Override
    public BookingResponse create(UUID customerId, CreateBookingRequest request) {
        CatalogEventDto event = catalogClient.getEvent(request.eventId());
        requireQueueAccess(request.eventId(), customerId, request.queueAccessToken());

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
    public void confirmPayment(UUID bookingId, UUID transactionId, BigDecimal amount, String gatewayTransId) {
        Booking booking = bookingRepository.findWithTicketsById(bookingId).orElse(null);
        if (booking == null) {
            log.error("Không tìm thấy booking {} dù đã thanh toán thành công — yêu cầu hoàn tiền.", bookingId);
            requestRefund(bookingId, transactionId, amount, gatewayTransId,
                    "Không tìm thấy booking " + bookingId);
            return;
        }
        if (booking.getStatus() == BookingStatus.CANCELLED) {
            // Saga Compensation (xem docs/api-design.md §5.4): khách đã thanh toán
            // nhưng booking đã bị auto-release do hết hạn giữ chỗ (race hiếm) — yêu
            // cầu Payment Service hoàn tiền qua MoMo thay vì chỉ log cảnh báo.
            log.error("Booking {} đã thanh toán thành công nhưng đã bị hủy do hết hạn giữ chỗ trước đó — yêu cầu hoàn tiền.",
                    bookingId);
            requestRefund(bookingId, transactionId, amount, gatewayTransId,
                    "Booking đã bị hủy do hết hạn giữ chỗ trước khi thanh toán được xác nhận");
            return;
        }

        int affected = bookingRepository.markPaidIfPending(bookingId);
        if (affected == 0) {
            log.info("Booking {} không còn PENDING_PAYMENT (hiện tại: {}), bỏ qua payment.success trùng lặp.",
                    bookingId, booking.getStatus());
            return;
        }
        booking.setStatus(BookingStatus.PAID);

        Map<UUID, Integer> quantityByTicketClass = transitionLockedTickets(booking, TicketStatus.ISSUED);
        bookingRepository.save(booking);
        releaseSeatHold(booking, quantityByTicketClass);

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

    @Override
    public void markRefunded(UUID bookingId) {
        Booking booking = bookingRepository.findWithTicketsById(bookingId).orElse(null);
        if (booking == null) {
            log.warn("Không tìm thấy booking {} khi xử lý payment.refunded, bỏ qua.", bookingId);
            return;
        }

        BookingStatus previousStatus = booking.getStatus();
        int affected = bookingRepository.markRefundedIfNotAlready(bookingId);
        if (affected == 0) {
            log.info("Booking {} đã REFUNDED trước đó, bỏ qua payment.refunded trùng lặp.", bookingId);
            return;
        }
        booking.setStatus(BookingStatus.REFUNDED);

        if (previousStatus == BookingStatus.PENDING_PAYMENT) {
            // confirmPayment chưa từng kịp nhả ghế (lỗi hệ thống giữa chừng trước khi
            // release() được gọi) — release nốt vé/Redis seat hold tại đây.
            Map<UUID, Integer> quantityByTicketClass = transitionLockedTickets(booking, TicketStatus.CANCELLED);
            releaseSeatHold(booking, quantityByTicketClass);
        }
        bookingRepository.save(booking);
    }

    @Override
    public CheckInResponse checkIn(UUID organizerId, CheckInRequest request) {
        Ticket ticket = ticketRepository.findByQrCodeData(request.qrCodeData())
                .orElseThrow(() -> new ResourceNotFoundException("Mã QR không hợp lệ."));
        Booking booking = ticket.getBooking();

        CatalogEventDto event = catalogClient.getEvent(booking.getEventId());
        if (!event.organizerId().equals(organizerId)) {
            throw new ForbiddenException("Vé này không thuộc sự kiện của bạn.");
        }

        if (ticket.getStatus() == TicketStatus.CHECKED_IN) {
            throw new ConflictException("Vé đã được check-in trước đó.");
        }
        if (ticket.getStatus() != TicketStatus.ISSUED) {
            throw new ConflictException("Vé chưa được phát hành (chưa thanh toán).");
        }

        Instant checkedInAt = Instant.now();
        int affected = ticketRepository.checkInIfIssued(ticket.getId(), checkedInAt);
        if (affected == 0) {
            // Quét trùng gần như đồng thời (race hiếm) — nhánh khác đã thắng giữa lúc đọc và ghi ở trên.
            throw new ConflictException("Vé đã được check-in trước đó.");
        }

        String customerName = userClient.findFullName(booking.getCustomerId());
        return new CheckInResponse(ticket.getId(), ticket.getTicketClassName(), event.title(), customerName,
                TicketStatus.CHECKED_IN.name(), checkedInAt);
    }

    @Override
    @Transactional(readOnly = true)
    public EventReportResponse getEventReport(UUID eventId, UUID organizerId) {
        CatalogEventDto event = catalogClient.getEvent(eventId);
        if (organizerId != null && !event.organizerId().equals(organizerId)) {
            throw new ForbiddenException("Sự kiện này không thuộc quyền quản lý của bạn.");
        }

        Map<UUID, TicketClassReportRow> soldByClass = ticketRepository.aggregateSoldByTicketClass(eventId).stream()
                .collect(Collectors.toMap(TicketClassReportRow::ticketClassId, row -> row));

        List<EventReportResponse.TicketClassReport> classReports = event.ticketClasses().stream()
                .map(tc -> buildTicketClassReport(tc, soldByClass.get(tc.id())))
                .toList();

        BigDecimal totalRevenue = classReports.stream()
                .map(EventReportResponse.TicketClassReport::revenue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        long totalSold = classReports.stream().mapToLong(EventReportResponse.TicketClassReport::sold).sum();
        long totalCapacity = classReports.stream().mapToLong(EventReportResponse.TicketClassReport::totalQuantity).sum();
        long totalCheckedIn = classReports.stream().mapToLong(EventReportResponse.TicketClassReport::checkedIn).sum();

        return new EventReportResponse(
                eventId, event.title(), totalRevenue, totalSold, totalCapacity,
                rate(totalSold, totalCapacity), totalCheckedIn, rate(totalCheckedIn, totalSold),
                classReports);
    }

    private EventReportResponse.TicketClassReport buildTicketClassReport(
            CatalogEventDto.TicketClassDto ticketClass, TicketClassReportRow row) {
        long totalQuantity = ticketClass.totalQuantity() != null ? ticketClass.totalQuantity() : 0L;
        long sold = row != null ? row.soldCount() : 0L;
        long checkedIn = row != null ? row.checkedInCount() : 0L;
        BigDecimal revenue = row != null ? row.revenue() : BigDecimal.ZERO;
        return new EventReportResponse.TicketClassReport(
                ticketClass.id(), ticketClass.name(), totalQuantity, sold, checkedIn, revenue,
                rate(sold, totalQuantity), rate(checkedIn, sold));
    }

    private static double rate(long numerator, long denominator) {
        return denominator > 0 ? (double) numerator / denominator : 0d;
    }

    private void requestRefund(UUID bookingId, UUID transactionId, BigDecimal amount, String gatewayTransId, String reason) {
        eventPublisher.publishRefundRequested(BookingRefundRequestedEvent.builder()
                .eventType("booking.refund-requested")
                .bookingId(bookingId)
                .transactionId(transactionId)
                .amount(amount)
                .gatewayTransId(gatewayTransId)
                .reason(reason)
                .build());
    }

    /**
     * Khi phòng chờ ảo đang bật cho sự kiện, chặn tạo đơn nếu thiếu/sai
     * {@code queueAccessToken} (xem docs/virtual-waiting-room.md §8) — bỏ qua
     * hoàn toàn nếu Queue Service không phản hồi được (tính năng phụ trợ,
     * xem {@link QueueClient#checkAccess}).
     */
    private void requireQueueAccess(UUID eventId, UUID customerId, String queueAccessToken) {
        QueueAccessDto access = queueClient.checkAccess(eventId, customerId, queueAccessToken);
        if (access.enabled() && !access.valid()) {
            throw new ForbiddenException(
                    "Token không hợp lệ hoặc đã hết hạn. Vui lòng quay lại hàng chờ.");
        }
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

        Map<UUID, Integer> quantityByTicketClass = transitionLockedTickets(booking, TicketStatus.CANCELLED);
        bookingRepository.save(booking);
        releaseSeatHold(booking, quantityByTicketClass);
    }

    /** Chuyển các vé đang LOCKED sang {@code newStatus}, trả về số lượng đã chuyển theo từng hạng vé. */
    private Map<UUID, Integer> transitionLockedTickets(Booking booking, TicketStatus newStatus) {
        Map<UUID, Integer> quantityByTicketClass = new LinkedHashMap<>();
        for (Ticket ticket : booking.getTickets()) {
            if (ticket.getStatus() == TicketStatus.LOCKED) {
                ticket.setStatus(newStatus);
                quantityByTicketClass.merge(ticket.getTicketClassId(), 1, Integer::sum);
            }
        }
        return quantityByTicketClass;
    }

    private void releaseSeatHold(Booking booking, Map<UUID, Integer> quantityByTicketClass) {
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
