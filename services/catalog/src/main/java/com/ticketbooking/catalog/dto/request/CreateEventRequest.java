package com.ticketbooking.catalog.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record CreateEventRequest(
        UUID categoryId,

        @NotBlank(message = "Tên sự kiện không được để trống")
        String title,

        String description,

        @NotBlank(message = "Địa điểm không được để trống")
        String location,

        String venueName,

        String bannerUrl,

        @NotNull(message = "Thời gian bắt đầu không được để trống")
        Instant startTime,

        @NotNull(message = "Thời gian kết thúc không được để trống")
        Instant endTime,

        Instant saleStartTime,

        Instant saleEndTime,

        @NotEmpty(message = "Sự kiện phải có ít nhất 1 hạng vé")
        @Valid
        List<TicketClassRequest> ticketClasses
) {
}
