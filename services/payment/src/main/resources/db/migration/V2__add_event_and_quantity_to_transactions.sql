-- V2__add_event_and_quantity_to_transactions.sql
-- Snapshot event_id/quantity tại thời điểm initiate() — cần để tính tiền vào
-- ví Organizer (commission_rate/flat_fee_per_ticket) khi xử lý MoMo IPN, vốn
-- không mang theo JWT của khách nên không thể forward sang Booking Service để
-- tra cứu lại (xem Transaction.java, docs/database-schema.md §5 đã cập nhật).

ALTER TABLE transactions ADD COLUMN IF NOT EXISTS event_id UUID;
ALTER TABLE transactions ADD COLUMN IF NOT EXISTS quantity INTEGER;

CREATE INDEX IF NOT EXISTS idx_transactions_event ON transactions(event_id);
