package com.ticketbooking.auth.dto.response;

/**
 * Kết quả tạo tài khoản Organizer bởi Admin.
 */
public record AdminCreateOrganizerResponse(
                AuthResponse.UserInfo account,
                String tempPassword) {
}
