package com.ticketbooking.booking.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Bản sao rút gọn của {@code EventResponse} bên Catalog Service — chỉ giữ lại
 * các trường Booking Service cần để xác thực đơn hàng (giá, số vé còn lại, cửa
 * sổ mở bán). Bỏ qua các trường không dùng tới (title, description, ...).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CatalogEventDto(
        UUID id,
        String status,
        Instant saleStartTime,
        Instant saleEndTime,
        List<TicketClassDto> ticketClasses
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TicketClassDto(
            UUID id,
            String name,
            BigDecimal price,
            Integer availableQuantity
    ) {
    }
}
