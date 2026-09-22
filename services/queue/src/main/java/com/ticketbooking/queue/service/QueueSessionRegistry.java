package com.ticketbooking.queue.service;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Ánh xạ STOMP sessionId → (eventId, userId) mà session đó đã join, để
 * {@code SessionDisconnectEvent} biết cần {@code ZREM} user khỏi hàng chờ nào
 * (ngắt kết nối = mất chỗ ngay lập tức, xem docs/virtual-waiting-room.md §5)
 * thay vì phải chờ heartbeat TTL 15s hết hạn.
 */
@Component
public class QueueSessionRegistry {

    public record Session(UUID eventId, UUID userId) {
    }

    private final Map<String, Session> sessions = new ConcurrentHashMap<>();

    public void register(String sessionId, UUID eventId, UUID userId) {
        sessions.put(sessionId, new Session(eventId, userId));
    }

    public Session remove(String sessionId) {
        return sessions.remove(sessionId);
    }
}
