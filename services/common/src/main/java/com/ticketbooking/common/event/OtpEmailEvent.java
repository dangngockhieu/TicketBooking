package com.ticketbooking.common.event;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/**
 * Kafka topic {@code auth.otp-requested} — Auth Service bắn khi sinh OTP mới
 * (đăng ký/xác thực email hoặc quên mật khẩu), Notification Service lắng
 * nghe để gửi email thật (xem docs/technical-flows.md §0). {@code purpose}
 * dùng chuỗi thay vì tham chiếu trực tiếp enum {@code OtpPurpose} của
 * auth-service — common không phụ thuộc ngược lại bất kỳ service nào.
 */
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class OtpEmailEvent extends BaseEvent {

    private String email;

    private String otp;

    /** Giá trị: {@code EMAIL_VERIFICATION} hoặc {@code PASSWORD_RESET} (xem auth-service OtpPurpose). */
    private String purpose;

    private int expiresInMinutes;
}
