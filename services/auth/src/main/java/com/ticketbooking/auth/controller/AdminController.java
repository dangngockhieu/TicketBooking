package com.ticketbooking.auth.controller;

import com.ticketbooking.auth.dto.request.AdminCreateOrganizerRequest;
import com.ticketbooking.auth.dto.response.AdminCreateOrganizerResponse;
import com.ticketbooking.auth.service.AuthService;
import com.ticketbooking.common.dto.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * API dành riêng cho ADMIN. Xem docs/technical-flows.md §0.5, docs/api-design.md
 * §1.5 (repo gốc) — Admin cấp tài khoản Organizer trực tiếp, không có luồng
 * "duyệt đơn đăng ký" vì Organizer không tự đăng ký.
 */
@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final AuthService authService;

    public AdminController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/organizers")
    public ResponseEntity<ApiResponse<AdminCreateOrganizerResponse>> createOrganizer(
            @Valid @RequestBody AdminCreateOrganizerRequest request
    ) {
        AdminCreateOrganizerResponse response = authService.createOrganizer(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success("Đã tạo tài khoản Organizer.", response));
    }
}
