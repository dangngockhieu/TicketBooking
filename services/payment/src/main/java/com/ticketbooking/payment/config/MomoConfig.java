package com.ticketbooking.payment.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(MomoProperties.class)
public class MomoConfig {

    /**
     * RestClient.Builder riêng cho MomoClient (KHÔNG {@code @LoadBalanced} — MoMo
     * là dịch vụ ngoài, gọi bằng URL tuyệt đối) — đặt tên/bean riêng để tránh
     * xung đột với RestClient.Builder có thể được khai báo cho các client nội bộ
     * khác (vd: gọi User Service) sau này.
     */
    @Bean
    public RestClient.Builder momoRestClientBuilder() {
        return RestClient.builder();
    }
}
