package com.ticketbooking.booking.repository;

import com.ticketbooking.booking.entity.Ticket;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface TicketRepository extends JpaRepository<Ticket, UUID> {

    Optional<Ticket> findByQrCodeData(String qrCodeData);
}
