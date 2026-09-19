-- Idempotent consumer guard cho Kafka topic tickets.generated (xem
-- docs/development-plan.md GĐ4 bảng rủi ro: "Event trùng lặp").
-- Mỗi bookingId chỉ được phép trừ available_quantity đúng 1 lần, kể cả khi
-- Kafka redeliver message.
CREATE TABLE IF NOT EXISTS processed_ticket_events (
    booking_id   UUID PRIMARY KEY,
    processed_at TIMESTAMP NOT NULL DEFAULT NOW()
);
