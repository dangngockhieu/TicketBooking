package com.ticketbooking.catalog.service;

import com.ticketbooking.catalog.dto.request.CreateEventRequest;
import com.ticketbooking.catalog.dto.request.EventFeeUpdateRequest;
import com.ticketbooking.catalog.dto.request.EventSearchFilter;
import com.ticketbooking.catalog.dto.request.UpdateEventRequest;
import com.ticketbooking.catalog.dto.response.EventResponse;
import com.ticketbooking.common.dto.PageResponse;
import com.ticketbooking.common.event.TicketsGeneratedEvent;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

public interface EventService {

    /**
     * Tìm kiếm công khai — luôn chỉ trả sự kiện PUBLISHED (xem
     * EventSpecifications).
     */
    PageResponse<EventResponse> search(EventSearchFilter filter, Pageable pageable);

    /** Chi tiết công khai — luôn chỉ trả sự kiện PUBLISHED. */
    EventResponse getPublicDetail(UUID eventId);

    /**
     * {@code image} (tùy chọn) ghi đè {@code request.bannerUrl()} bằng ảnh vừa
     * upload (xem EventImageService).
     */
    EventResponse create(UUID organizerId, CreateEventRequest request, MultipartFile image);

    /**
     * {@code image} (tùy chọn): nếu có, ảnh banner cũ (nếu tồn tại và do chính
     * hệ thống lưu) sẽ bị xóa trước khi lưu ảnh mới, tránh tồn file rác trên
     * đĩa (xem EventImageService#delete).
     */
    EventResponse update(UUID organizerId, UUID eventId, UpdateEventRequest request, MultipartFile image);

    EventResponse publish(UUID organizerId, UUID eventId);

    /**
     * Admin sửa {@code commissionRate}/{@code flatFeePerTicket} riêng cho một
     */
    EventResponse updateFees(UUID eventId, EventFeeUpdateRequest request);

    /**
     * Trừ vĩnh viễn {@code available_quantity} sau khi Booking Service xác
     * nhận thanh toán thành công (Kafka consumer {@code tickets.generated},
     */
    void reduceAvailableQuantity(UUID eventId, UUID ticketClassId, int quantity);

    /**
     * Xử lý toàn bộ {@code tickets.generated} cho một booking — idempotent:
     * nếu {@code bookingId} đã được xử lý trước đó (Kafka redeliver message),
     * bỏ qua toàn bộ để tránh trừ kho 2 lần
     * bảng rủi ro "Event trùng lặp").
     */
    void processTicketsGenerated(TicketsGeneratedEvent event);

    /**
     * Chuyển các sự kiện PUBLISHED đã qua {@code endTime} sang COMPLETED — dùng
     * bởi {@code EventCompletionScheduler}, kích hoạt payout tự động phía
     * Payment Service 7 ngày sau.
     */
    void completeEndedEvents();
}
