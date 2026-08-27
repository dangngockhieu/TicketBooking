package com.ticketbooking.user.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;

/**
 * user-service không tự ký/verify token bằng key thủ công (khác auth-service)
 * — Spring Boot tự autoconfigure {@code JwtDecoder} từ
 * {@code spring.security.oauth2.resourceserver.jwt.jwk-set-uri}. Ở đây chỉ
 * cần dạy Spring Security cách đọc claim tùy biến "permissions" (thay vì
 * claim mặc định "scope") — phải khớp với cách auth-service phát hành token
 * (xem auth-service JwtTokenProvider#createAccessToken).
 */
@Configuration
public class JwtConfig {

    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter converter = new JwtGrantedAuthoritiesConverter();
        converter.setAuthorityPrefix("");
        converter.setAuthoritiesClaimName("permissions");

        JwtAuthenticationConverter jwtAuthenticationConverter = new JwtAuthenticationConverter();
        jwtAuthenticationConverter.setJwtGrantedAuthoritiesConverter(converter);
        return jwtAuthenticationConverter;
    }
}
