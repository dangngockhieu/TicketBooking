package com.ticketbooking.auth.controller;

import com.ticketbooking.auth.dto.request.ChangePasswordRequest;
import com.ticketbooking.auth.dto.request.LoginRequest;
import com.ticketbooking.auth.dto.request.RefreshTokenRequest;
import com.ticketbooking.auth.dto.request.RegisterRequest;
import com.ticketbooking.auth.dto.request.ResendVerificationRequest;
import com.ticketbooking.auth.dto.request.VerifyEmailRequest;
import com.ticketbooking.auth.dto.response.AuthResponse;
import com.ticketbooking.auth.dto.response.LoginResult;
import com.ticketbooking.auth.enums.ClientType;
import com.ticketbooking.auth.security.JwtTokenProvider;
import com.ticketbooking.auth.service.AuthService;
import com.ticketbooking.auth.util.SecurityUtil;
import com.ticketbooking.common.dto.ApiResponse;
import com.ticketbooking.common.exception.UnauthorizedException;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final JwtTokenProvider jwtTokenProvider;

    public AuthController(AuthService authService, JwtTokenProvider jwtTokenProvider) {
        this.authService = authService;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<AuthResponse.UserInfo>> register(@Valid @RequestBody RegisterRequest request) {
        AuthResponse.UserInfo userInfo = authService.register(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success("Đăng ký tài khoản thành công.", userInfo));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(
            @RequestHeader(value = "X-Client-Type", required = false) String clientTypeHeader,
            @Valid @RequestBody LoginRequest request
    ) {
        ClientType clientType = ClientType.fromString(clientTypeHeader);
        LoginResult result = authService.login(request, clientType);

        ResponseEntity.BodyBuilder responseBuilder = ResponseEntity.ok();

        // Nếu là WEB: Gắn Refresh Token vào HttpOnly Cookie
        if (result.clientType() == ClientType.WEB) {
            ResponseCookie cookie = ResponseCookie.from("refreshToken", result.rawRefreshToken())
                    .httpOnly(true)
                    .path("/")
                    .maxAge(jwtTokenProvider.getRefreshTokenExpirationSeconds())
                    .sameSite("Strict")
                    .build();
            responseBuilder.header(HttpHeaders.SET_COOKIE, cookie.toString());
        }

        return responseBuilder.body(ApiResponse.success("Đăng nhập thành công.", result.authResponse()));
    }

    @PostMapping("/verify-email")
    public ResponseEntity<ApiResponse<AuthResponse>> verifyEmail(
            @RequestHeader(value = "X-Client-Type", required = false) String clientTypeHeader,
            @Valid @RequestBody VerifyEmailRequest request
    ) {
        ClientType clientType = ClientType.fromString(clientTypeHeader);
        LoginResult result = authService.verifyEmail(request, clientType);

        ResponseEntity.BodyBuilder responseBuilder = ResponseEntity.ok();

        // Xác thực thành công ➔ tự động đăng nhập, áp dụng Session Binding như /login
        if (result.clientType() == ClientType.WEB) {
            ResponseCookie cookie = ResponseCookie.from("refreshToken", result.rawRefreshToken())
                    .httpOnly(true)
                    .path("/")
                    .maxAge(jwtTokenProvider.getRefreshTokenExpirationSeconds())
                    .sameSite("Strict")
                    .build();
            responseBuilder.header(HttpHeaders.SET_COOKIE, cookie.toString());
        }

        return responseBuilder.body(ApiResponse.success("Xác thực email thành công.", result.authResponse()));
    }

    @PostMapping("/resend-verification")
    public ResponseEntity<ApiResponse<Void>> resendVerification(
            @Valid @RequestBody ResendVerificationRequest request
    ) {
        authService.resendVerification(request);
        return ResponseEntity.ok(ApiResponse.success("Đã gửi lại mã xác thực.", null));
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<AuthResponse>> refresh(
            @CookieValue(name = "refreshToken", required = false) String cookieRefreshToken,
            @RequestBody(required = false) RefreshTokenRequest bodyRequest
    ) {
        String rawToken = (cookieRefreshToken != null && !cookieRefreshToken.isBlank())
                ? cookieRefreshToken
                : (bodyRequest != null ? bodyRequest.refreshToken() : null);

        if (rawToken == null || rawToken.isBlank()) {
            throw new UnauthorizedException("Phiên đăng nhập đã hết hạn hoặc không tìm thấy Refresh Token. Vui lòng đăng nhập lại.");
        }

        LoginResult result = authService.refresh(rawToken);
        ResponseEntity.BodyBuilder responseBuilder = ResponseEntity.ok();

        // Áp dụng Session Binding từ DB: Nếu nguồn gốc là WEB ➔ Set Cookie mới
        if (result.clientType() == ClientType.WEB) {
            ResponseCookie cookie = ResponseCookie.from("refreshToken", result.rawRefreshToken())
                    .httpOnly(true)
                    .path("/")
                    .maxAge(jwtTokenProvider.getRefreshTokenExpirationSeconds())
                    .sameSite("Strict")
                    .build();
            responseBuilder.header(HttpHeaders.SET_COOKIE, cookie.toString());
        }

        return responseBuilder.body(ApiResponse.success("Làm mới token thành công.", result.authResponse()));
    }

    @PostMapping("/logout")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Void>> logout() {
        String email = SecurityUtil.getCurrentUserLogin().orElse(null);
        authService.logout(email);

        ResponseCookie deleteCookie = ResponseCookie.from("refreshToken", "")
                .httpOnly(true)
                .path("/")
                .maxAge(0)
                .build();

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, deleteCookie.toString())
                .body(ApiResponse.success("Đăng xuất thành công.", null));
    }

    @GetMapping("/account")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'ORGANIZER', 'ADMIN')")
    public ResponseEntity<ApiResponse<AuthResponse.UserInfo>> getCurrentUser() {
        String email = SecurityUtil.getCurrentUserLogin().orElse(null);
        AuthResponse.UserInfo profile = authService.getCurrentUserProfile(email);
        return ResponseEntity.ok(ApiResponse.success("Lấy thông tin tài khoản thành công.", profile));
    }

    @PutMapping("/change-password")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Void>> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        String email = SecurityUtil.getCurrentUserLogin().orElse(null);
        authService.changePassword(email, request);

        // Đổi mật khẩu đã thu hồi toàn bộ refresh token (kể cả của phiên hiện tại)
        // ➔ xóa luôn cookie phía client để tránh gọi /refresh thất bại lặp lại.
        // Client (web) cần đăng nhập lại bằng mật khẩu mới.
        ResponseCookie deleteCookie = ResponseCookie.from("refreshToken", "")
                .httpOnly(true)
                .path("/")
                .maxAge(0)
                .build();

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, deleteCookie.toString())
                .body(ApiResponse.success("Đổi mật khẩu thành công. Vui lòng đăng nhập lại.", null));
    }
}
