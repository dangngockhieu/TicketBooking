package com.ticketbooking.payment.repository;

import com.ticketbooking.payment.entity.Transaction;
import com.ticketbooking.payment.enums.TransactionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TransactionRepository extends JpaRepository<Transaction, UUID> {

    Optional<Transaction> findByGatewayTransId(String gatewayTransId);

    /** Idempotency khi khởi tạo thanh toán (xem docs/api-design.md §5.1). */
    boolean existsByBookingIdAndStatusIn(UUID bookingId, Collection<TransactionStatus> statuses);

    /**
     * Dùng bởi PayoutAutoCreationScheduler để duyệt qua từng sự kiện có doanh thu.
     */
    @Query("select distinct t.eventId from Transaction t where t.status = com.ticketbooking.payment.enums.TransactionStatus.SUCCESS and t.eventId is not null")
    List<UUID> findDistinctEventIdsWithSuccessfulPayment();

    @Query("select coalesce(sum(t.amount), 0) from Transaction t where t.eventId = :eventId and t.status = com.ticketbooking.payment.enums.TransactionStatus.SUCCESS")
    BigDecimal sumSuccessAmountByEventId(@Param("eventId") UUID eventId);

    @Query("select coalesce(sum(t.quantity), 0) from Transaction t where t.eventId = :eventId and t.status = com.ticketbooking.payment.enums.TransactionStatus.SUCCESS")
    Integer sumSuccessQuantityByEventId(@Param("eventId") UUID eventId);
}
