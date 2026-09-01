package com.ticketbooking.catalog.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CategoryRequest(
        @NotBlank(message = "Tên danh mục không được để trống")
        @Size(max = 100, message = "Tên danh mục tối đa 100 ký tự")
        String name,

        @NotBlank(message = "Slug không được để trống")
        @Size(max = 100, message = "Slug tối đa 100 ký tự")
        @Pattern(regexp = "^[a-z0-9]+(?:-[a-z0-9]+)*$", message = "Slug chỉ gồm chữ thường, số và dấu gạch ngang")
        String slug,

        String description
) {
}
