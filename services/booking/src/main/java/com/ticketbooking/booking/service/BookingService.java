package com.ticketbooking.booking.service;

import com.ticketbooking.booking.dto.request.CreateBookingRequest;
import com.ticketbooking.booking.dto.response.BookingResponse;
import com.ticketbooking.booking.enums.BookingStatus;
import com.ticketbooking.common.dto.PageResponse;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface BookingService {

    BookingResponse create(UUID customerId, CreateBookingRequest request);

    /** Chỉ chủ đơn mới được xem — ném ForbiddenException nếu không phải chủ đơn. */
    BookingResponse getDetail(UUID customerId, UUID bookingId);

    PageResponse<BookingResponse> listMine(UUID customerId, BookingStatus status, Pageable pageable);

    /** Hủy đơn theo yêu cầu khách hàng — chỉ khi đang PENDING_PAYMENT. */
    void cancel(UUID customerId, UUID bookingId);

    /**
     * Nhả ghế hệ thống (hết hạn giữ chỗ) — dùng bởi
     * {@code BookingExpirationScheduler} và Redis keyspace notification
     * listener, KHÔNG kiểm tra quyền sở hữu. No-op nếu booking không còn ở
     * trạng thái PENDING_PAYMENT (đã được xử lý bởi nhánh khác).
     */
    void releaseExpiredBooking(UUID bookingId);
}
