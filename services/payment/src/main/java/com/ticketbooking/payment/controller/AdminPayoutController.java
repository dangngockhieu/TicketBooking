package com.ticketbooking.payment.controller;

import com.ticketbooking.common.dto.ApiResponse;
import com.ticketbooking.payment.dto.request.UpdatePayoutStatusRequest;
import com.ticketbooking.payment.dto.response.PayoutResponse;
import com.ticketbooking.payment.service.PayoutService;
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
@RequestMapping("/api/admin/payouts")
@PreAuthorize("hasRole('ADMIN')")
public class AdminPayoutController {

    private final PayoutService payoutService;

    public AdminPayoutController(PayoutService payoutService) {
        this.payoutService = payoutService;
    }

    @PatchMapping("/{payoutId}/status")
    public ResponseEntity<ApiResponse<PayoutResponse>> updateStatus(
            @PathVariable UUID payoutId, @Valid @RequestBody UpdatePayoutStatusRequest request) {
        PayoutResponse response = payoutService.updateStatus(payoutId, request);
        return ResponseEntity.ok(ApiResponse.success("Cập nhật trạng thái yêu cầu rút tiền thành công.", response));
    }
}
