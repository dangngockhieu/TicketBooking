package com.ticketbooking.user.service;

import com.ticketbooking.user.dto.request.UpdateProfileRequest;
import com.ticketbooking.user.dto.response.ProfileResponse;

import java.util.Optional;
import java.util.UUID;

public interface ProfileService {

    ProfileResponse getMyProfile(UUID accountId, String email, String role);

    ProfileResponse updateMyProfile(UUID accountId, String email, String role, UpdateProfileRequest request);

    /**
     * Tra cứu tên hiển thị theo {@code accountId} — dùng bởi endpoint nội bộ
     * cho Booking Service (hiển thị tên khách khi check-in QR, xem
     * docs/api-design.md §4.5). Không tự tạo profile mặc định nếu chưa có
     * (khác {@link #getMyProfile}) vì đây là truy vấn của service khác, không
     * phải hành động của chính người dùng.
     */
    Optional<String> findFullName(UUID accountId);
}
