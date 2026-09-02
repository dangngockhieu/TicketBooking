package com.ticketbooking.user.dto.response;

import com.ticketbooking.user.entity.OrganizerBankAccount;

import java.time.Instant;

public record BankAccountResponse(
        String bankName,
        String bankAccountNumber,
        String bankAccountHolder,
        boolean verified,
        Instant verifiedAt
) {
    public static BankAccountResponse from(OrganizerBankAccount entity) {
        return new BankAccountResponse(
                entity.getBankName(),
                entity.getBankAccountNumber(),
                entity.getBankAccountHolder(),
                entity.isVerified(),
                entity.getVerifiedAt());
    }
}
