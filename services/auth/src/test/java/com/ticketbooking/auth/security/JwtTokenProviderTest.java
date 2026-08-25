package com.ticketbooking.auth.security;

import com.ticketbooking.auth.config.JwtConfig;
import com.ticketbooking.auth.dto.response.AuthResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class JwtTokenProviderTest {

    private JwtTokenProvider jwtTokenProvider;

    @BeforeEach
    void setUp() {
        JwtProperties properties = new JwtProperties(
                "this_is_a_very_secure_secret_key_ticket_booking_system_32_bytes_min",
                3600,
                86400
        );
        JwtConfig jwtConfig = new JwtConfig(properties);
        JwtEncoder encoder = jwtConfig.jwtEncoder();
        JwtDecoder decoder = jwtConfig.jwtDecoder();

        jwtTokenProvider = new JwtTokenProvider(encoder, decoder, properties);
    }

    @Test
    void testCreateAndValidateAccessToken() {
        UUID userId = UUID.randomUUID();
        AuthResponse.UserInfo user = new AuthResponse.UserInfo(userId, "test@example.com", "CUSTOMER", "ACTIVE");

        String token = jwtTokenProvider.createAccessToken(user);
        assertNotNull(token);
        assertFalse(token.isBlank());

        Jwt jwt = jwtTokenProvider.checkValidToken(token);
        assertEquals("test@example.com", jwt.getSubject());
        assertEquals(userId.toString(), jwt.getClaimAsString("userId"));
        assertEquals("CUSTOMER", jwt.getClaimAsString("role"));
    }

    @Test
    void testCreateAndValidateRefreshToken() {
        String token = jwtTokenProvider.createRefreshToken("test@example.com");
        assertNotNull(token);
        assertFalse(token.isBlank());

        Jwt jwt = jwtTokenProvider.checkValidToken(token);
        assertEquals("test@example.com", jwt.getSubject());
    }
}
