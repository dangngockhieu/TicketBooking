package com.ticketbooking.auth.service.impl;

import com.ticketbooking.auth.service.OtpService;
import com.ticketbooking.common.exception.BadRequestException;
import com.ticketbooking.common.exception.InvalidTokenException;
import com.ticketbooking.common.exception.TooManyRequestsException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.UUID;

@Slf4j
@Service
public class OtpServiceImpl implements OtpService {

    private static final String OTP_KEY_PREFIX = "email_verify:";
    private static final String COOLDOWN_KEY_PREFIX = "email_verify_cooldown:";
    private static final Duration OTP_TTL = Duration.ofMinutes(5);
    private static final Duration COOLDOWN_TTL = Duration.ofSeconds(60);

    private final StringRedisTemplate redisTemplate;
    private final SecureRandom secureRandom = new SecureRandom();

    public OtpServiceImpl(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public String issueOtp(UUID accountId) {
        String cooldownKey = COOLDOWN_KEY_PREFIX + accountId;
        Boolean cooldownActive = redisTemplate.hasKey(cooldownKey);
        if (Boolean.TRUE.equals(cooldownActive)) {
            throw new TooManyRequestsException(
                    "Bạn vừa yêu cầu gửi mã. Vui lòng đợi ít phút rồi thử lại.");
        }

        String otp = generateOtp();
        String otpKey = OTP_KEY_PREFIX + accountId;
        redisTemplate.opsForValue().set(otpKey, otp, OTP_TTL);
        redisTemplate.opsForValue().set(cooldownKey, "1", COOLDOWN_TTL);

        // ⏳ Gửi email OTP thật qua Kafka + notification-service chưa triển khai
        // (notification-service chưa tồn tại trong repo — xem docs/technical-flows.md §0.4).
        // Tạm thời log ra console để test thủ công ở môi trường dev.
        log.info("[DEV-ONLY] OTP xác thực email cho account {}: {}", accountId, otp);

        return otp;
    }

    @Override
    public void verifyOtp(UUID accountId, String otp) {
        String otpKey = OTP_KEY_PREFIX + accountId;
        String storedOtp = redisTemplate.opsForValue().get(otpKey);

        if (storedOtp == null) {
            throw new InvalidTokenException(
                    "Mã OTP đã hết hạn hoặc không tồn tại. Vui lòng yêu cầu gửi lại mã.");
        }

        if (!storedOtp.equals(otp)) {
            throw new BadRequestException("Mã OTP không chính xác.");
        }

        redisTemplate.delete(otpKey);
    }

    private String generateOtp() {
        int value = secureRandom.nextInt(1_000_000);
        return String.format("%06d", value);
    }
}
