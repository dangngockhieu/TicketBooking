package com.ticketbooking.payment.controller;

import com.ticketbooking.common.dto.ApiResponse;
import com.ticketbooking.common.exception.UnauthorizedException;
import com.ticketbooking.payment.dto.request.InitiatePaymentRequest;
import com.ticketbooking.payment.dto.response.PaymentInitiateResponse;
import com.ticketbooking.payment.momo.dto.MomoIpnRequest;
import com.ticketbooking.payment.service.PaymentService;
import com.ticketbooking.payment.util.SecurityUtil;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping("/initiate")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<ApiResponse<PaymentInitiateResponse>> initiate(@Valid @RequestBody InitiatePaymentRequest request) {
        PaymentInitiateResponse response = paymentService.initiate(currentCustomerId(), currentBearerToken(), request);
        return ResponseEntity.ok(ApiResponse.success("Khởi tạo thanh toán thành công.", response));
    }

    /**
     * MoMo IPN Callback — server-to-server, xác thực bằng signature (xem
     * docs/api-design.md §5.2). Phải trả 204 trong vòng 5s, nếu không MoMo
     * retry tối đa 5 lần.
     */
    @PostMapping("/momo/ipn")
    public ResponseEntity<Void> momoIpn(@RequestBody MomoIpnRequest request) {
        paymentService.handleMomoIpn(request);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    private UUID currentCustomerId() {
        return SecurityUtil.getCurrentUserId()
                .orElseThrow(() -> new UnauthorizedException("Bạn chưa đăng nhập."));
    }

    private String currentBearerToken() {
        return SecurityUtil.getCurrentBearerToken()
                .orElseThrow(() -> new UnauthorizedException("Bạn chưa đăng nhập."));
    }
}
