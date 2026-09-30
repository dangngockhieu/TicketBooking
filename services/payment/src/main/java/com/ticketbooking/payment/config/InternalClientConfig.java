package com.ticketbooking.payment.config;

import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.HttpClientSettings;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * RestClient.Builder cho các lời gọi service nội bộ qua Eureka (vd: Booking,
 * User Service).
 */
@Configuration
public class InternalClientConfig {

    /**
     * Connect 2s / read 5s — gọi nội bộ cùng mạng Docker
     * Treo lâu hơn gần như chắc chắn là lỗi (deadlock, query kẹt) nên
     * fail-fast thay vì chờ vô hạn (mặc định của RestClient).
     */
    @Bean
    @LoadBalanced
    public RestClient.Builder loadBalancedRestClientBuilder() {
        HttpClientSettings settings = HttpClientSettings.defaults()
                .withConnectTimeout(Duration.ofSeconds(2))
                .withReadTimeout(Duration.ofSeconds(5));
        ClientHttpRequestFactory requestFactory = ClientHttpRequestFactoryBuilder.detect().build(settings);
        return RestClient.builder().requestFactory(requestFactory);
    }
}
