package com.ticketbooking.queue.config;

import com.ticketbooking.queue.security.CustomAccessDeniedHandler;
import com.ticketbooking.queue.security.CustomAuthenticationEntryPoint;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Xác thực JWT cho các API REST như mọi resource server khác trong hệ thống
 * (xem CLAUDE.md "Zero-trust JWT verification"). Riêng kênh WebSocket/STOMP
 * (handshake HTTP tại {@code /ws/queue/**}) KHÔNG thể đính kèm header
 * Authorization từ trình duyệt — permitAll ở đây, xác thực JWT thực sự diễn
 * ra tại khung STOMP {@code CONNECT} qua {@link com.ticketbooking.queue.security.StompAuthChannelInterceptor}.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(securedEnabled = true)
public class SecurityConfig {

        @Bean
        public SecurityFilterChain filterChain(
                        HttpSecurity http,
                        CustomAuthenticationEntryPoint customAuthenticationEntryPoint,
                        CustomAccessDeniedHandler customAccessDeniedHandler,
                        JwtAuthenticationConverter jwtAuthenticationConverter) throws Exception {
                http
                                .cors(Customizer.withDefaults())
                                .csrf(AbstractHttpConfigurer::disable)
                                .formLogin(AbstractHttpConfigurer::disable)
                                .httpBasic(AbstractHttpConfigurer::disable)
                                .sessionManagement(session -> session
                                                .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                                .authorizeHttpRequests(authz -> authz
                                                .requestMatchers("/actuator/**")
                                                .permitAll()
                                                .requestMatchers("/api/internal/**")
                                                .permitAll()
                                                .requestMatchers("/ws/queue/**")
                                                .permitAll()
                                                .requestMatchers(HttpMethod.GET, "/api/queue/*/status")
                                                .permitAll()
                                                .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html")
                                                .permitAll()
                                                .anyRequest().authenticated())
                                .oauth2ResourceServer(oauth2 -> oauth2
                                                .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter))
                                                .authenticationEntryPoint(customAuthenticationEntryPoint)
                                                .accessDeniedHandler(customAccessDeniedHandler));

                return http.build();
        }
}
