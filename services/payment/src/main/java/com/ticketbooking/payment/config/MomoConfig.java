package com.ticketbooking.payment.config;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.HttpClientSettings;
import org.springframework.boot.restclient.RestClientCustomizer;
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
    public RestClient.Builder momoRestClientBuilder(ObjectProvider<RestClientCustomizer> customizers) {
        HttpClientSettings settings = HttpClientSettings.defaults()
                .withConnectTimeout(Duration.ofSeconds(5))
                .withReadTimeout(Duration.ofSeconds(15));
        ClientHttpRequestFactory requestFactory = ClientHttpRequestFactoryBuilder.detect().build(settings);
        RestClient.Builder builder = RestClient.builder().requestFactory(requestFactory);
        customizers.orderedStream().forEach(customizer -> customizer.customize(builder));
        return builder;
    }
}
