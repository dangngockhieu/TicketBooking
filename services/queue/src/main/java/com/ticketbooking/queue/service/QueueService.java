package com.ticketbooking.queue.service;

import com.ticketbooking.queue.dto.QueueMessage;
import com.ticketbooking.queue.dto.response.QueueAccessResponse;

import java.util.UUID;

public interface QueueService {

    boolean isEnabled(UUID eventId);

    /**
     * Cho user vào hàng chờ (hoặc cấp quyền vào thẳng nếu queue đang tắt cho
     * sự kiện này) — trả về message đầu tiên gửi ngay cho client.
     */
    QueueMessage join(UUID eventId, UUID userId);

    /** Reset TTL heartbeat — mất kết nối > 15s bị coi là rời hàng chờ (xem docs/virtual-waiting-room.md §5). */
    void heartbeat(UUID eventId, UUID userId);

    /** Rời hàng chờ ngay lập tức (đóng tab/mất WebSocket) — dual-layer cùng với heartbeat TTL. */
    void leave(UUID eventId, UUID userId);

    /** Vị trí hiện tại — {@code null} nếu user không còn trong hàng chờ (đã được ADMITTED hoặc chưa từng join). */
    QueueMessage currentPosition(UUID eventId, UUID userId);

    QueueAccessResponse checkAccess(UUID eventId, UUID userId, String accessToken);

    /**
     * Chuyển user từ waiting sang active + cấp access token — dùng bởi
     * {@code QueueAdmissionScheduler} (đến lượt tự nhiên) và
     * {@code QueueAutoToggleScheduler} (auto-disable, cho tất cả vào luôn).
     */
    QueueMessage admitUser(UUID eventId, UUID userId);
}
