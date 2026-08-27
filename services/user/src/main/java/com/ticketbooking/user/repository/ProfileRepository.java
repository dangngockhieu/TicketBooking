package com.ticketbooking.user.repository;

import com.ticketbooking.user.entity.Profile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ProfileRepository extends JpaRepository<Profile, UUID> {

    Optional<Profile> findByAccountId(UUID accountId);

    Optional<Profile> findByPhoneNumber(String phoneNumber);
}
