-- V2__add_require_password_change.sql
-- Hỗ trợ luồng "Admin cấp tài khoản Organizer với mật khẩu tạm": khi true, tài khoản
-- bắt buộc đổi mật khẩu (PUT /auth/change-password) trước khi được dùng các API khác.
-- Xem: docs/technical-flows.md §0.5, docs/api-design.md §1.5 (repo gốc).

ALTER TABLE accounts
    ADD COLUMN IF NOT EXISTS require_password_change BOOLEAN NOT NULL DEFAULT FALSE;
