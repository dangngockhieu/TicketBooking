package com.ticketbooking.auth;

import com.ticketbooking.auth.repository.AccountRepository;
import com.ticketbooking.auth.repository.RefreshTokenRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(properties = {
        "spring.cloud.config.enabled=false",
        "eureka.client.enabled=false",
        "spring.autoconfigure.exclude=org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,org.springframework.boot.orm.jpa.autoconfigure.HibernateJpaAutoConfiguration,org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration",
        "jwt.secret=a_very_long_secret_key_ticket_booking_system_32_bytes_min",
        "jwt.access-token-expiration-seconds=3600",
        "jwt.refresh-token-expiration-seconds=86400"
})
class AuthApplicationTests {

    @MockitoBean
    private AccountRepository accountRepository;

    @MockitoBean
    private RefreshTokenRepository refreshTokenRepository;

    @Test
    void contextLoads() {
    }

}
