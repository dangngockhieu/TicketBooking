package com.ticketbooking.apigateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverterAdapter;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Whitelist khớp đúng danh sách public route của các service phía sau (auth, catalog,
 * queue, payment) — Gateway phải đồng bộ, nếu không request public sẽ bị Gateway chặn
 * 401 trước khi kịp tới service.
 */
@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    /**
     * Danh sách origin frontend được phép gọi qua Gateway (điểm vào duy nhất
     * — mỗi service phía sau KHÔNG tự cấu hình CORS riêng). Nhiều origin cách
     * nhau bởi dấu phẩy, xem CORS_ALLOWED_ORIGINS trong .env.example.
     */
    @Value("${cors.allowed-origins:http://localhost:3000}")
    private String allowedOrigins;

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of(allowedOrigins.split(",")));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        // Cho phép cookie refreshToken (HttpOnly, xem AuthController) đi kèm request cross-origin.
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(
            ServerHttpSecurity http,
            ReactiveJwtAuthenticationConverterAdapter jwtAuthenticationConverter,
            CorsConfigurationSource corsConfigurationSource) {
        http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .authorizeExchange(exchanges -> exchanges
                        .pathMatchers(
                                "/api/auth/login",
                                "/api/auth/register",
                                "/api/auth/refresh",
                                "/api/auth/verify-email",
                                "/api/auth/resend-verification",
                                "/api/auth/forgot-password",
                                "/api/auth/reset-password",
                                "/.well-known/jwks.json",
                                "/actuator/**",
                                "/v3/api-docs/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/docs/**")
                        .permitAll()
                        // Dữ liệu public của catalog/queue — khớp permitAll ở từng service. Frontend
                        // render trang chủ/sự kiện bằng Server Component không có token người dùng.
                        .pathMatchers(HttpMethod.GET,
                                "/api/events",
                                "/api/events/*",
                                "/api/categories",
                                "/api/queue/*/status")
                        .permitAll()
                        // STOMP phòng chờ tự xác thực ở queue-service; IPN do MoMo gọi server-to-server
                        // (xác thực bằng chữ ký trong body).
                        .pathMatchers("/ws/queue/**", "/api/payments/momo/ipn")
                        .permitAll()
                        .anyExchange().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter)));

        return http.build();
    }
}
