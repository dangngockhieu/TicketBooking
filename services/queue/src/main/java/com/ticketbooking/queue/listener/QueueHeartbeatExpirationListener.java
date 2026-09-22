package com.ticketbooking.queue.listener;

import com.ticketbooking.queue.service.QueueService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Safety-net chính cho "ngắt kết nối = mất chỗ" (xem docs/virtual-waiting-room.md
 * §5) — bắt sự kiện Redis keyspace notification {@code expired} của key
 * {@code queue:heartbeat:{eventId}:{userId}} khi client dừng gửi heartbeat
 * quá 15 giây (mất mạng, đóng tab mà không kịp bắn sự kiện disconnect, v.v.).
 */
@Slf4j
@Component
public class QueueHeartbeatExpirationListener implements MessageListener {

    private static final String HEARTBEAT_PREFIX = "queue:heartbeat:";

    private final QueueService queueService;

    public QueueHeartbeatExpirationListener(QueueService queueService) {
        this.queueService = queueService;
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        String expiredKey = new String(message.getBody(), StandardCharsets.UTF_8);
        if (!expiredKey.startsWith(HEARTBEAT_PREFIX)) {
            return;
        }

        String[] parts = expiredKey.substring(HEARTBEAT_PREFIX.length()).split(":");
        if (parts.length != 2) {
            log.warn("queue:heartbeat key không đúng định dạng, bỏ qua: {}", expiredKey);
            return;
        }

        try {
            UUID eventId = UUID.fromString(parts[0]);
            UUID userId = UUID.fromString(parts[1]);
            queueService.leave(eventId, userId);
        } catch (IllegalArgumentException e) {
            log.warn("Không parse được queue:heartbeat key '{}': {}", expiredKey, e.getMessage());
        }
    }
}
