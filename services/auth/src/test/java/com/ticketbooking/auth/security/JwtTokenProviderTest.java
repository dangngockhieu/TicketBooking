package com.ticketbooking.auth.security;

import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
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

    // Keypair riêng cho test (không dùng chung với keypair dev thật ở secrets/jwt/),
    // lưu ở src/test/resources/keys/ — Maven Surefire chạy test với working dir = module basedir.
    private static final String TEST_PRIVATE_KEY_PATH = "src/test/resources/keys/private-test.key";
    private static final String TEST_PUBLIC_KEY_PATH = "src/test/resources/keys/public-test.key";

    private JwtTokenProvider jwtTokenProvider;

    @BeforeEach
    void setUp() throws Exception {
        JwtProperties properties = new JwtProperties(
                TEST_PRIVATE_KEY_PATH,
                TEST_PUBLIC_KEY_PATH,
                "test-key-1",
                3600,
                86400
        );
        JwtConfig jwtConfig = new JwtConfig(properties);
        RSAKey rsaKey = jwtConfig.rsaKey();
        JWKSource<SecurityContext> jwkSource = jwtConfig.jwkSource(rsaKey);
        JwtEncoder encoder = jwtConfig.jwtEncoder(jwkSource);
        JwtDecoder decoder = jwtConfig.jwtDecoder(rsaKey);

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
