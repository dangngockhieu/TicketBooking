package com.ticketbooking.payment.repository;

import com.ticketbooking.payment.entity.PayoutRequest;
import com.ticketbooking.payment.enums.PayoutSource;
import com.ticketbooking.payment.enums.PayoutStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface PayoutRequestRepository extends JpaRepository<PayoutRequest, UUID> {

    boolean existsByEventIdAndSource(UUID eventId, PayoutSource source);

    Page<PayoutRequest> findByOrganizerId(UUID organizerId, Pageable pageable);

    Page<PayoutRequest> findByOrganizerIdAndStatus(UUID organizerId, PayoutStatus status, Pageable pageable);
}
