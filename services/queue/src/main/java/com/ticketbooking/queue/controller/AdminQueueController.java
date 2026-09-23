package com.ticketbooking.queue.controller;

import com.ticketbooking.common.dto.ApiResponse;
import com.ticketbooking.queue.dto.request.QueueConfigUpdateRequest;
import com.ticketbooking.queue.dto.response.QueueConfigResponse;
import com.ticketbooking.queue.model.QueueConfig;
import com.ticketbooking.queue.service.QueueConfigService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Admin bật/tắt thủ công hoặc chỉnh ngưỡng phòng chờ cho một sự kiện (xem
 * docs/virtual-waiting-room.md §3 mục 1). {@code QueueAutoToggleScheduler}
 * vẫn có thể ghi đè {@code enabled} ở lần chạy tiếp theo nếu tải thực tế vượt
 * ngưỡng — đây là hành vi mong muốn theo thiết kế (auto luôn thắng theo tải
 * thực tế), admin chỉ áp dụng tức thời/ngưỡng, không "khóa cứng" cấu hình.
 */
@RestController
@RequestMapping("/api/admin/queue")
@PreAuthorize("hasRole('ADMIN')")
public class AdminQueueController {

    private final QueueConfigService configService;

    public AdminQueueController(QueueConfigService configService) {
        this.configService = configService;
    }

    @GetMapping("/{eventId}/config")
    public ResponseEntity<ApiResponse<QueueConfigResponse>> getConfig(@PathVariable UUID eventId) {
        QueueConfig config = configService.getConfig(eventId);
        return ResponseEntity.ok(ApiResponse.success(QueueConfigResponse.from(eventId, config)));
    }

    @PatchMapping("/{eventId}/config")
    public ResponseEntity<ApiResponse<QueueConfigResponse>> updateConfig(
            @PathVariable UUID eventId, @Valid @RequestBody QueueConfigUpdateRequest request) {
        QueueConfig updated = configService.updateConfig(eventId, request);
        return ResponseEntity.ok(ApiResponse.success("Cập nhật cấu hình phòng chờ thành công.",
                QueueConfigResponse.from(eventId, updated)));
    }
}
