package com.ticketbooking.user.util;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Optional;
import java.util.UUID;

public final class SecurityUtil {

    private SecurityUtil() {
    }

    public static Optional<String> getCurrentUserLogin() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof Jwt jwt) {
            return Optional.ofNullable(jwt.getSubject());
        }
        return Optional.empty();
    }

    public static Optional<UUID> getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return Optional.empty();
        }
        if (authentication.getPrincipal() instanceof Jwt jwt) {
            String userIdStr = jwt.getClaimAsString("userId");
            if (userIdStr != null) {
                try {
                    return Optional.of(UUID.fromString(userIdStr));
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
        return Optional.empty();
    }
}
