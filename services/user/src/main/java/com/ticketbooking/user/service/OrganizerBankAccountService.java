package com.ticketbooking.user.service;

import com.ticketbooking.user.dto.request.BankAccountRequest;
import com.ticketbooking.user.dto.response.BankAccountResponse;

import java.util.UUID;

public interface OrganizerBankAccountService {

    /** @return {@code null} nếu Organizer chưa từng thiết lập tài khoản ngân hàng. */
    BankAccountResponse getMyBankAccount(UUID accountId);

    /**
     * Tạo mới hoặc thay thế tài khoản ngân hàng hiện có (upsert theo profile,
     * KHÔNG có khái niệm nhiều tài khoản). Luôn reset {@code verified=false} —
     * bắt buộc Admin xác minh lại, chống chiếm đoạt tài khoản đổi nơi nhận tiền.
     */
    BankAccountResponse upsertMyBankAccount(UUID accountId, BankAccountRequest request);

    /** ADMIN xác minh tài khoản ngân hàng của một Organizer cụ thể. */
    BankAccountResponse verify(UUID organizerAccountId);
}
