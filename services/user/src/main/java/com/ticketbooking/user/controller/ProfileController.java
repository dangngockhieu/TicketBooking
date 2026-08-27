package com.ticketbooking.user.controller;

import com.ticketbooking.common.dto.ApiResponse;
import com.ticketbooking.user.dto.request.UpdateProfileRequest;
import com.ticketbooking.user.dto.response.ProfileResponse;
import com.ticketbooking.user.service.ProfileService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/users")
public class ProfileController {

    private final ProfileService profileService;

    public ProfileController(ProfileService profileService) {
        this.profileService = profileService;
    }

    @GetMapping("/me")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'ORGANIZER')")
    public ResponseEntity<ApiResponse<ProfileResponse>> getMyProfile(JwtAuthenticationToken authentication) {
        ProfileResponse profile = profileService.getMyProfile(
                accountId(authentication.getToken()),
                authentication.getToken().getSubject(),
                role(authentication.getToken()));
        return ResponseEntity.ok(ApiResponse.success("Lấy hồ sơ thành công.", profile));
    }

    @PutMapping("/me")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'ORGANIZER')")
    public ResponseEntity<ApiResponse<ProfileResponse>> updateMyProfile(
            JwtAuthenticationToken authentication,
            @Valid @RequestBody UpdateProfileRequest request) {
        ProfileResponse profile = profileService.updateMyProfile(
                accountId(authentication.getToken()),
                authentication.getToken().getSubject(),
                role(authentication.getToken()),
                request);
        return ResponseEntity.ok(ApiResponse.success("Cập nhật hồ sơ thành công.", profile));
    }

    private static UUID accountId(Jwt jwt) {
        return UUID.fromString(jwt.getClaimAsString("userId"));
    }

    private static String role(Jwt jwt) {
        return jwt.getClaimAsString("role");
    }
}
