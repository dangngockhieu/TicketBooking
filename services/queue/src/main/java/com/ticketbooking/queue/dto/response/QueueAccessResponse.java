package com.ticketbooking.queue.dto.response;

/**
 * Trả lời cho Booking Service khi kiểm tra {@code accessToken} trước khi tạo
 * đơn hàng (xem docs/virtual-waiting-room.md §8). {@code valid} luôn
 * {@code true} khi {@code enabled=false} vì không có gate nào để vượt qua.
 */
public record QueueAccessResponse(boolean enabled, boolean valid) {
}
