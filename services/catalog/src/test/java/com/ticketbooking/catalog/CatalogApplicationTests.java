package com.ticketbooking.catalog;

import com.ticketbooking.catalog.repository.CategoryRepository;
import com.ticketbooking.catalog.repository.EventRepository;
import com.ticketbooking.catalog.repository.ProcessedTicketEventRepository;
import com.ticketbooking.catalog.repository.TicketClassRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(properties = {
        "spring.cloud.config.enabled=false",
        "eureka.client.enabled=false",
        "spring.autoconfigure.exclude=org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,org.springframework.boot.orm.jpa.autoconfigure.HibernateJpaAutoConfiguration,org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration,org.springframework.boot.data.redis.autoconfigure.RedisAutoConfiguration",
        "spring.kafka.consumer.group-id=catalog-service",
        "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=http://localhost:8081/.well-known/jwks.json"
})
class CatalogApplicationTests {

    @MockitoBean
    private CategoryRepository categoryRepository;

    @MockitoBean
    private EventRepository eventRepository;

    @MockitoBean
    private TicketClassRepository ticketClassRepository;

    @MockitoBean
    private ProcessedTicketEventRepository processedTicketEventRepository;

    @MockitoBean
    private StringRedisTemplate stringRedisTemplate;

    @Test
    void contextLoads() {
    }

}
