package com.ticketbooking.payment.momo.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Response từ {@code POST /v2/gateway/api/disburse} — chỉ giữ các trường cần dùng. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record MomoDisburseResponse(
        String orderId,
        String requestId,
        Integer resultCode,
        String message,
        Long transId
) {
    public boolean isSuccess() {
        return resultCode != null && resultCode == 0;
    }
}
