package com.ticketbooking.payment.scheduler;

import com.ticketbooking.payment.service.PayoutService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Job nền hàng ngày tạo payout tự động cho các sự kiện đã COMPLETED đủ 7 ngày
 * (xem docs/development-plan.md GĐ4 mục 4, docs/api-design.md §7.5).
 */
@Component
public class PayoutAutoCreationScheduler {

    private final PayoutService payoutService;

    public PayoutAutoCreationScheduler(PayoutService payoutService) {
        this.payoutService = payoutService;
    }

    @Scheduled(cron = "0 0 2 * * *")
    public void createAutoPayouts() {
        payoutService.createAutoPayouts();
    }
}
