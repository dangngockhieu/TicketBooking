package com.ticketbooking.booking.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketbooking.common.exception.ConflictException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Atomic Seat Hold (xem docs/database-schema.md §4 "Redis Keys",
 * docs/development-plan.md GĐ3 mục 1) — chống overbooking bằng Redis Lua
 * script check-and-increment thay vì lock ở tầng ứng dụng.
 */
@Slf4j
@Service
public class SeatHoldService {

    private static final String HOLD_COUNT_PREFIX = "hold_count:";
    private static final String SEAT_HOLD_PREFIX = "seat_hold:";

    private static final RedisScript<Long> HOLD_SCRIPT = buildHoldScript();

    private static RedisScript<Long> buildHoldScript() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("scripts/seat_hold.lua"));
        script.setResultType(Long.class);
        return script;
    }

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public SeatHoldService(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * Giữ {@code quantity} vé cho {@code ticketClassId}, atomic so với mọi request
     * khác đang tranh cùng hạng vé. Ném {@link ConflictException} (SOLD_OUT hoặc
     * INSUFFICIENT_TICKETS) nếu không đủ vé — không thay đổi gì trong Redis.
     */
    public void hold(UUID eventId, UUID ticketClassId, int quantity, int availableQuantity) {
        String key = holdCountKey(eventId, ticketClassId);
        Long result = redisTemplate.execute(HOLD_SCRIPT, List.of(key),
                String.valueOf(quantity), String.valueOf(availableQuantity));

        if (result == null || result == -1L) {
            long current = currentHoldCount(eventId, ticketClassId);
            long remaining = availableQuantity - current;
            if (remaining <= 0) {
                throw new ConflictException("Hạng vé đã hết vé.");
            }
            throw new ConflictException(
                    "Không đủ vé, yêu cầu " + quantity + ", còn " + remaining + ".");
        }
    }

    /** Trả lại {@code quantity} vé đã giữ — dùng khi hủy/rollback đơn hàng. */
    public void release(UUID eventId, UUID ticketClassId, int quantity) {
        redisTemplate.opsForValue().increment(holdCountKey(eventId, ticketClassId), -quantity);
    }

    /**
     * Ghi key {@code seat_hold:*} làm safety-net — Redis Keyspace Notification
     * bắt sự kiện {@code expired} của key này để kích hoạt nhả ghế sớm, song
     * song với Scheduled Job quét {@code bookings.expired_at} (xem BookingExpirationScheduler).
     */
    public void markHeld(UUID eventId, UUID ticketClassId, UUID customerId, UUID bookingId, int quantity, Duration ttl) {
        try {
            String value = objectMapper.writeValueAsString(Map.of("bookingId", bookingId.toString(), "quantity", quantity));
            redisTemplate.opsForValue().set(seatHoldKey(eventId, ticketClassId, customerId), value, ttl);
        } catch (Exception e) {
            log.warn("Không thể ghi seat_hold key cho booking {}: {}", bookingId, e.getMessage());
        }
    }

    public void clearHeld(UUID eventId, UUID ticketClassId, UUID customerId) {
        redisTemplate.delete(seatHoldKey(eventId, ticketClassId, customerId));
    }

    private long currentHoldCount(UUID eventId, UUID ticketClassId) {
        String value = redisTemplate.opsForValue().get(holdCountKey(eventId, ticketClassId));
        return value == null ? 0 : Long.parseLong(value);
    }

    public static String holdCountKey(UUID eventId, UUID ticketClassId) {
        return HOLD_COUNT_PREFIX + eventId + ":" + ticketClassId;
    }

    public static String seatHoldKey(UUID eventId, UUID ticketClassId, UUID customerId) {
        return SEAT_HOLD_PREFIX + eventId + ":" + ticketClassId + ":" + customerId;
    }
}
