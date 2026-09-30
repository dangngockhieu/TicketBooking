package com.ticketbooking.payment.service;

import com.ticketbooking.common.event.BookingRefundRequestedEvent;
import com.ticketbooking.payment.dto.request.InitiatePaymentRequest;
import com.ticketbooking.payment.dto.response.PaymentInitiateResponse;
import com.ticketbooking.payment.momo.dto.MomoIpnRequest;

import java.util.UUID;

public interface PaymentService {

    /**
     * Khởi tạo thanh toán MoMo cho một booking (xem docs/api-design.md §5.1).
     *
     * @param customerId     chủ booking đang gọi (lấy từ JWT)
     * @param bearerToken    forward nguyên vẹn sang Booking Service để service đó tự verify quyền sở hữu
     * @param idempotencyKey tùy chọn (client tự sinh) — nếu trùng key của một lần gọi
     *                       thành công trước đó trong TTL, trả lại response cũ thay vì
     *                       tạo giao dịch MoMo mới (chống double-click/double-submit)
     */
    PaymentInitiateResponse initiate(
            UUID customerId, String bearerToken, InitiatePaymentRequest request, String idempotencyKey);

    /**
     * Xử lý MoMo IPN Callback (xem docs/api-design.md §5.2) — verify signature,
     * idempotency theo transId, cập nhật Transaction, publish Kafka event.
     * Ném {@link com.ticketbooking.common.exception.BadRequestException} nếu
     * signature không khớp hoặc amount không khớp (chặn giả mạo).
     */
    void handleMomoIpn(MomoIpnRequest request);

    /**
     * Xử lý Saga Compensation (Kafka consumer {@code booking.refund-requested},
     * xem docs/api-design.md §5.4) — gọi MoMo Refund API cho giao dịch đã
     * SUCCESS, cập nhật Transaction sang REFUNDED và publish {@code payment.refunded}.
     * No-op nếu giao dịch không tồn tại hoặc đã REFUNDED (idempotent).
     */
    void processRefund(BookingRefundRequestedEvent event);
}
