package com.ticketbooking.queue.service.impl;

import com.ticketbooking.queue.dto.request.QueueConfigUpdateRequest;
import com.ticketbooking.queue.model.QueueConfig;
import com.ticketbooking.queue.repository.QueueRedisKeys;
import com.ticketbooking.queue.service.QueueConfigService;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class QueueConfigServiceImpl implements QueueConfigService {

    private final StringRedisTemplate redisTemplate;
    private final HashOperations<String, String, String> hashOps;

    public QueueConfigServiceImpl(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.hashOps = redisTemplate.opsForHash();
    }

    @Override
    public QueueConfig getConfig(UUID eventId) {
        Map<String, String> fields = hashOps.entries(QueueRedisKeys.config(eventId));
        if (fields.isEmpty()) {
            return QueueConfig.DEFAULT;
        }
        return QueueConfig.builder()
                .enabled(Boolean.parseBoolean(fields.getOrDefault("enabled", "false")))
                .maxConcurrent(Integer.parseInt(fields.getOrDefault("maxConcurrent",
                        String.valueOf(QueueConfig.DEFAULT.getMaxConcurrent()))))
                .autoEnableThreshold(Integer.parseInt(fields.getOrDefault("autoEnableThreshold",
                        String.valueOf(QueueConfig.DEFAULT.getAutoEnableThreshold()))))
                .purchaseWindowSeconds(Integer.parseInt(fields.getOrDefault("purchaseWindowSeconds",
                        String.valueOf(QueueConfig.DEFAULT.getPurchaseWindowSeconds()))))
                .build();
    }

    @Override
    public QueueConfig updateConfig(UUID eventId, QueueConfigUpdateRequest request) {
        QueueConfig current = getConfig(eventId);
        QueueConfig updated = QueueConfig.builder()
                .enabled(request.enabled() != null ? request.enabled() : current.isEnabled())
                .maxConcurrent(request.maxConcurrent() != null ? request.maxConcurrent() : current.getMaxConcurrent())
                .autoEnableThreshold(request.autoEnableThreshold() != null
                        ? request.autoEnableThreshold() : current.getAutoEnableThreshold())
                .purchaseWindowSeconds(request.purchaseWindowSeconds() != null
                        ? request.purchaseWindowSeconds() : current.getPurchaseWindowSeconds())
                .build();
        saveConfig(eventId, updated);
        return updated;
    }

    @Override
    public void saveConfig(UUID eventId, QueueConfig config) {
        hashOps.putAll(QueueRedisKeys.config(eventId), Map.of(
                "enabled", String.valueOf(config.isEnabled()),
                "maxConcurrent", String.valueOf(config.getMaxConcurrent()),
                "autoEnableThreshold", String.valueOf(config.getAutoEnableThreshold()),
                "purchaseWindowSeconds", String.valueOf(config.getPurchaseWindowSeconds())));
        trackEvent(eventId);
    }

    @Override
    public Set<UUID> getTrackedEventIds() {
        Set<String> raw = redisTemplate.opsForSet().members(QueueRedisKeys.TRACKED_EVENTS);
        if (raw == null) {
            return Set.of();
        }
        return raw.stream().map(UUID::fromString).collect(Collectors.toSet());
    }

    @Override
    public void trackEvent(UUID eventId) {
        redisTemplate.opsForSet().add(QueueRedisKeys.TRACKED_EVENTS, eventId.toString());
    }
}
