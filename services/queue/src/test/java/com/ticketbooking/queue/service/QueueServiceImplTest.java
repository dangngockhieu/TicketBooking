package com.ticketbooking.queue.service;

import com.ticketbooking.queue.dto.QueueMessage;
import com.ticketbooking.queue.dto.response.QueueAccessResponse;
import com.ticketbooking.queue.model.QueueConfig;
import com.ticketbooking.queue.repository.QueueRedisKeys;
import com.ticketbooking.queue.service.impl.QueueServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.ZSetOperations;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class QueueServiceImplTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private QueueConfigService configService;

    @Mock
    private ZSetOperations<String, String> zSetOperations;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private QueueServiceImpl queueService;

    private UUID eventId;
    private UUID userId;

    @BeforeEach
    void setUp() {
        queueService = new QueueServiceImpl(redisTemplate, configService);
        eventId = UUID.randomUUID();
        userId = UUID.randomUUID();
    }

    @Test
    void join_queueDisabled_admitsImmediately() {
        when(configService.getConfig(eventId)).thenReturn(QueueConfig.builder()
                .enabled(false).maxConcurrent(500).autoEnableThreshold(1000).purchaseWindowSeconds(600).build());
        when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        QueueMessage result = queueService.join(eventId, userId);

        assertEquals(QueueMessage.Type.ADMITTED, result.type());
        assertNotNull(result.accessToken());
        verify(zSetOperations).add(eq(QueueRedisKeys.active(eventId)), eq(userId.toString()), anyDouble());
        verify(valueOperations).set(eq(QueueRedisKeys.token(eventId, userId)), anyString(), any(java.time.Duration.class));
        verify(configService).trackEvent(eventId);
    }

    @Test
    void join_queueEnabled_addsToWaitingAndReturnsPosition() {
        when(configService.getConfig(eventId)).thenReturn(QueueConfig.builder()
                .enabled(true).maxConcurrent(500).autoEnableThreshold(1000).purchaseWindowSeconds(600).build());
        when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(zSetOperations.rank(QueueRedisKeys.waiting(eventId), userId.toString())).thenReturn(4L);
        when(zSetOperations.zCard(QueueRedisKeys.waiting(eventId))).thenReturn(120L);

        QueueMessage result = queueService.join(eventId, userId);

        assertEquals(QueueMessage.Type.POSITION_UPDATE, result.type());
        assertEquals(5L, result.position());
        assertEquals(120L, result.totalWaiting());
        verify(zSetOperations).addIfAbsent(eq(QueueRedisKeys.waiting(eventId)), eq(userId.toString()), anyDouble());
    }

    @Test
    void currentPosition_userNotWaiting_returnsNull() {
        when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
        when(zSetOperations.rank(QueueRedisKeys.waiting(eventId), userId.toString())).thenReturn(null);

        assertNull(queueService.currentPosition(eventId, userId));
    }

    @Test
    void checkAccess_queueDisabled_alwaysValid() {
        when(configService.getConfig(eventId)).thenReturn(QueueConfig.DEFAULT);

        QueueAccessResponse response = queueService.checkAccess(eventId, userId, null);

        assertFalse(response.enabled());
        assertTrue(response.valid());
    }

    @Test
    void checkAccess_queueEnabled_validatesStoredToken() {
        when(configService.getConfig(eventId)).thenReturn(QueueConfig.builder()
                .enabled(true).maxConcurrent(500).autoEnableThreshold(1000).purchaseWindowSeconds(600).build());
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(QueueRedisKeys.token(eventId, userId))).thenReturn("stored-token");

        assertTrue(queueService.checkAccess(eventId, userId, "stored-token").valid());
        assertFalse(queueService.checkAccess(eventId, userId, "wrong-token").valid());
        assertFalse(queueService.checkAccess(eventId, userId, null).valid());
    }
}
