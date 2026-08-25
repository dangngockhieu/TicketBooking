package com.ticketbooking.auth.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * Admin tạo trực tiếp tài khoản Organizer sau khi đã thẩm định giấy phép tổ
 * chức sự kiện ngoài hệ thống. Không nhận mật khẩu từ Admin — hệ thống tự
 * sinh mật khẩu tạm ngẫu nhiên (xem {@link com.ticketbooking.auth.dto.response.AdminCreateOrganizerResponse}).
 */
public record AdminCreateOrganizerRequest(
        @NotBlank(message = "Email không được để trống")
        @Email(message = "Email không đúng định dạng")
        String email,

        @NotBlank(message = "Tên đơn vị/họ tên không được để trống")
        String fullName
) {
}
