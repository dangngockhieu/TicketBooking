package com.ticketbooking.auth.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record AuthResponse(
        String accessToken,
        String tokenType,
        Long expiresIn,
        UserInfo user,
        String refreshToken
) {
    public static AuthResponse of(String accessToken, Long expiresIn, UserInfo user, String refreshToken) {
        return new AuthResponse(accessToken, "Bearer", expiresIn, user, refreshToken);
    }

    public record UserInfo(
            UUID id,
            String email,
            String role
    ) {
    }
}
