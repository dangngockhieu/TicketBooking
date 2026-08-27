-- V1__init_profiles_table.sql
-- Khởi tạo schema cơ sở dữ liệu cho User Service (user_db trên port 5434)

CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- Bảng profiles (Hồ sơ cá nhân — liên kết accounts của Auth Service bằng Soft Key)
CREATE TABLE IF NOT EXISTS profiles (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    account_id    UUID         NOT NULL UNIQUE,  -- Soft Key → Auth Service accounts.id
    full_name     VARCHAR(255) NOT NULL,
    phone_number  VARCHAR(20)  UNIQUE,
    avatar_url    VARCHAR(500),
    user_type     VARCHAR(20)  NOT NULL CHECK (user_type IN ('CUSTOMER', 'ORGANIZER')),
    metadata      JSONB        NOT NULL DEFAULT '{}'::jsonb,
    created_at    TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_profiles_account_id ON profiles(account_id);
CREATE INDEX IF NOT EXISTS idx_profiles_phone ON profiles(phone_number);
CREATE INDEX IF NOT EXISTS idx_profiles_metadata ON profiles USING GIN (metadata);
