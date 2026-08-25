package com.ticketbooking.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Đổi mật khẩu khi đã đăng nhập. Dùng cho:
 * - Customer/Organizer tự đổi mật khẩu ở trang hồ sơ.
 * - Organizer bắt buộc đổi mật khẩu tạm ở lần đăng nhập đầu tiên
 *   (xem AuthServiceImpl#createOrganizer, Account#requirePasswordChange).
 */
public record ChangePasswordRequest(
        @NotBlank(message = "Vui lòng nhập mật khẩu hiện tại")
        String currentPassword,

        @NotBlank(message = "Mật khẩu mới không được để trống")
        @Size(min = 6, message = "Mật khẩu mới phải có ít nhất 6 ký tự")
        String newPassword,

        @NotBlank(message = "Vui lòng xác nhận mật khẩu mới")
        String confirmPassword
) {
    public boolean isConfirmMatched() {
        return newPassword != null && newPassword.equals(confirmPassword);
    }
}
