package com.ticketbooking.payment.dto.response;

import com.ticketbooking.payment.entity.OrganizerWallet;

import java.math.BigDecimal;
import java.time.Instant;

public record WalletResponse(
        BigDecimal availableBalance,
        BigDecimal pendingPayout,
        BigDecimal totalWithdrawn,
        Instant updatedAt
) {
    public static WalletResponse from(OrganizerWallet wallet) {
        return new WalletResponse(
                wallet.getAvailableBalance(), wallet.getPendingPayout(),
                wallet.getTotalWithdrawn(), wallet.getUpdatedAt());
    }

    public static WalletResponse empty() {
        return new WalletResponse(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, null);
    }
}
