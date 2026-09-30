package com.ticketbooking.payment.config;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.HttpClientSettings;
import org.springframework.boot.restclient.RestClientCustomizer;
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
     * Connect 2s / read 5s — gọi nội bộ cùng mạng Docker. Treo lâu hơn gần như
     * chắc chắn là lỗi (deadlock, query kẹt) nên fail-fast thay vì chờ vô hạn
     * (mặc định của RestClient).
     * <p>
     * Tự định nghĩa RestClient.Builder (thay vì để Boot auto-config) nên phải
     * tự áp các RestClientCustomizer (bao gồm ObservationRestClientCustomizer
     * khi có tracing trên classpath) — auto-config của Boot chỉ chạy khi chưa
     * có bean RestClient.Builder nào khác (@ConditionalOnMissingBean), bean này
     * đã "che" mất nó nên tracing sẽ không tự động hoạt động nếu bỏ qua bước
     * customize() này. Dùng ObjectProvider (lazy) thay vì inject trực tiếp vì
     * @Configuration của ứng dụng được xử lý trước auto-configuration — inject
     * thẳng ObservationRestClientCustomizer sẽ fail lúc khởi động do bean đó
     * chưa tồn tại tại thời điểm này.
     */
    @Bean
    @LoadBalanced
    public RestClient.Builder loadBalancedRestClientBuilder(ObjectProvider<RestClientCustomizer> customizers) {
        HttpClientSettings settings = HttpClientSettings.defaults()
                .withConnectTimeout(Duration.ofSeconds(2))
                .withReadTimeout(Duration.ofSeconds(5));
        ClientHttpRequestFactory requestFactory = ClientHttpRequestFactoryBuilder.detect().build(settings);
        RestClient.Builder builder = RestClient.builder().requestFactory(requestFactory);
        customizers.orderedStream().forEach(customizer -> customizer.customize(builder));
        return builder;
    }
}
