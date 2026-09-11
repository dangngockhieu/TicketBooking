package com.ticketbooking.booking.config;

import com.ticketbooking.booking.listener.SeatHoldExpirationListener;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

/**
 * Đăng ký lắng nghe kênh keyspace notification {@code __keyevent@*__:expired}
 * — yêu cầu Redis bật {@code notify-keyspace-events Ex} (đã cấu hình trong
 * docker-compose.yml). Dùng pattern có wildcard db index để không phụ thuộc
 * database index cụ thể.
 * <p>
 * Lấy {@code RedisConnectionFactory} qua {@link ObjectProvider} và bỏ qua nếu
 * vắng mặt — cho phép smoke test loại trừ toàn bộ {@code RedisAutoConfiguration}
 * (không có datasource/Redis thật) mà không cần mock lại toàn bộ cây bean Redis
 * (health indicator, reactive template, ...) mà autoconfiguration đó kéo theo.
 */
@Configuration
public class RedisListenerConfig {

    @Bean
    public RedisMessageListenerContainer redisMessageListenerContainer(
            ObjectProvider<RedisConnectionFactory> connectionFactoryProvider,
            SeatHoldExpirationListener seatHoldExpirationListener) {
        RedisConnectionFactory connectionFactory = connectionFactoryProvider.getIfAvailable();
        if (connectionFactory == null) {
            return null;
        }

        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(seatHoldExpirationListener, new PatternTopic("__keyevent@*__:expired"));
        return container;
    }
}
