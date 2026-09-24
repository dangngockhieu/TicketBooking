package com.ticketbooking.booking.scheduler;

import com.ticketbooking.booking.entity.Booking;
import com.ticketbooking.booking.enums.BookingStatus;
import com.ticketbooking.booking.repository.BookingRepository;
import com.ticketbooking.booking.service.BookingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/**
 * Nguồn nhả ghế đáng tin cậy nhất —
 * quét trực tiếp Postgres nên không phụ thuộc vào Redis keyspace notification
 * (best-effort, có thể bị bỏ lỡ nếu Redis khởi động lại). Chạy mỗi phút.
 */
@Slf4j
@Component
public class BookingExpirationScheduler {

    private final BookingRepository bookingRepository;
    private final BookingService bookingService;

    public BookingExpirationScheduler(BookingRepository bookingRepository, BookingService bookingService) {
        this.bookingRepository = bookingRepository;
        this.bookingService = bookingService;
    }

    @Scheduled(fixedRate = 60_000)
    public void releaseExpiredBookings() {
        List<Booking> expired = bookingRepository.findByStatusAndExpiredAtBefore(
                BookingStatus.PENDING_PAYMENT, Instant.now());

        for (Booking booking : expired) {
            try {
                bookingService.releaseExpiredBooking(booking.getId());
            } catch (Exception e) {
                log.error("Không thể nhả ghế tự động cho booking {}: {}", booking.getId(), e.getMessage(), e);
            }
        }
    }
}
