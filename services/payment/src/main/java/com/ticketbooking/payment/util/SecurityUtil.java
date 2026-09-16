package com.ticketbooking.payment.util;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Optional;
import java.util.UUID;

public final class SecurityUtil {

    private SecurityUtil() {
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

    /**
     * Bearer token gốc của request hiện tại — dùng để forward sang Booking
     * Service khi gọi nội bộ (xem BookingClient), để service đó tự verify lại
     * quyền sở hữu booking (Zero Trust — không tin payload phía Payment).
     */
    public static Optional<String> getCurrentBearerToken() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof Jwt jwt) {
            return Optional.of("Bearer " + jwt.getTokenValue());
        }
        return Optional.empty();
    }
}
