package com.ticketbooking.user.service;

import com.ticketbooking.common.exception.ConflictException;
import com.ticketbooking.user.dto.request.UpdateProfileRequest;
import com.ticketbooking.user.dto.response.ProfileResponse;
import com.ticketbooking.user.entity.Profile;
import com.ticketbooking.user.enums.UserType;
import com.ticketbooking.user.repository.ProfileRepository;
import com.ticketbooking.user.service.impl.ProfileServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProfileServiceImplTest {

    @Mock
    private ProfileRepository profileRepository;

    @InjectMocks
    private ProfileServiceImpl profileService;

    private UUID accountId;
    private Profile existingProfile;

    @BeforeEach
    void setUp() {
        accountId = UUID.randomUUID();
        existingProfile = Profile.builder()
                .id(UUID.randomUUID())
                .accountId(accountId)
                .fullName("Nguyễn Văn A")
                .phoneNumber("0901234567")
                .userType(UserType.CUSTOMER)
                .build();
    }

    @Test
    void testGetMyProfile_ExistingProfile_ReturnsIt() {
        when(profileRepository.findByAccountId(accountId)).thenReturn(Optional.of(existingProfile));

        ProfileResponse result = profileService.getMyProfile(accountId, "user@example.com", "CUSTOMER");

        assertEquals(existingProfile.getId(), result.id());
        assertEquals("Nguyễn Văn A", result.fullName());
        assertEquals("user@example.com", result.email());
        assertEquals("CUSTOMER", result.role());
        verify(profileRepository, never()).save(any(Profile.class));
    }

    @Test
    void testGetMyProfile_NoProfile_CreatesDefaultAndReturnsIt() {
        when(profileRepository.findByAccountId(accountId)).thenReturn(Optional.empty());
        when(profileRepository.save(any(Profile.class))).thenAnswer(invocation -> {
            Profile profile = invocation.getArgument(0);
            profile.setId(UUID.randomUUID());
            return profile;
        });

        ProfileResponse result = profileService.getMyProfile(accountId, "organizer@example.com", "ORGANIZER");

        assertNotNull(result.id());
        assertEquals("", result.fullName());
        assertEquals(accountId, result.accountId());

        ArgumentCaptor<Profile> captor = ArgumentCaptor.forClass(Profile.class);
        verify(profileRepository).save(captor.capture());
        assertEquals(UserType.ORGANIZER, captor.getValue().getUserType());
    }

    @Test
    void testUpdateMyProfile_Success_UpdatesFields() {
        when(profileRepository.findByAccountId(accountId)).thenReturn(Optional.of(existingProfile));
        when(profileRepository.findByPhoneNumber("0909999999")).thenReturn(Optional.empty());
        when(profileRepository.save(any(Profile.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UpdateProfileRequest request = new UpdateProfileRequest("Nguyễn Văn B", "0909999999");
        ProfileResponse result = profileService.updateMyProfile(accountId, "user@example.com", "CUSTOMER", request);

        assertEquals("Nguyễn Văn B", result.fullName());
        assertEquals("0909999999", result.phoneNumber());
    }

    @Test
    void testUpdateMyProfile_PhoneNumberTakenByAnotherAccount_ThrowsConflict() {
        Profile otherProfile = Profile.builder()
                .id(UUID.randomUUID())
                .accountId(UUID.randomUUID())
                .fullName("Người khác")
                .phoneNumber("0909999999")
                .userType(UserType.CUSTOMER)
                .build();

        when(profileRepository.findByAccountId(accountId)).thenReturn(Optional.of(existingProfile));
        when(profileRepository.findByPhoneNumber("0909999999")).thenReturn(Optional.of(otherProfile));

        UpdateProfileRequest request = new UpdateProfileRequest("Nguyễn Văn B", "0909999999");

        assertThrows(ConflictException.class,
                () -> profileService.updateMyProfile(accountId, "user@example.com", "CUSTOMER", request));
        verify(profileRepository, never()).save(any(Profile.class));
    }

    @Test
    void testUpdateMyProfile_KeepingOwnPhoneNumber_DoesNotThrowConflict() {
        when(profileRepository.findByAccountId(accountId)).thenReturn(Optional.of(existingProfile));
        when(profileRepository.findByPhoneNumber("0901234567")).thenReturn(Optional.of(existingProfile));
        when(profileRepository.save(any(Profile.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UpdateProfileRequest request = new UpdateProfileRequest("Nguyễn Văn A", "0901234567");

        assertDoesNotThrow(() -> profileService.updateMyProfile(accountId, "user@example.com", "CUSTOMER", request));
    }
}
