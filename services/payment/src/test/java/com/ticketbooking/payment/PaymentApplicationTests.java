package com.ticketbooking.payment;

import com.ticketbooking.payment.repository.OrganizerWalletRepository;
import com.ticketbooking.payment.repository.PayoutRequestRepository;
import com.ticketbooking.payment.repository.TransactionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(properties = {
        "spring.cloud.config.enabled=false",
        "eureka.client.enabled=false",
        "spring.autoconfigure.exclude=org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,org.springframework.boot.orm.jpa.autoconfigure.HibernateJpaAutoConfiguration,org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration,org.springframework.boot.kafka.autoconfigure.KafkaAutoConfiguration",
        "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=http://localhost:8081/.well-known/jwks.json"
})
class PaymentApplicationTests {

    @MockitoBean
    private TransactionRepository transactionRepository;

    @MockitoBean
    private OrganizerWalletRepository organizerWalletRepository;

    @MockitoBean
    private PayoutRequestRepository payoutRequestRepository;

    @Test
    void contextLoads() {
    }

}
