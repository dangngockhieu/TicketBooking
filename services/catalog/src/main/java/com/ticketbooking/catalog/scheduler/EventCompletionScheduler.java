package com.ticketbooking.catalog.scheduler;

import com.ticketbooking.catalog.service.EventService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Chuyển PUBLISHED -> COMPLETED khi sự kiện đã qua endTime — điều kiện để
 * Payment Service tạo payout tự động 7 ngày sau (xem
 * docs/development-plan.md GĐ4 mục 4). Chạy mỗi giờ.
 */
@Component
public class EventCompletionScheduler {

    private final EventService eventService;

    public EventCompletionScheduler(EventService eventService) {
        this.eventService = eventService;
    }

    @Scheduled(fixedRate = 3_600_000)
    public void completeEndedEvents() {
        eventService.completeEndedEvents();
    }
}
