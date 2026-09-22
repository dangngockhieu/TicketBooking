package com.ticketbooking.queue.config;

import com.ticketbooking.queue.listener.QueueHeartbeatExpirationListener;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

/**
 * Đăng ký lắng nghe kênh keyspace notification {@code __keyevent@*__:expired}
 * — yêu cầu Redis bật {@code notify-keyspace-events Ex} (đã cấu hình trong
 * docker-compose.yml, dùng chung với Booking Service).
 */
@Configuration
public class RedisListenerConfig {

    @Bean
    public RedisMessageListenerContainer redisMessageListenerContainer(
            ObjectProvider<RedisConnectionFactory> connectionFactoryProvider,
            QueueHeartbeatExpirationListener heartbeatExpirationListener) {
        RedisConnectionFactory connectionFactory = connectionFactoryProvider.getIfAvailable();
        if (connectionFactory == null) {
            return null;
        }

        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(heartbeatExpirationListener, new PatternTopic("__keyevent@*__:expired"));
        return container;
    }
}
