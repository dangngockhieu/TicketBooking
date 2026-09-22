package com.ticketbooking.queue.service;

import com.ticketbooking.queue.dto.request.QueueConfigUpdateRequest;
import com.ticketbooking.queue.model.QueueConfig;

import java.util.Set;
import java.util.UUID;

public interface QueueConfigService {

    QueueConfig getConfig(UUID eventId);

    QueueConfig updateConfig(UUID eventId, QueueConfigUpdateRequest request);

    void saveConfig(UUID eventId, QueueConfig config);

    /** Danh sách eventId đang được Scheduled Job theo dõi (đã có người vào hàng chờ hoặc đã được admin cấu hình). */
    Set<UUID> getTrackedEventIds();

    void trackEvent(UUID eventId);
}
