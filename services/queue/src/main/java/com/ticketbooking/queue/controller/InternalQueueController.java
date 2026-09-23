package com.ticketbooking.queue.controller;

import com.ticketbooking.common.dto.ApiResponse;
import com.ticketbooking.queue.dto.response.QueueAccessResponse;
import com.ticketbooking.queue.service.QueueService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Endpoint nội bộ (service-to-service qua Eureka, KHÔNG đi qua API Gateway —
 * xem InternalOrganizerBankAccountController bên user-service cho quy ước
 * tương tự) — dùng bởi Booking Service để kiểm tra {@code accessToken} trước
 * khi tạo đơn hàng khi phòng chờ đang bật (xem docs/virtual-waiting-room.md §8).
 */
@RestController
@RequestMapping("/api/internal/queue/{eventId}/access")
public class InternalQueueController {

    private final QueueService queueService;

    public InternalQueueController(QueueService queueService) {
        this.queueService = queueService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<QueueAccessResponse>> checkAccess(
            @PathVariable UUID eventId,
            @RequestParam UUID userId,
            @RequestParam(required = false) String accessToken) {
        return ResponseEntity.ok(ApiResponse.success(queueService.checkAccess(eventId, userId, accessToken)));
    }
}
