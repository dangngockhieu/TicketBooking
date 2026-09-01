package com.ticketbooking.catalog.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record TicketClassRequest(
        @NotBlank(message = "Tên hạng vé không được để trống")
        String name,

        String description,

        @NotNull(message = "Giá vé không được để trống")
        @DecimalMin(value = "0", message = "Giá vé không được âm")
        BigDecimal price,

        @NotNull(message = "Tổng số vé không được để trống")
        @Positive(message = "Tổng số vé phải lớn hơn 0")
        Integer totalQuantity
) {
}
