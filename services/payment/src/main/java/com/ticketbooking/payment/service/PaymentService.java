package com.ticketbooking.payment.service;

import com.ticketbooking.payment.dto.request.InitiatePaymentRequest;
import com.ticketbooking.payment.dto.response.PaymentInitiateResponse;

import java.util.UUID;

public interface PaymentService {

    /**
     * Khởi tạo thanh toán MoMo cho một booking (xem docs/api-design.md §5.1).
     *
     * @param customerId  chủ booking đang gọi (lấy từ JWT)
     * @param bearerToken forward nguyên vẹn sang Booking Service để service đó tự verify quyền sở hữu
     */
    PaymentInitiateResponse initiate(UUID customerId, String bearerToken, InitiatePaymentRequest request);
}
