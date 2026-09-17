package com.ticketbooking.catalog.service;

import com.ticketbooking.catalog.dto.request.CreateEventRequest;
import com.ticketbooking.catalog.dto.request.EventSearchFilter;
import com.ticketbooking.catalog.dto.request.UpdateEventRequest;
import com.ticketbooking.catalog.dto.response.EventResponse;
import com.ticketbooking.common.dto.PageResponse;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface EventService {

    /** Tìm kiếm công khai — luôn chỉ trả sự kiện PUBLISHED (xem EventSpecifications). */
    PageResponse<EventResponse> search(EventSearchFilter filter, Pageable pageable);

    /** Chi tiết công khai — luôn chỉ trả sự kiện PUBLISHED. */
    EventResponse getPublicDetail(UUID eventId);

    EventResponse create(UUID organizerId, CreateEventRequest request);

    EventResponse update(UUID organizerId, UUID eventId, UpdateEventRequest request);

    EventResponse publish(UUID organizerId, UUID eventId);

    /**
     * Trừ vĩnh viễn {@code available_quantity} sau khi Booking Service xác
     * nhận thanh toán thành công (Kafka consumer {@code tickets.generated},
     * xem docs/development-plan.md GĐ4 mục 2) và invalidate cache liên quan.
     */
    void reduceAvailableQuantity(UUID eventId, UUID ticketClassId, int quantity);
}
