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

import java.util.Set;
import java.util.UUID;

/**
 * Tự động bật/tắt phòng chờ theo tải (xem docs/virtual-waiting-room.md §4.2).
 * Chạy mỗi 1 giây, độc lập với cấu hình thủ công của admin — admin vẫn có
 * thể set {@code enabled} qua API, nhưng vòng lặp này sẽ ghi đè lại theo
 * ngưỡng tải ở lần chạy tiếp theo (đúng như pseudo-code trong tài liệu thiết
 * kế: không có khái niệm "khóa" giữa auto và manual).
 */
@Slf4j
@Component
public class QueueAutoToggleScheduler {

    private final StringRedisTemplate redisTemplate;
    private final QueueConfigService configService;
    private final QueueService queueService;
    private final SimpMessagingTemplate messagingTemplate;

    public QueueAutoToggleScheduler(
            StringRedisTemplate redisTemplate,
            QueueConfigService configService,
            QueueService queueService,
            SimpMessagingTemplate messagingTemplate) {
        this.redisTemplate = redisTemplate;
        this.configService = configService;
        this.queueService = queueService;
        this.messagingTemplate = messagingTemplate;
    }

    @Scheduled(fixedRate = 1000)
    public void checkAndToggleQueue() {
        for (UUID eventId : configService.getTrackedEventIds()) {
            QueueConfig config = configService.getConfig(eventId);

            Long activeCount = redisTemplate.opsForZSet().zCard(QueueRedisKeys.active(eventId));
            Long waitingCount = redisTemplate.opsForZSet().zCard(QueueRedisKeys.waiting(eventId));
            long active = activeCount != null ? activeCount : 0;
            long waiting = waitingCount != null ? waitingCount : 0;
            long totalDemand = active + waiting;

            if (!config.isEnabled() && totalDemand > config.getAutoEnableThreshold()) {
                configService.saveConfig(eventId, QueueConfig.builder()
                        .enabled(true)
                        .maxConcurrent(config.getMaxConcurrent())
                        .autoEnableThreshold(config.getAutoEnableThreshold())
                        .purchaseWindowSeconds(config.getPurchaseWindowSeconds())
                        .build());
                log.warn("Queue AUTO-ENABLED cho sự kiện {} (demand: {})", eventId, totalDemand);
                continue;
            }

            if (config.isEnabled() && active < config.getAutoEnableThreshold() * 0.3) {
                configService.saveConfig(eventId, QueueConfig.builder()
                        .enabled(false)
                        .maxConcurrent(config.getMaxConcurrent())
                        .autoEnableThreshold(config.getAutoEnableThreshold())
                        .purchaseWindowSeconds(config.getPurchaseWindowSeconds())
                        .build());
                admitAllWaiting(eventId);
                log.info("Queue AUTO-DISABLED cho sự kiện {} (active: {})", eventId, active);
            }
        }
    }

    /** Tải đã giảm — cho toàn bộ người đang chờ vào thẳng (xem §4.2). */
    private void admitAllWaiting(UUID eventId) {
        Set<String> waitingUsers = redisTemplate.opsForZSet().range(QueueRedisKeys.waiting(eventId), 0, -1);
        if (waitingUsers == null || waitingUsers.isEmpty()) {
            return;
        }
        for (String userIdStr : waitingUsers) {
            UUID userId = UUID.fromString(userIdStr);
            QueueMessage admitted = queueService.admitUser(eventId, userId);
            messagingTemplate.convertAndSendToUser(userId.toString(), "/queue/" + eventId + "/updates", admitted);
        }
    }
}
