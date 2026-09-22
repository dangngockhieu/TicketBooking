package com.ticketbooking.queue.websocket;

import com.ticketbooking.queue.dto.QueueMessage;
import com.ticketbooking.queue.service.QueueService;
import com.ticketbooking.queue.service.QueueSessionRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.UUID;

/**
 * Client → Server STOMP messages (xem docs/virtual-waiting-room.md §7 "Client
 * → Server"). Client publish tới {@code /app/queue/{eventId}/join} ngay sau
 * khi kết nối, rồi {@code /app/queue/{eventId}/heartbeat} mỗi 10 giây; server
 * trả lời (bất đồng bộ) qua user destination {@code /user/queue/{eventId}/updates}.
 */
@Slf4j
@Controller
public class QueueWebSocketController {

    private final QueueService queueService;
    private final QueueSessionRegistry sessionRegistry;
    private final SimpMessagingTemplate messagingTemplate;

    public QueueWebSocketController(
            QueueService queueService, QueueSessionRegistry sessionRegistry, SimpMessagingTemplate messagingTemplate) {
        this.queueService = queueService;
        this.sessionRegistry = sessionRegistry;
        this.messagingTemplate = messagingTemplate;
    }

    @MessageMapping("/queue/{eventId}/join")
    public void join(@DestinationVariable UUID eventId, Principal principal, SimpMessageHeaderAccessor headerAccessor) {
        UUID userId = UUID.fromString(principal.getName());
        sessionRegistry.register(headerAccessor.getSessionId(), eventId, userId);

        QueueMessage message = queueService.join(eventId, userId);
        messagingTemplate.convertAndSendToUser(userId.toString(), "/queue/" + eventId + "/updates", message);
    }

    @MessageMapping("/queue/{eventId}/heartbeat")
    public void heartbeat(@DestinationVariable UUID eventId, Principal principal) {
        queueService.heartbeat(eventId, UUID.fromString(principal.getName()));
    }
}
