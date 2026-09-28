package com.ticketbooking.auth.controller;

import com.ticketbooking.auth.dto.response.InternalAccountResponse;
import com.ticketbooking.auth.repository.AccountRepository;
import com.ticketbooking.common.dto.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Endpoint nội bộ (service-to-service qua Eureka, KHÔNG đi qua API Gateway —
 * xem InternalOrganizerBankAccountController bên user-service cho quy ước
 * tương tự) — dùng bởi Booking Service để lấy email khách hàng khi gửi
 * E-Ticket qua Notification Service (email không được lưu ở user-service,
 * chỉ auth-service giữ vì đó là định danh đăng nhập).
 */
@RestController
@RequestMapping("/api/internal/accounts/{accountId}")
public class InternalAccountController {

    private final AccountRepository accountRepository;

    public InternalAccountController(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<InternalAccountResponse>> getEmail(@PathVariable UUID accountId) {
        String email = accountRepository.findById(accountId).map(a -> a.getEmail()).orElse(null);
        return ResponseEntity.ok(ApiResponse.success(new InternalAccountResponse(email)));
    }
}
