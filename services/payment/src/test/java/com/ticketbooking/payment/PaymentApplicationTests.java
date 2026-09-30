package com.ticketbooking.payment;

import com.ticketbooking.payment.repository.OrganizerWalletRepository;
import com.ticketbooking.payment.repository.PayoutRequestRepository;
import com.ticketbooking.payment.repository.TransactionRepository;
import com.ticketbooking.common.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

@SpringBootTest(properties = {
        "spring.cloud.config.enabled=false",
        "eureka.client.enabled=false",
        "spring.autoconfigure.exclude=org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,org.springframework.boot.orm.jpa.autoconfigure.HibernateJpaAutoConfiguration,org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration,org.springframework.boot.data.redis.autoconfigure.RedisAutoConfiguration",
        "spring.kafka.consumer.group-id=payment-service",
        "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=http://localhost:8081/.well-known/jwks.json"
})
class PaymentApplicationTests {

    @MockitoBean
    private TransactionRepository transactionRepository;

    @MockitoBean
    private OrganizerWalletRepository organizerWalletRepository;

    @MockitoBean
    private PayoutRequestRepository payoutRequestRepository;

    @MockitoBean
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void contextLoads() {
    }

    @Test
    void globalExceptionHandlerIsRegistered() {
        assertDoesNotThrow(() -> applicationContext.getBean(GlobalExceptionHandler.class));
    }

}
