package com.ticketbooking.payment.repository;

import com.ticketbooking.payment.entity.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface TransactionRepository extends JpaRepository<Transaction, UUID> {

    Optional<Transaction> findByGatewayTransId(String gatewayTransId);

    Optional<Transaction> findByBookingId(UUID bookingId);
}
