-- Atomic check-and-increment cho giữ chỗ (xem docs/database-schema.md §4 "Redis Keys").
-- KEYS[1] = hold_count:{event_id}:{ticket_class_id}
-- ARGV[1] = số lượng vé muốn giữ (requested)
-- ARGV[2] = available_quantity lấy từ Catalog Service tại thời điểm gọi
--
-- Trả về -1 nếu không đủ vé (không có gì bị thay đổi trong Redis).
-- Trả về giá trị hold_count MỚI (đã cộng dồn) nếu giữ chỗ thành công.
local current = tonumber(redis.call('GET', KEYS[1]) or '0')
local requested = tonumber(ARGV[1])
local available = tonumber(ARGV[2])

if current + requested > available then
    return -1
end

return redis.call('INCRBY', KEYS[1], requested)
