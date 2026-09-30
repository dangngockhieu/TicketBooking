package com.ticketbooking.payment.client;

import com.ticketbooking.common.dto.ApiResponse;
import com.ticketbooking.common.exception.TimeoutException;
import com.ticketbooking.payment.client.dto.BankAccountDto;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.util.UUID;

/**
 * Gọi sang User Service qua Eureka (load-balanced RestClient) để lấy snapshot
 * tài khoản ngân hàng đã xác minh khi tạo PayoutRequest (xem
 */
@Component
public class UserClient {

    private final RestClient restClient;

    public UserClient(
            @Qualifier("loadBalancedRestClientBuilder") RestClient.Builder restClientBuilder,
            @Value("${user-service.name:user-service}") String userServiceName) {
        this.restClient = restClientBuilder.baseUrl("http://" + userServiceName).build();
    }

    /**
     * Yêu cầu rút tiền thủ công (§7.4) — có JWT của chính Organizer để forward,
     * service tự verify quyền.
     */
    public BankAccountDto getMyBankAccount(String bearerToken) {
        try {
            ApiResponse<BankAccountDto> response = restClient.get()
                    .uri("/api/organizer/bank-account")
                    .header(HttpHeaders.AUTHORIZATION, bearerToken)
                    .retrieve()
                    .body(new ParameterizedTypeReference<ApiResponse<BankAccountDto>>() {
                    });
            return response != null ? response.getData() : null;
        } catch (ResourceAccessException ex) {
            throw new TimeoutException("User Service không phản hồi kịp thời.");
        }
    }

    /**
     * Payout tự động (§7.5) — job nền không có JWT, gọi endpoint nội bộ (xem
     * InternalOrganizerBankAccountController).
     */
    public BankAccountDto getBankAccountByOrganizerId(UUID organizerId) {
        try {
            ApiResponse<BankAccountDto> response = restClient.get()
                    .uri("/api/internal/organizers/{id}/bank-account", organizerId)
                    .retrieve()
                    .body(new ParameterizedTypeReference<ApiResponse<BankAccountDto>>() {
                    });
            return response != null ? response.getData() : null;
        } catch (ResourceAccessException ex) {
            throw new TimeoutException("User Service không phản hồi kịp thời.");
        }
    }
}
