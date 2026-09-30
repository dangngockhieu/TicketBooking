package com.ticketbooking.payment.service;

import com.ticketbooking.payment.dto.response.PaymentInitiateResponse;

import java.util.Optional;

/**
 * Idempotency key cho POST /api/payments/initiate (header {@code Idempotency-Key},
 * client tự sinh UUID mỗi lần user bấm nút thanh toán) — chống double-click/
 * double-submit tạo 2 giao dịch MoMo cho cùng 1 booking. Lưu trong Redis với
 * TTL ngắn (không phải cache aside như catalog-service).
 */
public interface PaymentIdempotencyService {

    /** Response đã lưu cho key này (nếu request trước đó với cùng key đã thành công), hoặc rỗng nếu chưa từng thấy. */
    Optional<PaymentInitiateResponse> findCachedResponse(String idempotencyKey);

    /** Lưu response thành công để trả lại y hệt nếu client gửi lại cùng key trong TTL. */
    void saveResponse(String idempotencyKey, PaymentInitiateResponse response);
}
