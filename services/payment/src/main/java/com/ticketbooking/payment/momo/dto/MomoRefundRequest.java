package com.ticketbooking.payment.momo.dto;

/** Request body cho {@code POST /v2/gateway/api/refund} (xem docs/api-design.md §5.4). */
public record MomoRefundRequest(
        String partnerCode,
        String accessKey,
        String orderId,
        String requestId,
        String amount,
        long transId,
        String lang,
        String description,
        String signature
) {
}
