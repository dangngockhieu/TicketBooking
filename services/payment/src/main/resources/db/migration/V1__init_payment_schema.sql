-- V1__init_payment_schema.sql
-- Khởi tạo schema cơ sở dữ liệu cho Payment Service (payment_db trên port 5437)

CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- 1. Bảng transactions (Giao dịch thanh toán — Soft Key tới Booking Service)
CREATE TABLE IF NOT EXISTS transactions (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    booking_id        UUID           NOT NULL,  -- Soft Key → Booking Service
    amount            DECIMAL(15, 2) NOT NULL CHECK (amount > 0),
    payment_method    VARCHAR(50)    NOT NULL,
    gateway_trans_id  VARCHAR(255)   UNIQUE,
    status            VARCHAR(20)    NOT NULL DEFAULT 'PENDING'
                      CHECK (status IN ('PENDING', 'SUCCESS', 'FAILED', 'REFUNDED')),
    gateway_response  JSONB,
    paid_at           TIMESTAMP,
    created_at        TIMESTAMP      NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMP      NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_transactions_booking ON transactions(booking_id);
CREATE INDEX IF NOT EXISTS idx_transactions_status ON transactions(status);
CREATE INDEX IF NOT EXISTS idx_transactions_gateway ON transactions(gateway_trans_id);

-- 2. Bảng organizer_wallets (Ví Organizer — 1 Organizer = đúng 1 ví)
CREATE TABLE IF NOT EXISTS organizer_wallets (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organizer_id       UUID           NOT NULL UNIQUE,  -- Soft Key → Auth Service
    available_balance  DECIMAL(15, 2) NOT NULL DEFAULT 0,
    pending_payout     DECIMAL(15, 2) NOT NULL DEFAULT 0,
    total_withdrawn    DECIMAL(15, 2) NOT NULL DEFAULT 0,
    updated_at         TIMESTAMP      NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_organizer_wallets_organizer ON organizer_wallets(organizer_id);

-- 3. Bảng payout_requests (Yêu cầu rút tiền — AUTO sau 7 ngày event COMPLETED / MANUAL do Organizer xin)
CREATE TABLE IF NOT EXISTS payout_requests (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organizer_id          UUID           NOT NULL,  -- Soft Key → Auth Service
    amount                DECIMAL(15, 2) NOT NULL CHECK (amount > 0),
    -- 3 cột dưới là snapshot tự động lấy từ organizer_bank_accounts (User Service)
    -- tại thời điểm tạo request — KHÔNG phải field client tự điền
    bank_name             VARCHAR(100)   NOT NULL,
    bank_account_number   VARCHAR(50)    NOT NULL,
    bank_account_holder   VARCHAR(255)   NOT NULL,
    status                VARCHAR(20)    NOT NULL DEFAULT 'PENDING'
                          CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'PAID', 'HOLD')),
    source                VARCHAR(10)    NOT NULL CHECK (source IN ('AUTO', 'MANUAL')),
    event_id              UUID,          -- Soft Key → Catalog Service, chỉ khi source=AUTO
    reason                TEXT,
    momo_disbursement_id  VARCHAR(255)   UNIQUE,
    created_at            TIMESTAMP      NOT NULL DEFAULT NOW(),
    processed_at          TIMESTAMP,

    CONSTRAINT chk_reason_required CHECK (status NOT IN ('REJECTED', 'HOLD') OR reason IS NOT NULL)
);

CREATE INDEX IF NOT EXISTS idx_payout_requests_organizer ON payout_requests(organizer_id);
CREATE INDEX IF NOT EXISTS idx_payout_requests_status ON payout_requests(status);
CREATE UNIQUE INDEX IF NOT EXISTS idx_payout_requests_event_auto ON payout_requests(event_id)
    WHERE source = 'AUTO';  -- mỗi event chỉ có tối đa 1 payout AUTO
