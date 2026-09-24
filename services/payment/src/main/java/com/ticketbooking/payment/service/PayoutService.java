package com.ticketbooking.payment.service;

import com.ticketbooking.payment.dto.request.CreatePayoutRequest;
import com.ticketbooking.payment.dto.request.UpdatePayoutStatusRequest;
import com.ticketbooking.payment.dto.response.PayoutResponse;
import com.ticketbooking.payment.dto.response.WalletResponse;
import com.ticketbooking.payment.enums.PayoutStatus;
import com.ticketbooking.common.dto.PageResponse;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface PayoutService {

    WalletResponse getWallet(UUID organizerId);

    /**
     * Xin rút tiền thủ công (§7.4, source=MANUAL) — tự tra cứu tài khoản ngân
     * hàng đã xác minh (forward JWT của Organizer sang User Service), từ chối
     * nếu chưa xác minh hoặc vượt quá availableBalance.
     */
    PayoutResponse requestManualPayout(UUID organizerId, String bearerToken, CreatePayoutRequest request);

    PageResponse<PayoutResponse> listMine(UUID organizerId, PayoutStatus status, Pageable pageable);

    /**
     * ADMIN duyệt/từ chối/tạm giữ/chi trả — PAID kích hoạt gọi MoMo Disbursement
     * API (§7.5).
     */
    PayoutResponse updateStatus(UUID payoutId, UpdatePayoutStatusRequest request);

    /**
     * Job nền hàng ngày - với mỗi sự
     * kiện đã COMPLETED đủ 7 ngày và chưa có payout AUTO, tạo PayoutRequest
     * bằng netRevenue của sự kiện đó, reserve từ availableBalance sang
     * pendingPayout.
     */
    void createAutoPayouts();
}
