package com.ticketbooking.catalog.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.UUID;

/**
 * Sửa thông tin cốt lõi của sự kiện. KHÔNG bao gồm hạng vé — thêm/xóa/sửa hạng
 * vé sau khi tạo chưa nằm trong phạm vi API đã đặc tả (docs/api-design.md §3.5).
 */
public record UpdateEventRequest(
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

        Instant saleEndTime
) {
}
