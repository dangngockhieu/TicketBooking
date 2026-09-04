package com.ticketbooking.booking.repository;

import com.ticketbooking.booking.entity.Booking;
import com.ticketbooking.booking.enums.BookingStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BookingRepository extends JpaRepository<Booking, UUID> {

    @EntityGraph(attributePaths = "tickets")
    @Query("select b from Booking b where b.id = :id")
    Optional<Booking> findWithTicketsById(@Param("id") UUID id);

    Page<Booking> findByCustomerIdAndStatus(UUID customerId, BookingStatus status, Pageable pageable);

    Page<Booking> findByCustomerId(UUID customerId, Pageable pageable);

    /** Dùng bởi scheduled job nhả ghế tự động (xem docs/development-plan.md GĐ3 mục 3). */
    List<Booking> findByStatusAndExpiredAtBefore(BookingStatus status, Instant threshold);
}
