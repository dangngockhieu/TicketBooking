package com.ticketbooking.user.controller;

import com.ticketbooking.common.dto.ApiResponse;
import com.ticketbooking.user.dto.response.InternalProfileResponse;
import com.ticketbooking.user.service.ProfileService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Endpoint nội bộ (service-to-service qua Eureka, KHÔNG đi qua API Gateway —
 * xem InternalOrganizerBankAccountController để biết quy ước) — dùng bởi
 * Booking Service để hiển thị tên khách hàng khi check-in vé bằng QR (xem
 * docs/api-design.md §4.5).
 */
@RestController
@RequestMapping("/api/internal/profiles/{accountId}")
public class InternalProfileController {

    private final ProfileService profileService;

    public InternalProfileController(ProfileService profileService) {
        this.profileService = profileService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<InternalProfileResponse>> getFullName(@PathVariable UUID accountId) {
        String fullName = profileService.findFullName(accountId).orElse(null);
        return ResponseEntity.ok(ApiResponse.success(new InternalProfileResponse(fullName)));
    }
}
