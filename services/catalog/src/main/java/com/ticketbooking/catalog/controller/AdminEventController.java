package com.ticketbooking.catalog.controller;

import com.ticketbooking.catalog.dto.request.EventFeeUpdateRequest;
import com.ticketbooking.catalog.dto.response.EventResponse;
import com.ticketbooking.catalog.service.EventService;
import com.ticketbooking.common.dto.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/admin/events")
@PreAuthorize("hasRole('ADMIN')")
public class AdminEventController {

    private final EventService eventService;

    public AdminEventController(EventService eventService) {
        this.eventService = eventService;
    }

    @PatchMapping("/{eventId}/fees")
    public ResponseEntity<ApiResponse<EventResponse>> updateFees(
            @PathVariable UUID eventId, @Valid @RequestBody EventFeeUpdateRequest request) {
        EventResponse response = eventService.updateFees(eventId, request);
        return ResponseEntity.ok(ApiResponse.success("Cập nhật phí nền tảng thành công.", response));
    }
}
