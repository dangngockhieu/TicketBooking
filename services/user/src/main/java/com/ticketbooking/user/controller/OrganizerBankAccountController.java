package com.ticketbooking.user.controller;

import com.ticketbooking.common.dto.ApiResponse;
import com.ticketbooking.user.dto.request.BankAccountRequest;
import com.ticketbooking.user.dto.response.BankAccountResponse;
import com.ticketbooking.user.service.OrganizerBankAccountService;
import com.ticketbooking.user.util.SecurityUtil;
import com.ticketbooking.common.exception.UnauthorizedException;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/organizer/bank-account")
@PreAuthorize("hasRole('ORGANIZER')")
public class OrganizerBankAccountController {

    private final OrganizerBankAccountService bankAccountService;

    public OrganizerBankAccountController(OrganizerBankAccountService bankAccountService) {
        this.bankAccountService = bankAccountService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<BankAccountResponse>> getMyBankAccount() {
        return ResponseEntity.ok(ApiResponse.success(bankAccountService.getMyBankAccount(currentAccountId())));
    }

    @PutMapping
    public ResponseEntity<ApiResponse<BankAccountResponse>> upsertMyBankAccount(
            @Valid @RequestBody BankAccountRequest request) {
        BankAccountResponse response = bankAccountService.upsertMyBankAccount(currentAccountId(), request);
        return ResponseEntity.ok(ApiResponse.success("Cập nhật tài khoản ngân hàng thành công. Chờ Admin xác minh.", response));
    }

    private UUID currentAccountId() {
        return SecurityUtil.getCurrentUserId()
                .orElseThrow(() -> new UnauthorizedException("Bạn chưa đăng nhập."));
    }
}
