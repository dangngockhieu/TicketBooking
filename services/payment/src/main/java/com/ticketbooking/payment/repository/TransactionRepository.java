package com.ticketbooking.payment.repository;

import com.ticketbooking.payment.entity.Transaction;
import com.ticketbooking.payment.enums.TransactionStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

public interface TransactionRepository extends JpaRepository<Transaction, UUID> {

    Optional<Transaction> findByGatewayTransId(String gatewayTransId);

    /** Idempotency khi khởi tạo thanh toán (xem docs/api-design.md §5.1). */
    boolean existsByBookingIdAndStatusIn(UUID bookingId, Collection<TransactionStatus> statuses);
}
