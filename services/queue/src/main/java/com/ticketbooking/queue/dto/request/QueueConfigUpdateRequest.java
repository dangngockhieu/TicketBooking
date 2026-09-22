package com.ticketbooking.queue.dto.request;

import jakarta.validation.constraints.Min;

/**
 * Admin bật/tắt thủ công hoặc chỉnh ngưỡng phòng chờ cho một sự kiện (xem
 * docs/virtual-waiting-room.md §3 mục 1). Tất cả field đều optional — chỉ áp
 * dụng field nào được truyền (partial update), field còn lại giữ nguyên giá
 * trị hiện tại (hoặc mặc định nếu sự kiện chưa từng được cấu hình).
 */
public record QueueConfigUpdateRequest(
        Boolean enabled,

        @Min(value = 1, message = "maxConcurrent phải >= 1")
        Integer maxConcurrent,

        @Min(value = 1, message = "autoEnableThreshold phải >= 1")
        Integer autoEnableThreshold,

        @Min(value = 30, message = "purchaseWindowSeconds phải >= 30")
        Integer purchaseWindowSeconds
) {
}
