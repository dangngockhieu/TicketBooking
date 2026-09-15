package com.ticketbooking.payment.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Thông tin thương nhân MoMo (xem docs/api-design.md §5, KHÔNG hardcode secret — luôn qua .env). */
@ConfigurationProperties(prefix = "momo")
public record MomoProperties(
        String partnerCode,
        String accessKey,
        String secretKey,
        String endpoint,
        String redirectUrl,
        String ipnUrl
) {
}
