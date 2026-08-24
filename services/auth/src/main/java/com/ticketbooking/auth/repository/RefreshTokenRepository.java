package com.ticketbooking.auth.repository;

import com.ticketbooking.auth.entity.Account;
import com.ticketbooking.auth.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    Optional<RefreshToken> findByToken(String token);

    void deleteByToken(String token);

    void deleteByAccount(Account account);

    @Modifying
    @Query("UPDATE RefreshToken r SET r.revoked = true WHERE r.account.id = :accountId")
    void revokeAllByAccountId(@Param("accountId") UUID accountId);

    @Modifying
    @Query("DELETE FROM RefreshToken r WHERE r.expiredAt < :now OR r.revoked = true")
    void deleteExpiredOrRevoked(@Param("now") Instant now);
}
