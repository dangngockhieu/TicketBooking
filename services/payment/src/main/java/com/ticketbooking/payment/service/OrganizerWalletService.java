package com.ticketbooking.payment.service;

import com.ticketbooking.payment.entity.OrganizerWallet;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

public interface OrganizerWalletService {

    /**
     * Cộng tiền vào ví Organizer sau khi trừ hoa hồng nền tảng (xem
     * {@code net = gross - gross*commissionRate
     * - flatFeePerTicket*quantity}, không cho âm. Tự tạo ví nếu Organizer chưa có.
     */
    void creditForBooking(UUID organizerId, BigDecimal grossAmount, BigDecimal commissionRate,
            BigDecimal flatFeePerTicket, int quantity);

    Optional<OrganizerWallet> findWallet(UUID organizerId);

    /**
     * Chuyển {@code amount} từ availableBalance sang pendingPayout khi tạo
     * payout request — trả về {@code false} nếu không đủ availableBalance
     * (không thay đổi gì).
     */
    boolean reserveForPayout(UUID organizerId, BigDecimal amount);

    /**
     * Trả {@code amount} từ pendingPayout về availableBalance (payout bị từ chối).
     */
    void releaseReservedPayout(UUID organizerId, BigDecimal amount);

    /**
     * Chuyển {@code amount} từ pendingPayout sang totalWithdrawn (payout chi trả
     * thành công).
     */
    void markPayoutPaid(UUID organizerId, BigDecimal amount);
}
