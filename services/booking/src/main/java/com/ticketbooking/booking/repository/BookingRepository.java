package com.ticketbooking.booking.repository;

import com.ticketbooking.booking.entity.Booking;
import com.ticketbooking.booking.enums.BookingStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BookingRepository extends JpaRepository<Booking, UUID> {

    @EntityGraph(attributePaths = "tickets")
    @Query("SELECT b FROM Booking b WHERE b.id = :id")
    Optional<Booking> findWithTicketsById(@Param("id") UUID id);

    Page<Booking> findByCustomerIdAndStatus(UUID customerId, BookingStatus status, Pageable pageable);

    Page<Booking> findByCustomerId(UUID customerId, Pageable pageable);

    /** Dùng bởi scheduled job nhả ghế tự động (xem docs/development-plan.md GĐ3 mục 3). */
    List<Booking> findByStatusAndExpiredAtBefore(BookingStatus status, Instant threshold);

    /** Dùng bởi Redis keyspace notification listener để tra cứu booking từ key {@code seat_hold:*} đã hết hạn. */
    Optional<Booking> findFirstByCustomerIdAndEventIdAndStatus(UUID customerId, UUID eventId, BookingStatus status);

    /**
     * Chuyển sang CANCELLED chỉ khi đang PENDING_PAYMENT — trả về số dòng bị ảnh
     * hưởng (0 hoặc 1) để chống race giữa hủy thủ công, scheduled job và Redis
     * keyspace notification cùng xử lý một booking.
     */
    @Modifying
    @Query("UPDATE Booking b SET b.status = com.ticketbooking.booking.enums.BookingStatus.CANCELLED, "
            + "b.updatedAt = CURRENT_TIMESTAMP WHERE b.id = :id AND b.status = com.ticketbooking.booking.enums.BookingStatus.PENDING_PAYMENT")
    int cancelIfPending(@Param("id") UUID id);

    /**
     * Chuyển sang PAID chỉ khi đang PENDING_PAYMENT — trả về số dòng bị ảnh
     * hưởng (0 hoặc 1) để chống xử lý trùng khi Kafka redeliver
     * {@code payment.success} (xem docs/development-plan.md GĐ4 mục 2).
     */
    @Modifying
    @Query("UPDATE Booking b SET b.status = com.ticketbooking.booking.enums.BookingStatus.PAID, "
            + "b.updatedAt = CURRENT_TIMESTAMP WHERE b.id = :id AND b.status = com.ticketbooking.booking.enums.BookingStatus.PENDING_PAYMENT")
    int markPaidIfPending(@Param("id") UUID id);

    /**
     * Chuyển sang REFUNDED (Saga Compensation hoàn tất, xem docs/api-design.md
     * §5.4) — trừ khi đã REFUNDED, để chống xử lý trùng khi Kafka redeliver
     * {@code payment.refunded}.
     */
    @Modifying
    @Query("UPDATE Booking b SET b.status = com.ticketbooking.booking.enums.BookingStatus.REFUNDED, "
            + "b.updatedAt = CURRENT_TIMESTAMP WHERE b.id = :id AND b.status <> com.ticketbooking.booking.enums.BookingStatus.REFUNDED")
    int markRefundedIfNotAlready(@Param("id") UUID id);
}
