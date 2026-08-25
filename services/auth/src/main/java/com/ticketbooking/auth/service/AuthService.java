package com.ticketbooking.auth.service;

import com.ticketbooking.auth.dto.request.AdminCreateOrganizerRequest;
import com.ticketbooking.auth.dto.request.ChangePasswordRequest;
import com.ticketbooking.auth.dto.request.LoginRequest;
import com.ticketbooking.auth.dto.request.RegisterRequest;
import com.ticketbooking.auth.dto.request.ResendVerificationRequest;
import com.ticketbooking.auth.dto.request.VerifyEmailRequest;
import com.ticketbooking.auth.dto.response.AdminCreateOrganizerResponse;
import com.ticketbooking.auth.dto.response.AuthResponse;
import com.ticketbooking.auth.dto.response.LoginResult;
import com.ticketbooking.auth.enums.ClientType;

public interface AuthService {

    AuthResponse.UserInfo register(RegisterRequest request);

    /**
     * Xác thực OTP đã gửi khi đăng ký. Thành công thì chuyển account sang
     * ACTIVE và tự động đăng nhập (trả về AuthResponse như login).
     */
    LoginResult verifyEmail(VerifyEmailRequest request, ClientType clientType);

    /** Sinh và "gửi" (log dev) lại OTP mới cho tài khoản đang PENDING. */
    void resendVerification(ResendVerificationRequest request);

    LoginResult login(LoginRequest request, ClientType clientType);

    LoginResult refresh(String refreshToken);

    void logout(String email);

    AuthResponse.UserInfo getCurrentUserProfile(String email);

    /**
     * Đổi mật khẩu khi đã đăng nhập. Xác thực mật khẩu hiện tại, kiểm tra mật
     * khẩu mới khớp xác nhận, sau đó thu hồi toàn bộ refresh token hiện có
     * (đăng xuất mọi phiên khác) và tắt cờ requirePasswordChange nếu đang bật.
     */
    void changePassword(String email, ChangePasswordRequest request);

    /**
     * Admin tạo trực tiếp tài khoản Organizer (ACTIVE ngay, không qua OTP) sau
     * khi đã thẩm định giấy phép tổ chức sự kiện ngoài hệ thống.
     */
    AdminCreateOrganizerResponse createOrganizer(AdminCreateOrganizerRequest request);
}
