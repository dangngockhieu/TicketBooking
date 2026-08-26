package com.ticketbooking.auth.config;

import com.ticketbooking.auth.security.CustomAccessDeniedHandler;
import com.ticketbooking.auth.security.CustomAuthenticationEntryPoint;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(securedEnabled = true)
public class SecurityConfig {

        @Bean
        public PasswordEncoder passwordEncoder() {
                return Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();
        }

        @Bean
        public AuthenticationManager authenticationManager(AuthenticationConfiguration authenticationConfiguration)
                        throws Exception {
                return authenticationConfiguration.getAuthenticationManager();
        }

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
                                                .requestMatchers(
                                                                "/api/auth/login",
                                                                "/api/auth/register",
                                                                "/api/auth/refresh",
                                                                "/api/auth/verify-email",
                                                                "/api/auth/resend-verification",
                                                                "/api/auth/forgot-password",
                                                                "/api/auth/reset-password",
                                                                "/.well-known/jwks.json",
                                                                "/actuator/**")
                                                .permitAll()
                                                .anyRequest().authenticated())
                                .oauth2ResourceServer(oauth2 -> oauth2
                                                .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter))
                                                .authenticationEntryPoint(customAuthenticationEntryPoint)
                                                .accessDeniedHandler(customAccessDeniedHandler));

                return http.build();
        }
}
