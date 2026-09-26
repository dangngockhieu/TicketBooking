package com.ticketbooking.booking.client;

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
 * Gọi sang User Service qua Eureka (cùng bean {@code loadBalancedRestClientBuilder}
 * với {@link CatalogClient}/{@link QueueClient}) để lấy tên hiển thị của khách
 * hàng khi check-in vé bằng QR (xem docs/api-design.md §4.5).
 */
@Slf4j
@Component
public class UserClient {

    private record InternalProfileDto(String fullName) {
    }

    private final RestClient restClient;

    public UserClient(
            @Qualifier("loadBalancedRestClientBuilder") RestClient.Builder restClientBuilder,
            @Value("${user-service.name:user-service}") String userServiceName) {
        this.restClient = restClientBuilder.baseUrl("http://" + userServiceName).build();
    }

    /** Trả về {@code null} nếu không gọi được User Service hoặc khách chưa từng tạo hồ sơ — không chặn luồng check-in vì đây chỉ là thông tin hiển thị. */
    public String findFullName(UUID accountId) {
        try {
            ApiResponse<InternalProfileDto> response = restClient.get()
                    .uri("/api/internal/profiles/{accountId}", accountId)
                    .retrieve()
                    .body(new ParameterizedTypeReference<ApiResponse<InternalProfileDto>>() {
                    });
            return response != null && response.getData() != null ? response.getData().fullName() : null;
        } catch (RestClientException ex) {
            log.warn("Không gọi được User Service để lấy tên khách hàng (accountId={}): {}", accountId, ex.getMessage());
            return null;
        }
    }
}
