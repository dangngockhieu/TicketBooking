package com.ticketbooking.auth.dto.response;

/** {@code email} là {@code null} nếu không tìm thấy account (không throw 404 — caller tự quyết định fallback). */
public record InternalAccountResponse(String email) {
}
