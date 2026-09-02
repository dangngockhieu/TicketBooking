package com.ticketbooking.user.service.impl;

import com.ticketbooking.user.dto.request.BankAccountRequest;
import com.ticketbooking.user.dto.response.BankAccountResponse;
import com.ticketbooking.user.entity.OrganizerBankAccount;
import com.ticketbooking.user.entity.Profile;
import com.ticketbooking.user.enums.UserType;
import com.ticketbooking.user.repository.OrganizerBankAccountRepository;
import com.ticketbooking.user.repository.ProfileRepository;
import com.ticketbooking.user.service.OrganizerBankAccountService;
import com.ticketbooking.common.exception.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@Transactional
public class OrganizerBankAccountServiceImpl implements OrganizerBankAccountService {

    private final OrganizerBankAccountRepository bankAccountRepository;
    private final ProfileRepository profileRepository;

    public OrganizerBankAccountServiceImpl(
            OrganizerBankAccountRepository bankAccountRepository,
            ProfileRepository profileRepository) {
        this.bankAccountRepository = bankAccountRepository;
        this.profileRepository = profileRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public BankAccountResponse getMyBankAccount(UUID accountId) {
        return bankAccountRepository.findByProfile_AccountId(accountId)
                .map(BankAccountResponse::from)
                .orElse(null);
    }

    @Override
    public BankAccountResponse upsertMyBankAccount(UUID accountId, BankAccountRequest request) {
        Profile profile = resolveOrganizerProfile(accountId);

        OrganizerBankAccount bankAccount = bankAccountRepository.findByProfile(profile)
                .orElseGet(() -> OrganizerBankAccount.builder().profile(profile).build());

        bankAccount.setBankName(request.bankName());
        bankAccount.setBankAccountNumber(request.bankAccountNumber());
        bankAccount.setBankAccountHolder(request.bankAccountHolder());
        // Đổi tài khoản (kể cả lần thiết lập đầu) luôn reset xác minh — bắt buộc
        // Admin xác minh lại trước khi dùng được cho payout.
        bankAccount.setVerified(false);
        bankAccount.setVerifiedAt(null);

        return BankAccountResponse.from(bankAccountRepository.save(bankAccount));
    }

    @Override
    public BankAccountResponse verify(UUID organizerAccountId) {
        OrganizerBankAccount bankAccount = bankAccountRepository.findByProfile_AccountId(organizerAccountId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Organizer chưa thiết lập tài khoản ngân hàng."));

        bankAccount.setVerified(true);
        bankAccount.setVerifiedAt(Instant.now());

        return BankAccountResponse.from(bankAccountRepository.save(bankAccount));
    }

    /** Giống ProfileServiceImpl#createDefaultProfile nhưng cố định ORGANIZER — endpoint này chỉ dành cho Organizer. */
    private Profile resolveOrganizerProfile(UUID accountId) {
        return profileRepository.findByAccountId(accountId)
                .orElseGet(() -> profileRepository.save(Profile.builder()
                        .accountId(accountId)
                        .fullName("")
                        .userType(UserType.ORGANIZER)
                        .build()));
    }
}
