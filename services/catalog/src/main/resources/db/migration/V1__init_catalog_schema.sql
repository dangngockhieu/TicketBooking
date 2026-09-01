-- V1__init_catalog_schema.sql
-- Khởi tạo schema cơ sở dữ liệu cho Catalog Service (catalog_db trên port 5435)

CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- 1. Bảng categories (Danh mục sự kiện)
CREATE TABLE IF NOT EXISTS categories (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name        VARCHAR(100) NOT NULL UNIQUE,
    slug        VARCHAR(100) NOT NULL UNIQUE,
    description TEXT,
    created_at  TIMESTAMP    NOT NULL DEFAULT NOW()
);

-- 2. Bảng events (Sự kiện — Soft Key tới Auth Service qua organizer_id)
CREATE TABLE IF NOT EXISTS events (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    category_id          UUID         REFERENCES categories(id),
    organizer_id         UUID         NOT NULL,  -- Soft Key → Auth Service accounts.id
    title                VARCHAR(500) NOT NULL,
    description          TEXT,
    location             VARCHAR(500) NOT NULL,
    venue_name           VARCHAR(255),
    banner_url           VARCHAR(500),
    start_time           TIMESTAMP    NOT NULL,
    end_time             TIMESTAMP    NOT NULL,
    sale_start_time      TIMESTAMP,
    sale_end_time        TIMESTAMP,
    status               VARCHAR(20)    NOT NULL DEFAULT 'DRAFT'
                         CHECK (status IN ('DRAFT', 'PUBLISHED', 'CANCELLED', 'COMPLETED')),
    commission_rate      DECIMAL(5, 4)  NOT NULL DEFAULT 0.05 CHECK (commission_rate >= 0 AND commission_rate <= 1),
    flat_fee_per_ticket  DECIMAL(15, 2) NOT NULL DEFAULT 3000 CHECK (flat_fee_per_ticket >= 0),
    created_at           TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_events_category ON events(category_id);
CREATE INDEX IF NOT EXISTS idx_events_organizer ON events(organizer_id);
CREATE INDEX IF NOT EXISTS idx_events_status ON events(status);
CREATE INDEX IF NOT EXISTS idx_events_start_time ON events(start_time);
CREATE INDEX IF NOT EXISTS idx_events_status_start ON events(status, start_time);

-- 3. Bảng ticket_classes (Hạng vé của sự kiện)
CREATE TABLE IF NOT EXISTS ticket_classes (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id            UUID           NOT NULL REFERENCES events(id) ON DELETE CASCADE,
    name                VARCHAR(100)   NOT NULL,
    description         TEXT,
    price               DECIMAL(15, 2) NOT NULL CHECK (price >= 0),
    total_quantity      INTEGER        NOT NULL CHECK (total_quantity > 0),
    available_quantity  INTEGER        NOT NULL CHECK (available_quantity >= 0),
    sort_order          INTEGER        DEFAULT 0,
    created_at          TIMESTAMP      NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP      NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_available_lte_total CHECK (available_quantity <= total_quantity)
);

CREATE INDEX IF NOT EXISTS idx_ticket_classes_event ON ticket_classes(event_id);
