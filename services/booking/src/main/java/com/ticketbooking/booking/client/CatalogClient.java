package com.ticketbooking.booking.client;

import com.ticketbooking.booking.client.dto.CatalogEventDto;
import com.ticketbooking.common.dto.ApiResponse;
import com.ticketbooking.common.exception.ResourceNotFoundException;
import com.ticketbooking.common.exception.TimeoutException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.util.UUID;

/**
 * Gọi sang Catalog Service qua Eureka (load-balanced RestClient, KHÔNG
 * hard-code
 * host:port) để lấy thông tin sự kiện/hạng vé còn hiệu lực tại thời điểm đặt vé
 * (giá, số vé còn lại, cửa sổ mở bán).
 */
@Component
public class CatalogClient {

    private final RestClient restClient;

    public CatalogClient(
            @Qualifier("loadBalancedRestClientBuilder") RestClient.Builder restClientBuilder,
            @Value("${catalog-service.name:catalog-service}") String catalogServiceName) {
        this.restClient = restClientBuilder.baseUrl("http://" + catalogServiceName).build();
    }

    public CatalogEventDto getEvent(UUID eventId) {
        try {
            ApiResponse<CatalogEventDto> response = restClient.get()
                    .uri("/api/events/{id}", eventId)
                    .retrieve()
                    .body(new ParameterizedTypeReference<ApiResponse<CatalogEventDto>>() {
                    });
            return response != null ? response.getData() : null;
        } catch (HttpClientErrorException.NotFound ex) {
            throw new ResourceNotFoundException("Không tìm thấy sự kiện.");
        } catch (ResourceAccessException ex) {
            throw new TimeoutException("Catalog Service không phản hồi kịp thời.");
        }
    }
}
