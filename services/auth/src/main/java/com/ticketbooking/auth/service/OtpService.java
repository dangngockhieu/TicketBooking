package com.ticketbooking.auth.service;

import java.util.UUID;

/**
 * Sinh, lưu (Redis TTL) và xác thực mã OTP 6 số. Dùng chung cho xác thực email
 * (đăng ký) và đặt lại mật khẩu quên (forgot-password) — phân biệt bằng
 * {@link OtpPurpose} để namespace Redis không đè lên nhau. Xem
 * docs/technical-flows.md §0 (repo gốc).
 */
public interface OtpService {

    /** Thời hạn OTP (phút) — dùng bởi {@code AuthServiceImpl} khi build {@code OtpEmailEvent}. */
    int OTP_TTL_MINUTES = 5;

    /**
     * Sinh OTP mới cho {@code accountId}, ghi đè OTP cũ (nếu có) trong Redis.
     * Áp dụng cooldown chống spam trước khi cho sinh lại.
     *
     * @return mã OTP 6 số vừa sinh (dùng để log/gửi email khi có notification-service)
     * @throws com.ticketbooking.common.exception.TooManyRequestsException nếu đang trong thời gian cooldown
     */
    String issueOtp(UUID accountId, OtpPurpose purpose);

    /**
     * Kiểm tra OTP có khớp và còn hạn hay không. Nếu khớp, xoá key khỏi Redis
     * (một lần dùng).
     *
     * @throws com.ticketbooking.common.exception.BadRequestException nếu OTP sai
     * @throws com.ticketbooking.common.exception.InvalidTokenException nếu OTP hết hạn hoặc không tồn tại
     */
    void verifyOtp(UUID accountId, String otp, OtpPurpose purpose);
}
