package com.ticketbooking.payment.repository;

import com.ticketbooking.payment.entity.OrganizerWallet;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface OrganizerWalletRepository extends JpaRepository<OrganizerWallet, UUID> {

    Optional<OrganizerWallet> findByOrganizerId(UUID organizerId);
}
