package com.ticketbooking.user.service;

import com.ticketbooking.user.dto.request.BankAccountRequest;
import com.ticketbooking.user.dto.response.BankAccountResponse;
import com.ticketbooking.user.entity.OrganizerBankAccount;
import com.ticketbooking.user.entity.Profile;
import com.ticketbooking.user.enums.UserType;
import com.ticketbooking.user.repository.OrganizerBankAccountRepository;
import com.ticketbooking.user.repository.ProfileRepository;
import com.ticketbooking.user.service.impl.OrganizerBankAccountServiceImpl;
import com.ticketbooking.common.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrganizerBankAccountServiceImplTest {

    @Mock
    private OrganizerBankAccountRepository bankAccountRepository;

    @Mock
    private ProfileRepository profileRepository;

    @InjectMocks
    private OrganizerBankAccountServiceImpl bankAccountService;

    private UUID accountId;
    private Profile profile;

    @BeforeEach
    void setUp() {
        accountId = UUID.randomUUID();
        profile = Profile.builder()
                .id(UUID.randomUUID())
                .accountId(accountId)
                .fullName("Công ty ABC")
                .userType(UserType.ORGANIZER)
                .build();
    }

    @Test
    void testGetMyBankAccount_NotSet_ReturnsNull() {
        when(bankAccountRepository.findByProfile_AccountId(accountId)).thenReturn(Optional.empty());

        assertNull(bankAccountService.getMyBankAccount(accountId));
    }

    @Test
    void testUpsert_FirstTime_CreatesUnverified() {
        BankAccountRequest request = new BankAccountRequest("Vietcombank", "0071000123456", "CONG TY TNHH ABC");
        when(profileRepository.findByAccountId(accountId)).thenReturn(Optional.of(profile));
        when(bankAccountRepository.findByProfile(profile)).thenReturn(Optional.empty());
        when(bankAccountRepository.save(any(OrganizerBankAccount.class))).thenAnswer(inv -> inv.getArgument(0));

        BankAccountResponse result = bankAccountService.upsertMyBankAccount(accountId, request);

        assertEquals("Vietcombank", result.bankName());
        assertFalse(result.verified());
        assertNull(result.verifiedAt());
    }

    @Test
    void testUpsert_ReplaceExisting_ResetsVerified() {
        OrganizerBankAccount existing = OrganizerBankAccount.builder()
                .id(UUID.randomUUID())
                .profile(profile)
                .bankName("Techcombank")
                .bankAccountNumber("111")
                .bankAccountHolder("OLD HOLDER")
                .verified(true)
                .verifiedAt(java.time.Instant.now())
                .build();
        BankAccountRequest request = new BankAccountRequest("Vietcombank", "0071000123456", "CONG TY TNHH ABC");
        when(profileRepository.findByAccountId(accountId)).thenReturn(Optional.of(profile));
        when(bankAccountRepository.findByProfile(profile)).thenReturn(Optional.of(existing));
        when(bankAccountRepository.save(any(OrganizerBankAccount.class))).thenAnswer(inv -> inv.getArgument(0));

        BankAccountResponse result = bankAccountService.upsertMyBankAccount(accountId, request);

        assertEquals("Vietcombank", result.bankName());
        assertFalse(result.verified());
        assertNull(result.verifiedAt());
    }

    @Test
    void testVerify_NotSet_ThrowsResourceNotFound() {
        when(bankAccountRepository.findByProfile_AccountId(accountId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> bankAccountService.verify(accountId));
    }

    @Test
    void testVerify_Success() {
        OrganizerBankAccount existing = OrganizerBankAccount.builder()
                .id(UUID.randomUUID())
                .profile(profile)
                .bankName("Vietcombank")
                .bankAccountNumber("0071000123456")
                .bankAccountHolder("CONG TY TNHH ABC")
                .verified(false)
                .build();
        when(bankAccountRepository.findByProfile_AccountId(accountId)).thenReturn(Optional.of(existing));
        when(bankAccountRepository.save(any(OrganizerBankAccount.class))).thenAnswer(inv -> inv.getArgument(0));

        BankAccountResponse result = bankAccountService.verify(accountId);

        assertTrue(result.verified());
        assertNotNull(result.verifiedAt());
    }
}
