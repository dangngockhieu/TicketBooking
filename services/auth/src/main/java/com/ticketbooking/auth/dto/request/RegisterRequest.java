package com.ticketbooking.auth.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/*
 * Đăng ký tài khoản công khai — CHỈ tạo tài khoản CUSTOMER.
 */
public record RegisterRequest(
        @NotBlank(message = "Email không được để trống") @Email(message = "Email không đúng định dạng") String email,

        @NotBlank(message = "Mật khẩu không được để trống") @Size(min = 6, message = "Mật khẩu phải có ít nhất 6 ký tự") String password) {
}
