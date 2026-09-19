package com.ticketbooking.catalog.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * Admin sửa phí nền tảng riêng cho từng sự kiện (xem
 * docs/development-plan.md GĐ4 mục 4 — mặc định 5% + 3.000đ/vé, chỉ ADMIN
 * sửa được).
 */
public record EventFeeUpdateRequest(
        @NotNull(message = "commissionRate không được để trống")
        @DecimalMin(value = "0", message = "commissionRate phải >= 0")
        @DecimalMax(value = "1", message = "commissionRate phải <= 1")
        BigDecimal commissionRate,

        @NotNull(message = "flatFeePerTicket không được để trống")
        @DecimalMin(value = "0", message = "flatFeePerTicket phải >= 0")
        BigDecimal flatFeePerTicket
) {
}
