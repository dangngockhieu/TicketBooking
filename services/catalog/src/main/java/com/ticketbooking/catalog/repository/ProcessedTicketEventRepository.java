package com.ticketbooking.catalog.repository;

import com.ticketbooking.catalog.entity.ProcessedTicketEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ProcessedTicketEventRepository extends JpaRepository<ProcessedTicketEvent, UUID> {
}
