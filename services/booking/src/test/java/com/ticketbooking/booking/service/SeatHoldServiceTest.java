package com.ticketbooking.booking.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketbooking.common.exception.ConflictException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.Duration;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SeatHoldServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private SeatHoldService seatHoldService;

    private UUID eventId;
    private UUID ticketClassId;
    private UUID customerId;

    @BeforeEach
    void setUp() {
        seatHoldService = new SeatHoldService(redisTemplate, new ObjectMapper());
        eventId = UUID.randomUUID();
        ticketClassId = UUID.randomUUID();
        customerId = UUID.randomUUID();
    }

    @Test
    void hold_succeeds_whenScriptReturnsNewCount() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any(), any())).thenReturn(5L);

        assertDoesNotThrow(() -> seatHoldService.hold(eventId, ticketClassId, 5, 200));
    }

    @Test
    void hold_throwsSoldOut_whenNoTicketsRemain() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any(), any())).thenReturn(-1L);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(SeatHoldService.holdCountKey(eventId, ticketClassId))).thenReturn("200");

        ConflictException ex = assertThrows(ConflictException.class,
                () -> seatHoldService.hold(eventId, ticketClassId, 5, 200));
        assertTrue(ex.getMessage().contains("hết vé"));
    }

    @Test
    void hold_throwsInsufficientTickets_whenPartiallyAvailable() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any(), any())).thenReturn(-1L);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(SeatHoldService.holdCountKey(eventId, ticketClassId))).thenReturn("197");

        ConflictException ex = assertThrows(ConflictException.class,
                () -> seatHoldService.hold(eventId, ticketClassId, 5, 200));
        assertTrue(ex.getMessage().contains("còn 3"));
    }

    @Test
    void release_decrementsHoldCountByQuantity() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        seatHoldService.release(eventId, ticketClassId, 3);

        verify(valueOperations).increment(SeatHoldService.holdCountKey(eventId, ticketClassId), -3);
    }

    @Test
    void markHeld_writesJsonValueWithTtl() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        UUID bookingId = UUID.randomUUID();

        seatHoldService.markHeld(eventId, ticketClassId, customerId, bookingId, 2, Duration.ofSeconds(600));

        verify(valueOperations).set(
                eq(SeatHoldService.seatHoldKey(eventId, ticketClassId, customerId)),
                contains(bookingId.toString()),
                eq(Duration.ofSeconds(600)));
    }

    @Test
    void clearHeld_deletesSeatHoldKey() {
        seatHoldService.clearHeld(eventId, ticketClassId, customerId);

        verify(redisTemplate).delete(SeatHoldService.seatHoldKey(eventId, ticketClassId, customerId));
    }
}
