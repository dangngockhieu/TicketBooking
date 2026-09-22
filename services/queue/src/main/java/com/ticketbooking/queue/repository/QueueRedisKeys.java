package com.ticketbooking.queue.repository;

import java.util.UUID;

/**
 * Cấu trúc key Redis cho Virtual Waiting Room (xem docs/virtual-waiting-room.md §3).
 * Dùng chung Redis instance với Catalog/Booking Service (đã bật
 * {@code notify-keyspace-events Ex} trong docker-compose.yml).
 */
public final class QueueRedisKeys {

    /** Set các eventId đã từng có người vào hàng chờ/được cấu hình — dùng để các Scheduled Job biết cần quét event nào. */
    public static final String TRACKED_EVENTS = "queue:tracked-events";

    private QueueRedisKeys() {
    }

    public static String config(UUID eventId) {
        return "queue:config:" + eventId;
    }

    public static String waiting(UUID eventId) {
        return "queue:waiting:" + eventId;
    }

    public static String active(UUID eventId) {
        return "queue:active:" + eventId;
    }

    public static String token(UUID eventId, UUID userId) {
        return "queue:token:" + eventId + ":" + userId;
    }

    public static String heartbeat(UUID eventId, UUID userId) {
        return "queue:heartbeat:" + eventId + ":" + userId;
    }
}
