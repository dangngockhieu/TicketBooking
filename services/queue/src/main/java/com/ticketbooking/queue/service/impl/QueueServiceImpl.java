package com.ticketbooking.queue.service.impl;

import com.ticketbooking.queue.dto.QueueMessage;
import com.ticketbooking.queue.dto.response.QueueAccessResponse;
import com.ticketbooking.queue.model.QueueConfig;
import com.ticketbooking.queue.repository.QueueRedisKeys;
import com.ticketbooking.queue.service.QueueConfigService;
import com.ticketbooking.queue.service.QueueService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Cốt lõi Virtual Waiting Room (xem docs/virtual-waiting-room.md §3, §4) —
 * mọi trạng thái nằm ở Redis Sorted Set/String, không có Postgres vì đây là
 * dữ liệu vận hành tạm thời (10 phút hold + thời gian chờ), không cần bền
 * vững qua restart.
 */
@Slf4j
@Service
public class QueueServiceImpl implements QueueService {

    /** Theo docs/virtual-waiting-room.md §5 — mất kết nối > 15s coi như rời hàng chờ. */
    private static final Duration HEARTBEAT_TTL = Duration.ofSeconds(15);

    /** Trung bình mỗi người mất ~5 phút để mua xong hoặc hết hạn (xem §6 công thức ước tính). */
    private static final double AVG_PURCHASE_SECONDS = 300.0;

    private final StringRedisTemplate redisTemplate;
    private final QueueConfigService configService;

    public QueueServiceImpl(StringRedisTemplate redisTemplate, QueueConfigService configService) {
        this.redisTemplate = redisTemplate;
        this.configService = configService;
    }

    @Override
    public boolean isEnabled(UUID eventId) {
        return configService.getConfig(eventId).isEnabled();
    }

    @Override
    public QueueMessage join(UUID eventId, UUID userId) {
        configService.trackEvent(eventId);
        QueueConfig config = configService.getConfig(eventId);

        if (!config.isEnabled()) {
            return admitUser(eventId, userId);
        }

        redisTemplate.opsForZSet().addIfAbsent(QueueRedisKeys.waiting(eventId), userId.toString(),
                Instant.now().toEpochMilli());
        refreshHeartbeat(eventId, userId);

        QueueMessage position = currentPosition(eventId, userId);
        return position != null ? position : admitUser(eventId, userId);
    }

    @Override
    public void heartbeat(UUID eventId, UUID userId) {
        refreshHeartbeat(eventId, userId);
    }

    @Override
    public void leave(UUID eventId, UUID userId) {
        redisTemplate.opsForZSet().remove(QueueRedisKeys.waiting(eventId), userId.toString());
        redisTemplate.delete(QueueRedisKeys.heartbeat(eventId, userId));
    }

    @Override
    public QueueMessage currentPosition(UUID eventId, UUID userId) {
        Long rank = redisTemplate.opsForZSet().rank(QueueRedisKeys.waiting(eventId), userId.toString());
        if (rank == null) {
            return null;
        }
        long position = rank + 1;
        Long total = redisTemplate.opsForZSet().zCard(QueueRedisKeys.waiting(eventId));
        long totalWaiting = total != null ? total : position;
        long eta = estimateWaitSeconds(configService.getConfig(eventId), position);
        return QueueMessage.positionUpdate(position, totalWaiting, eta);
    }

    @Override
    public QueueAccessResponse checkAccess(UUID eventId, UUID userId, String accessToken) {
        boolean enabled = isEnabled(eventId);
        if (!enabled) {
            return new QueueAccessResponse(false, true);
        }
        String stored = redisTemplate.opsForValue().get(QueueRedisKeys.token(eventId, userId));
        boolean valid = stored != null && accessToken != null && stored.equals(accessToken);
        return new QueueAccessResponse(true, valid);
    }

    /**
     * Cấp quyền vào thẳng trang đặt vé — dùng khi queue tắt, đến lượt tự
     * nhiên trong hàng chờ, hoặc admin auto-disable đẩy hết hàng chờ vào
     * (xem §4.2, §4.3).
     */
    @Override
    public QueueMessage admitUser(UUID eventId, UUID userId) {
        QueueConfig config = configService.getConfig(eventId);
        redisTemplate.opsForZSet().remove(QueueRedisKeys.waiting(eventId), userId.toString());
        redisTemplate.delete(QueueRedisKeys.heartbeat(eventId, userId));

        long expiresInSeconds = config.getPurchaseWindowSeconds();
        redisTemplate.opsForZSet().add(QueueRedisKeys.active(eventId), userId.toString(),
                Instant.now().plusSeconds(expiresInSeconds).getEpochSecond());

        String accessToken = UUID.randomUUID().toString();
        redisTemplate.opsForValue().set(QueueRedisKeys.token(eventId, userId), accessToken,
                Duration.ofSeconds(expiresInSeconds));

        return QueueMessage.admitted(accessToken, expiresInSeconds);
    }

    private void refreshHeartbeat(UUID eventId, UUID userId) {
        redisTemplate.opsForValue().set(QueueRedisKeys.heartbeat(eventId, userId), "alive", HEARTBEAT_TTL);
    }

    private long estimateWaitSeconds(QueueConfig config, long position) {
        double slotsPerSecond = config.getMaxConcurrent() / AVG_PURCHASE_SECONDS;
        return (long) (position / slotsPerSecond);
    }
}
