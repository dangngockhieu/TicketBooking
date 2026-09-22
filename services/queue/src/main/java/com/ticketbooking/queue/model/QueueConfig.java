package com.ticketbooking.queue.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Cấu hình phòng chờ ảo cho một sự kiện (xem docs/virtual-waiting-room.md §3
 * mục 1) — lưu ở Redis Hash {@code queue:config:{eventId}}, không cần
 * Postgres vì toàn bộ trạng thái của Queue Service là tạm thời/vận hành.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QueueConfig {

    public static final QueueConfig DEFAULT = QueueConfig.builder()
            .enabled(false)
            .maxConcurrent(500)
            .autoEnableThreshold(1000)
            .purchaseWindowSeconds(600)
            .build();

    private boolean enabled;
    private int maxConcurrent;
    private int autoEnableThreshold;
    private int purchaseWindowSeconds;
}
