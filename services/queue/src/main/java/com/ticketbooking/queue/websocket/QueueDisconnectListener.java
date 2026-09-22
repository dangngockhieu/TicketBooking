package com.ticketbooking.queue.websocket;

import com.ticketbooking.queue.service.QueueService;
import com.ticketbooking.queue.service.QueueSessionRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

/**
 * Ngắt kết nối WebSocket = mất chỗ ngay lập tức (dual-layer cùng heartbeat
 * TTL 15s, xem docs/virtual-waiting-room.md §5 — tương tự triết lý dual-layer
 * auto-release của Booking Service ở seat hold).
 */
@Slf4j
@Component
public class QueueDisconnectListener {

    private final QueueSessionRegistry sessionRegistry;
    private final QueueService queueService;

    public QueueDisconnectListener(QueueSessionRegistry sessionRegistry, QueueService queueService) {
        this.sessionRegistry = sessionRegistry;
        this.queueService = queueService;
    }

    @EventListener
    public void onDisconnect(SessionDisconnectEvent event) {
        QueueSessionRegistry.Session session = sessionRegistry.remove(event.getSessionId());
        if (session != null) {
            queueService.leave(session.eventId(), session.userId());
            log.info("Queue: user {} ngắt kết nối, rời hàng chờ sự kiện {}.", session.userId(), session.eventId());
        }
    }
}
