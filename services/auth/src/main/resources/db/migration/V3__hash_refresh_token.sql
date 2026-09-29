-- refresh_tokens.token chuyển từ lưu JWT thô sang lưu SHA-256 hash (hex, 64 ký tự)
-- của JWT: (1) độ dài cố định, không còn phụ thuộc số claim trong token; (2) nếu
-- DB bị lộ, không ai dùng lại được token thô. Token cũ (thô) không thể suy ra
-- hash tương ứng nên xóa luôn — chấp nhận được vì đây là phiên đăng nhập, người
-- dùng chỉ cần đăng nhập lại.
DELETE FROM refresh_tokens;

ALTER TABLE refresh_tokens ALTER COLUMN token TYPE VARCHAR(64);
