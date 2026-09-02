package com.ticketbooking.user.controller;

import com.ticketbooking.common.dto.ApiResponse;
import com.ticketbooking.user.dto.response.BankAccountResponse;
import com.ticketbooking.user.service.OrganizerBankAccountService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/admin/organizers/{organizerId}/bank-account")
@PreAuthorize("hasRole('ADMIN')")
public class AdminBankAccountController {

    private final OrganizerBankAccountService bankAccountService;

    public AdminBankAccountController(OrganizerBankAccountService bankAccountService) {
        this.bankAccountService = bankAccountService;
    }

    @PatchMapping("/verify")
    public ResponseEntity<ApiResponse<BankAccountResponse>> verify(@PathVariable UUID organizerId) {
        BankAccountResponse response = bankAccountService.verify(organizerId);
        return ResponseEntity.ok(ApiResponse.success("Xác minh tài khoản ngân hàng thành công.", response));
    }
}
