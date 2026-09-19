package com.ticketbooking.payment.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;

/** Bản sao của {@code BankAccountResponse} bên User Service. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record BankAccountDto(
        String bankName,
        String bankAccountNumber,
        String bankAccountHolder,
        boolean verified,
        Instant verifiedAt
) {
}
