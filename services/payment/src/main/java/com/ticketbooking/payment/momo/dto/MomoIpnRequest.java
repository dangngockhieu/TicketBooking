package com.ticketbooking.payment.momo.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Request body MoMo gửi tới {@code POST /api/payments/momo/ipn} (xem docs/api-design.md §5.2). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record MomoIpnRequest(
        String partnerCode,
        String orderId,
        String requestId,
        Long amount,
        String orderInfo,
        String orderType,
        Long transId,
        Integer resultCode,
        String message,
        String payType,
        Long responseTime,
        String extraData,
        String signature
) {
}
