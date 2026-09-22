package com.ticketbooking.queue.dto.response;

import com.ticketbooking.queue.model.QueueConfig;

import java.util.UUID;

public record QueueConfigResponse(
        UUID eventId,
        boolean enabled,
        int maxConcurrent,
        int autoEnableThreshold,
        int purchaseWindowSeconds
) {
    public static QueueConfigResponse from(UUID eventId, QueueConfig config) {
        return new QueueConfigResponse(eventId, config.isEnabled(), config.getMaxConcurrent(),
                config.getAutoEnableThreshold(), config.getPurchaseWindowSeconds());
    }
}
