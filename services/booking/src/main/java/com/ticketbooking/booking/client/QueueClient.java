package com.ticketbooking.booking.client;

import com.ticketbooking.booking.client.dto.QueueAccessDto;
import com.ticketbooking.common.dto.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.UUID;

/**
 * Gọi sang Queue Service qua Eureka (load-balanced RestClient, cùng bean
 * {@code loadBalancedRestClientBuilder} với {@link CatalogClient}) để kiểm
 * tra {@code queueAccessToken} trước khi tạo đơn hàng khi phòng chờ ảo đang
 * bật cho sự kiện (xem docs/virtual-waiting-room.md §8).
 */
@Slf4j
@Component
public class QueueClient {

    private final RestClient restClient;

    public QueueClient(
            @Qualifier("loadBalancedRestClientBuilder") RestClient.Builder restClientBuilder,
            @Value("${queue-service.name:queue-service}") String queueServiceName) {
        this.restClient = restClientBuilder.baseUrl("http://" + queueServiceName).build();
    }

    /**
     * Trả về access mặc định "không cần token" ({@code enabled=false, valid=true}) nếu
     * Queue Service không phản hồi được — tránh chặn toàn bộ luồng đặt vé khi
     * phòng chờ ảo (tính năng phụ trợ) gặp sự cố tạm thời.
     */
    public QueueAccessDto checkAccess(UUID eventId, UUID userId, String accessToken) {
        try {
            ApiResponse<QueueAccessDto> response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/internal/queue/{eventId}/access")
                            .queryParam("userId", userId)
                            .queryParamIfPresent("accessToken", java.util.Optional.ofNullable(accessToken))
                            .build(eventId))
                    .retrieve()
                    .body(new ParameterizedTypeReference<ApiResponse<QueueAccessDto>>() {
                    });
            return response != null ? response.getData() : new QueueAccessDto(false, true);
        } catch (RestClientException ex) {
            log.warn("Không gọi được Queue Service để kiểm tra queueAccessToken (event={}): {}", eventId, ex.getMessage());
            return new QueueAccessDto(false, true);
        }
    }
}
