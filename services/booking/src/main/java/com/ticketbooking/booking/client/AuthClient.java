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
 * Gọi sang Auth Service qua Eureka (cùng bean {@code loadBalancedRestClientBuilder}
 * với {@link CatalogClient}) để lấy email khách hàng khi gửi E-Ticket qua
 * Notification Service — email chỉ auth-service giữ (định danh đăng nhập),
 * user-service không lưu.
 */
@Slf4j
@Component
public class AuthClient {

    private record InternalAccountDto(String email) {
    }

    private final RestClient restClient;

    public AuthClient(
            @Qualifier("loadBalancedRestClientBuilder") RestClient.Builder restClientBuilder,
            @Value("${auth-service.name:auth-service}") String authServiceName) {
        this.restClient = restClientBuilder.baseUrl("http://" + authServiceName).build();
    }

    /** Trả về {@code null} nếu không gọi được Auth Service hoặc không tìm thấy account — không chặn luồng xác nhận thanh toán. */
    public String findEmail(UUID accountId) {
        try {
            ApiResponse<InternalAccountDto> response = restClient.get()
                    .uri("/api/internal/accounts/{accountId}", accountId)
                    .retrieve()
                    .body(new ParameterizedTypeReference<ApiResponse<InternalAccountDto>>() {
                    });
            return response != null && response.getData() != null ? response.getData().email() : null;
        } catch (RestClientException ex) {
            log.warn("Không gọi được Auth Service để lấy email khách hàng (accountId={}): {}", accountId, ex.getMessage());
            return null;
        }
    }
}
