package com.ticketbooking.notification.listener;

import com.ticketbooking.common.event.OtpEmailEvent;
import com.ticketbooking.notification.service.EmailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AuthOtpEmailListenerTest {

    @Mock
    private EmailService emailService;

    private AuthOtpEmailListener listener;

    @BeforeEach
    void setUp() {
        listener = new AuthOtpEmailListener(emailService, "support@ticketbooking.local");
    }

    @Test
    void onOtpRequested_emailVerification_usesVerifyEmailTemplate() {
        OtpEmailEvent event = OtpEmailEvent.builder()
                .email("customer@example.com")
                .otp("123456")
                .purpose("EMAIL_VERIFICATION")
                .expiresInMinutes(5)
                .build();

        listener.onOtpRequested(event);

        verify(emailService).sendTemplatedEmail(
                eq("EMAIL_VERIFICATION"), isNull(), eq("customer@example.com"),
                anyString(), eq("verify-email"), anyMap());
    }

    @Test
    void onOtpRequested_passwordReset_usesResetPasswordTemplate() {
        OtpEmailEvent event = OtpEmailEvent.builder()
                .email("customer@example.com")
                .otp("654321")
                .purpose("PASSWORD_RESET")
                .expiresInMinutes(5)
                .build();

        listener.onOtpRequested(event);

        verify(emailService).sendTemplatedEmail(
                eq("PASSWORD_RESET"), isNull(), eq("customer@example.com"),
                anyString(), eq("reset-password-otp"), anyMap());
    }
}
