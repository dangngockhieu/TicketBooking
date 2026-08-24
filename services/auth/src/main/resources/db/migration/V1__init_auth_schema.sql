-- V1__init_auth_schema.sql
-- Khởi tạo schema cơ sở dữ liệu cho Auth Service (auth_db trên port 5433)

-- Kích hoạt extension hỗ trợ sinh UUID v4
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- 1. Bảng accounts (Tài khoản người dùng, phân quyền và trạng thái)
CREATE TABLE IF NOT EXISTS accounts (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email         VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    role          VARCHAR(20)  NOT NULL DEFAULT 'CUSTOMER' CHECK (role IN ('ADMIN', 'ORGANIZER', 'CUSTOMER')),
    status        VARCHAR(20)  NOT NULL DEFAULT 'PENDING' CHECK (status IN ('ACTIVE', 'LOCKED', 'PENDING')),
    created_at    TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_accounts_email ON accounts(email);
CREATE INDEX IF NOT EXISTS idx_accounts_role_status ON accounts(role, status);

-- 2. Bảng refresh_tokens (Quản lý phiên đăng nhập, xoay vòng token và Session Binding)
CREATE TABLE IF NOT EXISTS refresh_tokens (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    account_id    UUID         NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
    token         VARCHAR(512) NOT NULL UNIQUE,
    client_type   VARCHAR(20)  NOT NULL DEFAULT 'WEB' CHECK (client_type IN ('WEB', 'MOBILE')),
    expired_at    TIMESTAMP    NOT NULL,
    revoked       BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at    TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_refresh_tokens_token ON refresh_tokens(token);
CREATE INDEX IF NOT EXISTS idx_refresh_tokens_account_id ON refresh_tokens(account_id);
