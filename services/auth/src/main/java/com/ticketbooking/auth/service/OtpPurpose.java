package com.ticketbooking.auth.service;

/**
 * Phân biệt namespace Redis giữa các luồng dùng chung {@link OtpService},
 * để OTP xác thực email đăng ký và OTP đặt lại mật khẩu không đè lên nhau
 * dù cùng một tài khoản.
 */
public enum OtpPurpose {
    EMAIL_VERIFICATION,
    PASSWORD_RESET
}
