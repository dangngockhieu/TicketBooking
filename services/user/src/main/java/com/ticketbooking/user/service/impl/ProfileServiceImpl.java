package com.ticketbooking.user.service.impl;

import com.ticketbooking.common.exception.ConflictException;
import com.ticketbooking.user.dto.request.UpdateProfileRequest;
import com.ticketbooking.user.dto.response.ProfileResponse;
import com.ticketbooking.user.entity.Profile;
import com.ticketbooking.user.enums.UserType;
import com.ticketbooking.user.repository.ProfileRepository;
import com.ticketbooking.user.service.ProfileService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
@Transactional
public class ProfileServiceImpl implements ProfileService {

    private final ProfileRepository profileRepository;

    public ProfileServiceImpl(ProfileRepository profileRepository) {
        this.profileRepository = profileRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public ProfileResponse getMyProfile(UUID accountId, String email, String role) {
        Profile profile = profileRepository.findByAccountId(accountId)
                .orElseGet(() -> createDefaultProfile(accountId, role));
        return toResponse(profile, email, role);
    }

    @Override
    public ProfileResponse updateMyProfile(UUID accountId, String email, String role, UpdateProfileRequest request) {
        Profile profile = profileRepository.findByAccountId(accountId)
                .orElseGet(() -> createDefaultProfile(accountId, role));

        if (request.phoneNumber() != null && !request.phoneNumber().isBlank()) {
            profileRepository.findByPhoneNumber(request.phoneNumber())
                    .filter(existing -> !existing.getId().equals(profile.getId()))
                    .ifPresent(existing -> {
                        throw new ConflictException("Số điện thoại đã được sử dụng bởi tài khoản khác.");
                    });
        }

        profile.setFullName(request.fullName());
        profile.setPhoneNumber(request.phoneNumber());
        Profile saved = profileRepository.save(profile);

        return toResponse(saved, email, role);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<String> findFullName(UUID accountId) {
        return profileRepository.findByAccountId(accountId).map(Profile::getFullName);
    }

    /**
     * auth-service chỉ tạo bản ghi `accounts`, không tạo `profiles` (2 service,
     * 2 database riêng — không có transaction chung). Hồ sơ được khởi tạo mặc
     * định (rỗng) ngay lần đầu user gọi API user-service, thay vì chờ Kafka
     * event (chưa triển khai — xem docs/technical-flows.md §0.4).
     */
    private Profile createDefaultProfile(UUID accountId, String role) {
        Profile profile = Profile.builder()
                .accountId(accountId)
                .fullName("")
                .userType(UserType.valueOf(role))
                .build();
        return profileRepository.save(profile);
    }

    private ProfileResponse toResponse(Profile profile, String email, String role) {
        return new ProfileResponse(
                profile.getId(),
                profile.getAccountId(),
                profile.getFullName(),
                profile.getPhoneNumber(),
                profile.getAvatarUrl(),
                email,
                role);
    }
}
