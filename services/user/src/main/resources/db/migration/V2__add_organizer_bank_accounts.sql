-- V2__add_organizer_bank_accounts.sql
-- 1 Organizer = đúng 1 tài khoản ngân hàng cố định (ép bằng UNIQUE trên profile_id).
-- Tách bảng riêng khỏi profiles.metadata vì đây là dữ liệu nhạy cảm dùng để chi tiền
-- thật (payout) — cần ràng buộc SQL chặt + cột xác minh riêng, không thể là JSONB tự do.
-- Xem docs/database-schema.md §2, docs/technical-flows.md §4b.2.

CREATE TABLE IF NOT EXISTS organizer_bank_accounts (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    profile_id           UUID         NOT NULL UNIQUE REFERENCES profiles(id),
    bank_name            VARCHAR(100) NOT NULL,
    bank_account_number  VARCHAR(50)  NOT NULL,
    bank_account_holder  VARCHAR(255) NOT NULL,
    verified             BOOLEAN      NOT NULL DEFAULT FALSE,
    verified_at          TIMESTAMP,
    created_at           TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_organizer_bank_accounts_profile ON organizer_bank_accounts(profile_id);
