package com.ticketbooking.payment.client;

import com.ticketbooking.common.dto.ApiResponse;
import com.ticketbooking.common.exception.ForbiddenException;
import com.ticketbooking.common.exception.ResourceNotFoundException;
import com.ticketbooking.common.exception.TimeoutException;
import com.ticketbooking.payment.client.dto.BookingDto;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.util.UUID;

/**
 * Gọi sang Booking Service qua Eureka (load-balanced RestClient) để xác thực
 * booking trước khi khởi tạo thanh toán (xem docs/api-design.md §5.1).
 * <p>
 * Forward nguyên vẹn Bearer token của Customer đang gọi ({@code Authorization}
 * header) — Booking Service tự verify lại quyền sở hữu (Zero Trust), Payment
 * Service KHÔNG tự quyết định thay.
 */
@Component
public class BookingClient {

    private final RestClient restClient;

    public BookingClient(
            @Qualifier("loadBalancedRestClientBuilder") RestClient.Builder restClientBuilder,
            @Value("${booking-service.name:booking-service}") String bookingServiceName) {
        this.restClient = restClientBuilder.baseUrl("http://" + bookingServiceName).build();
    }

    @Retryable(
            retryFor = {
                    ResourceAccessException.class,
                    HttpServerErrorException.ServiceUnavailable.class,
                    HttpServerErrorException.GatewayTimeout.class,
                    HttpClientErrorException.TooManyRequests.class
            },
            maxAttempts = 2,
            backoff = @Backoff(delay = 200))
    public BookingDto getBooking(UUID bookingId, String bearerToken) {
        try {
            ApiResponse<BookingDto> response = restClient.get()
                    .uri("/api/bookings/{id}", bookingId)
                    .header(HttpHeaders.AUTHORIZATION, bearerToken)
                    .retrieve()
                    .body(new ParameterizedTypeReference<ApiResponse<BookingDto>>() {
                    });
            return response != null ? response.getData() : null;
        } catch (HttpClientErrorException.NotFound ex) {
            throw new ResourceNotFoundException("Không tìm thấy đơn hàng.");
        } catch (HttpClientErrorException.Forbidden ex) {
            throw new ForbiddenException("Đơn hàng không thuộc về bạn.");
        }
    }

    @Recover
    public BookingDto recoverGetBooking(Exception ex, UUID bookingId, String bearerToken) {
        throw new TimeoutException("Booking Service không phản hồi kịp thời.");
    }
}
