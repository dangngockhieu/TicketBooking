package com.ticketbooking.auth.security;

import com.ticketbooking.auth.dto.response.AuthResponse;
import com.ticketbooking.common.exception.InvalidTokenException;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Service
public class JwtTokenProvider {

    private final JwtEncoder jwtEncoder;
    private final JwtDecoder jwtDecoder;
    private final JwtProperties jwtProperties;

    public JwtTokenProvider(JwtEncoder jwtEncoder, JwtDecoder jwtDecoder, JwtProperties jwtProperties) {
        this.jwtEncoder = jwtEncoder;
        this.jwtDecoder = jwtDecoder;
        this.jwtProperties = jwtProperties;
    }

    public String createAccessToken(AuthResponse.UserInfo user) {
        Instant now = Instant.now();
        Instant validity = now.plus(jwtProperties.accessTokenExpirationSeconds(), ChronoUnit.SECONDS);

        String roleWithPrefix = user.role().startsWith("ROLE_") ? user.role() : "ROLE_" + user.role();
        List<String> authorities = List.of(roleWithPrefix);

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuedAt(now)
                .expiresAt(validity)
                .subject(user.email())
                .claim("userId", user.id().toString())
                .claim("role", user.role())
                .claim("permissions", authorities)
                .build();

        JwsHeader jwsHeader = JwsHeader.with(JwtProperties.JWT_ALGORITHM).keyId(jwtProperties.keyId()).build();
        return this.jwtEncoder.encode(JwtEncoderParameters.from(jwsHeader, claims)).getTokenValue();
    }

    public String createRefreshToken(String email) {
        Instant now = Instant.now();
        Instant validity = now.plus(jwtProperties.refreshTokenExpirationSeconds(), ChronoUnit.SECONDS);

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .id(UUID.randomUUID().toString())
                .issuedAt(now)
                .expiresAt(validity)
                .subject(email)
                .build();

        JwsHeader jwsHeader = JwsHeader.with(JwtProperties.JWT_ALGORITHM).keyId(jwtProperties.keyId()).build();
        return this.jwtEncoder.encode(JwtEncoderParameters.from(jwsHeader, claims)).getTokenValue();
    }

    public Jwt checkValidToken(String token) {
        try {
            return this.jwtDecoder.decode(token);
        } catch (JwtValidationException e) {
            throw new InvalidTokenException("Phiên đăng nhập đã hết hạn, vui lòng đăng nhập lại.");
        } catch (BadJwtException e) {
            throw new InvalidTokenException("Token không hợp lệ hoặc đã bị thay đổi: " + e.getMessage());
        } catch (Exception e) {
            throw new InvalidTokenException("Không thể xác thực token: " + e.getMessage());
        }
    }

    public long getAccessTokenExpirationSeconds() {
        return jwtProperties.accessTokenExpirationSeconds();
    }

    public long getRefreshTokenExpirationSeconds() {
        return jwtProperties.refreshTokenExpirationSeconds();
    }
}
