package com.ticketbooking.payment.controller;

import com.ticketbooking.common.dto.ApiResponse;
import com.ticketbooking.common.exception.UnauthorizedException;
import com.ticketbooking.payment.dto.response.WalletResponse;
import com.ticketbooking.payment.service.PayoutService;
import com.ticketbooking.payment.util.SecurityUtil;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/organizer/wallet")
@PreAuthorize("hasRole('ORGANIZER')")
public class OrganizerWalletController {

    private final PayoutService payoutService;

    public OrganizerWalletController(PayoutService payoutService) {
        this.payoutService = payoutService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<WalletResponse>> getWallet() {
        return ResponseEntity.ok(ApiResponse.success(payoutService.getWallet(currentOrganizerId())));
    }

    private UUID currentOrganizerId() {
        return SecurityUtil.getCurrentUserId()
                .orElseThrow(() -> new UnauthorizedException("Bạn chưa đăng nhập."));
    }
}
