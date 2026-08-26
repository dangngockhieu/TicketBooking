package com.ticketbooking.auth.service;

import com.ticketbooking.auth.dto.request.AdminCreateOrganizerRequest;
import com.ticketbooking.auth.dto.request.ChangePasswordRequest;
import com.ticketbooking.auth.dto.request.ForgotPasswordRequest;
import com.ticketbooking.auth.dto.request.LoginRequest;
import com.ticketbooking.auth.dto.request.RegisterRequest;
import com.ticketbooking.auth.dto.request.ResendVerificationRequest;
import com.ticketbooking.auth.dto.request.ResetPasswordRequest;
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

    /**
     * Sinh OTP đặt lại mật khẩu nếu email tồn tại. Luôn thành công (không ném
     * lỗi khi không tìm thấy tài khoản) để chống dò email hợp lệ (account
     * enumeration) — xem FE docs/04-auth-flow.md §3.3d.
     */
    void forgotPassword(ForgotPasswordRequest request);

    /**
     * Xác thực OTP đặt lại mật khẩu và đổi mật khẩu mới. Khác {@code
     * forgotPassword}, endpoint này ĐƯỢC PHÉP tiết lộ tài khoản không tồn tại
     * (404) vì OTP đã được gửi riêng ở bước trước — không tăng thêm rủi ro dò
     * email. Thành công thì thu hồi toàn bộ refresh token, không tự đăng nhập.
     */
    void resetPassword(ResetPasswordRequest request);
}
