package com.ticketbooking.auth.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record AuthResponse(
        String accessToken,
        String tokenType,
        Long expiresIn,
        UserInfo user,
        String refreshToken,
        // true khi tài khoản (Organizer) đăng nhập bằng mật khẩu tạm do Admin cấp
        // và chưa từng đổi mật khẩu — FE phải chặn điều hướng, bắt đổi mật khẩu trước.
        Boolean requirePasswordChange
) {
    public static AuthResponse of(String accessToken, Long expiresIn, UserInfo user, String refreshToken) {
        return new AuthResponse(accessToken, "Bearer", expiresIn, user, refreshToken, null);
    }

    public static AuthResponse of(
            String accessToken,
            Long expiresIn,
            UserInfo user,
            String refreshToken,
            boolean requirePasswordChange) {
        return new AuthResponse(accessToken, "Bearer", expiresIn, user, refreshToken, requirePasswordChange);
    }

    public record UserInfo(
            UUID id,
            String email,
            String role,
            String status
    ) {
    }
}
