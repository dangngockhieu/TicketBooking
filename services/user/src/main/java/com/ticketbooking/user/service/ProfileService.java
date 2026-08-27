package com.ticketbooking.user.service;

import com.ticketbooking.user.dto.request.UpdateProfileRequest;
import com.ticketbooking.user.dto.response.ProfileResponse;

import java.util.UUID;

public interface ProfileService {

    ProfileResponse getMyProfile(UUID accountId, String email, String role);

    ProfileResponse updateMyProfile(UUID accountId, String email, String role, UpdateProfileRequest request);
}
