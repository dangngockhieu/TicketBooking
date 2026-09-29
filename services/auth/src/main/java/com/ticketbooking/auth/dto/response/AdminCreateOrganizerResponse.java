package com.ticketbooking.auth.dto.response;

/**
 * Kết quả tạo tài khoản Organizer bởi Admin. Không chứa mật khẩu tạm — mật khẩu chỉ
 * được gửi qua email cho chính Organizer (Kafka auth.organizer-created → notification-service).
 */
public record AdminCreateOrganizerResponse(
                AuthResponse.UserInfo account) {
}
