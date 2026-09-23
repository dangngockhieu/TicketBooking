package com.ticketbooking.queue.controller;

import com.ticketbooking.queue.dto.response.QueueStatusResponse;
import com.ticketbooking.queue.service.QueueService;
import com.ticketbooking.common.dto.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Frontend gọi trước khi mở trang sự kiện để biết có cần vào phòng chờ không (xem docs/virtual-waiting-room.md §4.1). */
@RestController
@RequestMapping("/api/queue")
public class QueueStatusController {

    private final QueueService queueService;

    public QueueStatusController(QueueService queueService) {
        this.queueService = queueService;
    }

    @GetMapping("/{eventId}/status")
    public ResponseEntity<ApiResponse<QueueStatusResponse>> getStatus(@PathVariable UUID eventId) {
        boolean queueRequired = queueService.isEnabled(eventId);
        return ResponseEntity.ok(ApiResponse.success(new QueueStatusResponse(eventId, queueRequired)));
    }
}
