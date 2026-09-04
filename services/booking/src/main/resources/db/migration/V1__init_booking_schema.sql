-- V1__init_booking_schema.sql
-- Khởi tạo schema cơ sở dữ liệu cho Booking Service (booking_db trên port 5436)

CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- 1. Bảng bookings (Đơn đặt vé — Soft Key tới Auth Service và Catalog Service)
CREATE TABLE IF NOT EXISTS bookings (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    customer_id     UUID           NOT NULL,  -- Soft Key → Auth Service accounts.id
    event_id        UUID           NOT NULL,  -- Soft Key → Catalog Service events.id
    total_amount    DECIMAL(15, 2) NOT NULL CHECK (total_amount >= 0),
    quantity        INTEGER        NOT NULL CHECK (quantity > 0),
    status          VARCHAR(30)    NOT NULL DEFAULT 'PENDING_PAYMENT'
                    CHECK (status IN ('PENDING_PAYMENT', 'PAID', 'CANCELLED', 'REFUNDED')),
    expired_at      TIMESTAMP      NOT NULL,
    created_at      TIMESTAMP      NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP      NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_bookings_customer ON bookings(customer_id);
CREATE INDEX IF NOT EXISTS idx_bookings_event ON bookings(event_id);
CREATE INDEX IF NOT EXISTS idx_bookings_status ON bookings(status);
CREATE INDEX IF NOT EXISTS idx_bookings_expired ON bookings(status, expired_at)
    WHERE status = 'PENDING_PAYMENT';

-- 2. Bảng tickets (Vé — mỗi dòng là 1 vé vật lý, Hard FK tới bookings)
-- Ghi chú: ticket_class_name/unit_price là snapshot tại thời điểm đặt (không có
-- trong docs/database-schema.md bản gốc — bổ sung để tránh gọi lại Catalog
-- Service mỗi lần hiển thị vé, và để giá vé không đổi dù Organizer sửa giá sau
-- đó). Xem docs/database-schema.md §4 đã cập nhật.
CREATE TABLE IF NOT EXISTS tickets (
    id                UUID           PRIMARY KEY DEFAULT gen_random_uuid(),
    booking_id        UUID           NOT NULL REFERENCES bookings(id) ON DELETE CASCADE,
    ticket_class_id   UUID           NOT NULL,  -- Soft Key → Catalog Service ticket_classes.id
    ticket_class_name VARCHAR(100)   NOT NULL,
    unit_price        DECIMAL(15, 2) NOT NULL CHECK (unit_price >= 0),
    qr_code_data      VARCHAR(255)   NOT NULL UNIQUE,
    status            VARCHAR(20)    NOT NULL DEFAULT 'LOCKED'
                      CHECK (status IN ('LOCKED', 'ISSUED', 'CANCELLED', 'CHECKED_IN')),
    checked_in_at     TIMESTAMP,
    created_at        TIMESTAMP      NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMP      NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_tickets_booking ON tickets(booking_id);
CREATE INDEX IF NOT EXISTS idx_tickets_ticket_class ON tickets(ticket_class_id);
CREATE INDEX IF NOT EXISTS idx_tickets_qr_code ON tickets(qr_code_data);
CREATE INDEX IF NOT EXISTS idx_tickets_status ON tickets(status);
