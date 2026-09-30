package com.ticketbooking.payment.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketbooking.payment.dto.response.PaymentInitiateResponse;
import com.ticketbooking.payment.service.PaymentIdempotencyService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;

@Slf4j
@Service
public class PaymentIdempotencyServiceImpl implements PaymentIdempotencyService {

    private static final String KEY_PREFIX = "payment:idempotency:";
    private static final Duration TTL = Duration.ofMinutes(15);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public PaymentIdempotencyServiceImpl(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public Optional<PaymentInitiateResponse> findCachedResponse(String idempotencyKey) {
        try {
            String cached = redisTemplate.opsForValue().get(KEY_PREFIX + idempotencyKey);
            if (cached == null) {
                return Optional.empty();
            }
            return Optional.of(objectMapper.readValue(cached, PaymentInitiateResponse.class));
        } catch (Exception e) {
            // Lỗi đọc Redis/deserialize không được chặn luồng thanh toán chính —
            // coi như chưa từng thấy key này, để request đi tiếp bình thường.
            log.warn("Không đọc được idempotency key {}, bỏ qua cache: {}", idempotencyKey, e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public void saveResponse(String idempotencyKey, PaymentInitiateResponse response) {
        try {
            redisTemplate.opsForValue().set(
                    KEY_PREFIX + idempotencyKey, objectMapper.writeValueAsString(response), TTL);
        } catch (Exception e) {
            log.warn("Không lưu được idempotency key {}: {}", idempotencyKey, e.getMessage());
        }
    }
}
