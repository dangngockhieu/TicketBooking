package com.ticketbooking.auth.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

@ConfigurationProperties(prefix = "jwt")
public record JwtProperties(
        String secret,
        long accessTokenExpirationSeconds,
        long refreshTokenExpirationSeconds
) {
    public static final MacAlgorithm JWT_ALGORITHM = MacAlgorithm.HS256;

    public SecretKey getSecretKey() {
        byte[] keyBytes = (secret != null ? secret : "default_very_long_secret_key_ticket_booking_system_32_bytes").getBytes(StandardCharsets.UTF_8);
        return new SecretKeySpec(keyBytes, "HmacSHA256");
    }
}
