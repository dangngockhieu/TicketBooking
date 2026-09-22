package com.ticketbooking.queue.dto.response;

import java.util.UUID;

/** Frontend gọi trước khi mở kết nối WebSocket để biết có cần vào phòng chờ hay không (xem docs/virtual-waiting-room.md §4.1). */
public record QueueStatusResponse(UUID eventId, boolean queueRequired) {
}
