package com.ticketbooking.notification.listener;

import com.ticketbooking.common.event.OtpEmailEvent;
import com.ticketbooking.notification.service.EmailService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.Year;
import java.util.Map;

/**
 * Consumer Kafka {@code auth.otp-requested} — gửi email OTP thật cho luồng
 * xác thực email (đăng ký) và quên mật khẩu (xem docs/technical-flows.md §0,
 * TicketBooking-Frontend/email-templates/README.md cho danh sách biến).
 */
@Slf4j
@Component
public class AuthOtpEmailListener {

    private static final String TEMPLATE_VERIFY_EMAIL = "verify-email";
    private static final String TEMPLATE_RESET_PASSWORD = "reset-password-otp";

    private final EmailService emailService;
    private final String supportEmail;

    public AuthOtpEmailListener(EmailService emailService, @Value("${notification.mail.support-email}") String supportEmail) {
        this.emailService = emailService;
        this.supportEmail = supportEmail;
    }

    @KafkaListener(topics = "auth.otp-requested")
    public void onOtpRequested(OtpEmailEvent event) {
        boolean isPasswordReset = "PASSWORD_RESET".equals(event.getPurpose());
        String template = isPasswordReset ? TEMPLATE_RESET_PASSWORD : TEMPLATE_VERIFY_EMAIL;
        String subject = isPasswordReset
                ? "Mã đặt lại mật khẩu TicketBooking"
                : "Xác thực tài khoản TicketBooking";

        Map<String, Object> variables = Map.of(
                "email", event.getEmail(),
                "otp", event.getOtp(),
                "expiresInMinutes", event.getExpiresInMinutes(),
                "supportEmail", supportEmail,
                "year", Year.now().getValue());

        // Không dedupe OTP — mỗi lần yêu cầu (kể cả resend) đều là hành động
        // hợp lệ của người dùng, không phải Kafka redeliver trùng lặp.
        emailService.sendTemplatedEmail(event.getPurpose(), null, event.getEmail(), subject, template, variables);
    }
}
