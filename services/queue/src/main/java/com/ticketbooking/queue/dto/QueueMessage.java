package com.ticketbooking.queue.dto;

import java.time.Instant;

/**
 * Payload đẩy qua WebSocket cho client (xem docs/virtual-waiting-room.md §7
 * "WebSocket Messages Schema"). Dùng chung 1 shape cho cả 3 loại message —
 * field nào không áp dụng cho {@code type} thì để {@code null}.
 */
public record QueueMessage(
        Type type,
        Long position,
        Long totalWaiting,
        Long estimatedWaitSeconds,
        String accessToken,
        Long expiresInSeconds,
        String message,
        Instant timestamp
) {

    public enum Type {
        POSITION_UPDATE,
        ADMITTED,
        EVENT_SOLD_OUT
    }

    public static QueueMessage positionUpdate(long position, long totalWaiting, long estimatedWaitSeconds) {
        return new QueueMessage(Type.POSITION_UPDATE, position, totalWaiting, estimatedWaitSeconds,
                null, null, null, Instant.now());
    }

    public static QueueMessage admitted(String accessToken, long expiresInSeconds) {
        return new QueueMessage(Type.ADMITTED, null, null, null, accessToken, expiresInSeconds,
                "Đến lượt bạn! Bạn có " + (expiresInSeconds / 60) + " phút để hoàn tất đặt vé.", Instant.now());
    }
}
