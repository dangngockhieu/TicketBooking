package com.ticketbooking.auth.dto.response;

import com.ticketbooking.auth.enums.ClientType;

public record LoginResult(
        AuthResponse authResponse,
        String rawRefreshToken,
        ClientType clientType
) {
}
