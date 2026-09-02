package com.ticketbooking.user.repository;

import com.ticketbooking.user.entity.OrganizerBankAccount;
import com.ticketbooking.user.entity.Profile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface OrganizerBankAccountRepository extends JpaRepository<OrganizerBankAccount, UUID> {

    Optional<OrganizerBankAccount> findByProfile(Profile profile);

    Optional<OrganizerBankAccount> findByProfile_AccountId(UUID accountId);
}
