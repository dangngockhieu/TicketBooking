package com.ticketbooking.payment.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.HttpClientSettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Configuration
@EnableConfigurationProperties(MomoProperties.class)
public class MomoConfig {

    /**
     * RestClient.Builder riêng cho MomoClient (KHÔNG {@code @LoadBalanced} — MoMo
     * là dịch vụ ngoài, gọi bằng URL tuyệt đối) — đặt tên/bean riêng để tránh
     * xung đột với RestClient.Builder có thể được khai báo cho các client nội bộ
     * khác (vd: gọi User Service) sau này.
     * <p>
     * Connect 5s / read 15s — dài hơn client nội bộ vì đây là gọi qua Internet
     * thật tới MoMo, cần chịu được độ trễ/jitter mạng bình thường thay
     * vì fail-fast như các lời gọi cùng mạng Docker.
     */
    @Bean
    public RestClient.Builder momoRestClientBuilder() {
        HttpClientSettings settings = HttpClientSettings.defaults()
                .withConnectTimeout(Duration.ofSeconds(5))
                .withReadTimeout(Duration.ofSeconds(15));
        ClientHttpRequestFactory requestFactory = ClientHttpRequestFactoryBuilder.detect().build(settings);
        return RestClient.builder().requestFactory(requestFactory);
    }
}
