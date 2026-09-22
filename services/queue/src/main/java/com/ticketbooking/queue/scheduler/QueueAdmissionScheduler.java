package com.ticketbooking.queue.scheduler;

import com.ticketbooking.queue.dto.QueueMessage;
import com.ticketbooking.queue.model.QueueConfig;
import com.ticketbooking.queue.repository.QueueRedisKeys;
import com.ticketbooking.queue.service.QueueConfigService;
import com.ticketbooking.queue.service.QueueService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * Cho người vào từng đợt + phát vị trí hàng chờ real-time (xem
 * docs/virtual-waiting-room.md §4.3, §7). Một instance Queue Service quét
 * toàn bộ event đang được theo dõi — đủ dùng ở quy mô đồ án; ở quy mô lớn hơn
 * việc này cần được phân vùng theo eventId giữa nhiều instance (ví dụ dùng
 * Redis lock theo eventId) để tránh admit trùng khi chạy nhiều instance.
 */
@Slf4j
@Component
public class QueueAdmissionScheduler {

    private final StringRedisTemplate redisTemplate;
    private final QueueConfigService configService;
    private final QueueService queueService;
    private final SimpMessagingTemplate messagingTemplate;

    public QueueAdmissionScheduler(
            StringRedisTemplate redisTemplate,
            QueueConfigService configService,
            QueueService queueService,
            SimpMessagingTemplate messagingTemplate) {
        this.redisTemplate = redisTemplate;
        this.configService = configService;
        this.queueService = queueService;
        this.messagingTemplate = messagingTemplate;
    }

    /** Mỗi 2 giây: dọn active hết hạn rồi cho đúng số slot trống tiếp theo vào (xem §4.3). */
    @Scheduled(fixedRate = 2000)
    public void admitNextBatch() {
        for (UUID eventId : configService.getTrackedEventIds()) {
            QueueConfig config = configService.getConfig(eventId);
            if (!config.isEnabled()) {
                continue;
            }

            String activeKey = QueueRedisKeys.active(eventId);
            redisTemplate.opsForZSet().removeRangeByScore(activeKey, 0, Instant.now().getEpochSecond());

            Long currentActive = redisTemplate.opsForZSet().zCard(activeKey);
            long availableSlots = config.getMaxConcurrent() - (currentActive != null ? currentActive : 0);
            if (availableSlots <= 0) {
                continue;
            }

            Set<String> nextUsers = redisTemplate.opsForZSet()
                    .range(QueueRedisKeys.waiting(eventId), 0, availableSlots - 1);
            if (nextUsers == null) {
                continue;
            }

            for (String userIdStr : nextUsers) {
                UUID userId = UUID.fromString(userIdStr);
                QueueMessage admitted = queueService.admitUser(eventId, userId);
                sendToUser(userId, eventId, admitted);
                log.info("Queue: admitted user {} vào sự kiện {} (accessToken cấp).", userId, eventId);
            }
        }
    }

    /** Mỗi 3 giây: cập nhật vị trí cho toàn bộ người còn trong hàng chờ (xem §7). */
    @Scheduled(fixedRate = 3000)
    public void broadcastPositions() {
        for (UUID eventId : configService.getTrackedEventIds()) {
            Set<String> waitingUsers = redisTemplate.opsForZSet().range(QueueRedisKeys.waiting(eventId), 0, -1);
            if (waitingUsers == null || waitingUsers.isEmpty()) {
                continue;
            }
            for (String userIdStr : waitingUsers) {
                UUID userId = UUID.fromString(userIdStr);
                QueueMessage position = queueService.currentPosition(eventId, userId);
                if (position != null) {
                    sendToUser(userId, eventId, position);
                }
            }
        }
    }

    private void sendToUser(UUID userId, UUID eventId, QueueMessage message) {
        messagingTemplate.convertAndSendToUser(userId.toString(), "/queue/" + eventId + "/updates", message);
    }
}
