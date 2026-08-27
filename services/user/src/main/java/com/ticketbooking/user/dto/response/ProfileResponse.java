package com.ticketbooking.user.dto.response;

import java.util.UUID;

public record ProfileResponse(
        UUID id,
        UUID accountId,
        String fullName,
        String phoneNumber,
        String avatarUrl,
        String email,
        String role
) {
}
