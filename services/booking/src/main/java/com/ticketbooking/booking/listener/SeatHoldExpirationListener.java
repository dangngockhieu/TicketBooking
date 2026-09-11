package com.ticketbooking.booking.listener;

import com.ticketbooking.booking.enums.BookingStatus;
import com.ticketbooking.booking.repository.BookingRepository;
import com.ticketbooking.booking.service.BookingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Safety-net nhanh cho nhả ghế: bắt sự kiện Redis keyspace notification
 * {@code expired} của key {@code seat_hold:{event_id}:{ticket_class_id}:{customer_id}}
 * (xem docs/database-schema.md §4, docs/development-plan.md GĐ3 mục 3).
 * <p>
 * Giá trị của key đã mất tại thời điểm nhận notification nên KHÔNG dùng để suy
 * ra số lượng vé — thay vào đó tra lại booking đang PENDING_PAYMENT của
 * customer/event này trong Postgres (nguồn dữ liệu đáng tin cậy), rồi để
 * {@link BookingService#releaseExpiredBooking(UUID)} tự tính lại số lượng từ
 * các dòng {@code tickets} thực tế. Đây chỉ là một đường tăng tốc — Scheduled
 * Job ({@code BookingExpirationScheduler}) vẫn là lưới an toàn chính.
 */
@Slf4j
@Component
public class SeatHoldExpirationListener implements MessageListener {

    private static final String SEAT_HOLD_PREFIX = "seat_hold:";

    private final BookingRepository bookingRepository;
    private final BookingService bookingService;

    public SeatHoldExpirationListener(BookingRepository bookingRepository, BookingService bookingService) {
        this.bookingRepository = bookingRepository;
        this.bookingService = bookingService;
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        String expiredKey = new String(message.getBody(), StandardCharsets.UTF_8);
        if (!expiredKey.startsWith(SEAT_HOLD_PREFIX)) {
            return;
        }

        String[] parts = expiredKey.substring(SEAT_HOLD_PREFIX.length()).split(":");
        if (parts.length != 3) {
            log.warn("seat_hold key không đúng định dạng, bỏ qua: {}", expiredKey);
            return;
        }

        try {
            UUID eventId = UUID.fromString(parts[0]);
            UUID customerId = UUID.fromString(parts[2]);
            bookingRepository.findFirstByCustomerIdAndEventIdAndStatus(customerId, eventId, BookingStatus.PENDING_PAYMENT)
                    .ifPresent(booking -> bookingService.releaseExpiredBooking(booking.getId()));
        } catch (IllegalArgumentException e) {
            log.warn("Không parse được seat_hold key '{}': {}", expiredKey, e.getMessage());
        }
    }
}
