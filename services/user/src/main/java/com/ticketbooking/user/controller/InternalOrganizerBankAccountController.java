package com.ticketbooking.user.controller;

import com.ticketbooking.common.dto.ApiResponse;
import com.ticketbooking.user.dto.response.BankAccountResponse;
import com.ticketbooking.user.service.OrganizerBankAccountService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Endpoint nội bộ (service-to-service qua Eureka, KHÔNG đi qua API Gateway —
 * xem docs/system-design.md §9 "api-gateway là điểm vào duy nhất từ bên
 * ngoài") — dùng bởi Payment Service để lấy snapshot tài khoản ngân hàng đã
 * xác minh khi tạo PayoutRequest tự động (job nền không có JWT của Organizer
 * để forward, khác với luồng rút tiền thủ công — xem docs/development-plan.md
 * GĐ4 mục 4). KHÔNG permitAll ở tầng Gateway vì route này không được khai báo
 * ở đó; permitAll ở tầng Spring Security của chính service này vì lời gọi chỉ
 * đến từ mạng nội bộ giữa các service.
 */
@RestController
@RequestMapping("/api/internal/organizers/{organizerId}/bank-account")
public class InternalOrganizerBankAccountController {

    private final OrganizerBankAccountService bankAccountService;

    public InternalOrganizerBankAccountController(OrganizerBankAccountService bankAccountService) {
        this.bankAccountService = bankAccountService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<BankAccountResponse>> getBankAccount(@PathVariable UUID organizerId) {
        return ResponseEntity.ok(ApiResponse.success(bankAccountService.getMyBankAccount(organizerId)));
    }
}
