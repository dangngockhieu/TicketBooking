package com.ticketbooking.payment.service;

import java.math.BigDecimal;
import java.util.UUID;

public interface OrganizerWalletService {

    /**
     * Cộng tiền vào ví Organizer sau khi trừ hoa hồng nền tảng (xem
     * docs/development-plan.md GĐ4 mục 4): {@code net = gross - gross*commissionRate
     * - flatFeePerTicket*quantity}, không cho âm. Tự tạo ví nếu Organizer chưa có.
     */
    void creditForBooking(UUID organizerId, BigDecimal grossAmount, BigDecimal commissionRate,
                           BigDecimal flatFeePerTicket, int quantity);
}
