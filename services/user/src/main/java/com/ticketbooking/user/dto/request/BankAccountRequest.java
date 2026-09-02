package com.ticketbooking.user.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record BankAccountRequest(
        @NotBlank(message = "Tên ngân hàng không được để trống")
        @Size(max = 100, message = "Tên ngân hàng tối đa 100 ký tự")
        String bankName,

        @NotBlank(message = "Số tài khoản không được để trống")
        @Size(max = 50, message = "Số tài khoản tối đa 50 ký tự")
        String bankAccountNumber,

        @NotBlank(message = "Tên chủ tài khoản không được để trống")
        @Size(max = 255, message = "Tên chủ tài khoản tối đa 255 ký tự")
        String bankAccountHolder
) {
}
