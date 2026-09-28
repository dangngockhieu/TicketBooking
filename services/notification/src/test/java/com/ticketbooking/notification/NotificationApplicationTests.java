package com.ticketbooking.notification;

import com.ticketbooking.notification.repository.EmailLogRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(properties = {
        "spring.cloud.config.enabled=false",
        "eureka.client.enabled=false",
        "spring.autoconfigure.exclude=org.springframework.boot.data.mongodb.autoconfigure.MongoAutoConfiguration,org.springframework.boot.data.mongodb.autoconfigure.MongoDataAutoConfiguration",
        "spring.kafka.consumer.group-id=notification-service",
        "spring.mail.host=localhost",
        "notification.mail.from=no-reply@ticketbooking.local",
        "notification.mail.support-email=support@ticketbooking.local"
})
class NotificationApplicationTests {

    @MockitoBean
    private EmailLogRepository emailLogRepository;

    @Test
    void contextLoads() {
    }

}
