package com.ticketbooking.payment.controller;

import com.ticketbooking.common.dto.ApiResponse;
import com.ticketbooking.common.dto.PageResponse;
import com.ticketbooking.common.exception.UnauthorizedException;
import com.ticketbooking.payment.dto.request.CreatePayoutRequest;
import com.ticketbooking.payment.dto.response.PayoutResponse;
import com.ticketbooking.payment.enums.PayoutStatus;
import com.ticketbooking.payment.service.PayoutService;
import com.ticketbooking.payment.util.SecurityUtil;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/organizer/payouts")
@PreAuthorize("hasRole('ORGANIZER')")
public class OrganizerPayoutController {

    private final PayoutService payoutService;

    public OrganizerPayoutController(PayoutService payoutService) {
        this.payoutService = payoutService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<PayoutResponse>> requestPayout(@Valid @RequestBody CreatePayoutRequest request) {
        PayoutResponse response = payoutService.requestManualPayout(
                currentOrganizerId(), currentBearerToken(), request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Tạo yêu cầu rút tiền thành công.", response));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<PayoutResponse>>> listMine(
            @RequestParam(required = false) PayoutStatus status,
            @PageableDefault(size = 20, sort = "createdAt", direction = org.springframework.data.domain.Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(payoutService.listMine(currentOrganizerId(), status, pageable)));
    }

    private UUID currentOrganizerId() {
        return SecurityUtil.getCurrentUserId()
                .orElseThrow(() -> new UnauthorizedException("Bạn chưa đăng nhập."));
    }

    private String currentBearerToken() {
        return SecurityUtil.getCurrentBearerToken()
                .orElseThrow(() -> new UnauthorizedException("Bạn chưa đăng nhập."));
    }
}
