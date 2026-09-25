package com.ticketbooking.user.dto.response;

/** {@code fullName} là {@code null} nếu tài khoản chưa từng tạo profile (chưa gọi {@code GET /api/users/me}). */
public record InternalProfileResponse(String fullName) {
}
