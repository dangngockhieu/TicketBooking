package com.ticketbooking.payment.momo.dto;

/** Request body cho {@code POST /v2/gateway/api/disburse} (xem docs/api-design.md §7.5). */
public record MomoDisburseRequest(
        String partnerCode,
        String accessKey,
        String requestId,
        String orderId,
        String amount,
        String receiver,
        String description,
        String signature
) {
}
