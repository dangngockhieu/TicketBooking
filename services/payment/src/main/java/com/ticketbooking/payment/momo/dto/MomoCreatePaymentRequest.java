package com.ticketbooking.payment.momo.dto;

/** Request body cho {@code POST /v2/gateway/api/create} (xem docs/api-design.md §5.1). */
public record MomoCreatePaymentRequest(
        String partnerCode,
        String accessKey,
        String requestId,
        String amount,
        String orderId,
        String orderInfo,
        String redirectUrl,
        String ipnUrl,
        String requestType,
        String extraData,
        String lang,
        String signature
) {
}
