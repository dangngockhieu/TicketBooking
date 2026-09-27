# 🚀 Hướng Dẫn Kiểm Thử Chịu Tải (Load Testing with k6) — TicketBooking

Thư mục này chứa các kịch bản kiểm thử hiệu năng cho **TicketBooking**, khớp với API thực tế đã triển khai (KHÔNG phải API giả định) — 3 kịch bản:
1. **Flash Sale Race Condition (`burst-booking-race-condition.js`)**: Nhiều VU tranh chấp `POST /api/bookings` cùng lúc, xác thực thuật toán Atomic Seat Hold & chống Overbooking.
2. **Virtual Waiting Room (`virtual-waiting-room-test.js`)**: Kiểm thử phòng chờ ảo qua **WebSocket STOMP** thật (không phải REST giả định) — connect, join, heartbeat, chờ được `ADMITTED`.
3. **Catalog Browse Heavy (`catalog-browse-test.js`)**: Kiểm thử tải đọc danh mục sự kiện có Redis Caching qua `GET /api/events`.

> ⚠️ Cả 3 kịch bản đều cần **dữ liệu thật** (tài khoản Customer đã ACTIVE, sự kiện PUBLISHED có hạng vé) — không có API nào trong hệ thống chấp nhận request không JWT hoặc dữ liệu giả, nên không thể chạy "cắm là chạy" như README cũ. Xem mục 2 bên dưới để chuẩn bị trước khi chạy.

---

## 1. Cài đặt k6

### Trên Windows
```powershell
winget install k6 --source winget
# hoặc: choco install k6
```

### Trên macOS
```bash
brew install k6
```

### Trên Linux (Ubuntu/Debian)
```bash
sudo gpg -k
sudo gpg --no-default-keyring --keyring /usr/share/keyrings/k6-archive-keyring.gpg --keyserver hkp://keyserver.ubuntu.com:80 --recv-keys C5AD17C747E3415A3642D57D77C6C491D6AC1D69
echo "deb [signed-by=/usr/share/keyrings/k6-archive-keyring.gpg] https://dl.k6.io/deb stable main" | sudo tee /etc/apt/sources.list.d/k6.list
sudo apt-get update
sudo apt-get install k6
```

### Hoặc chạy trực tiếp bằng Docker (không cần cài k6 vào máy)
```bash
docker run --rm -i --network=host grafana/k6 run - < k6/burst-booking-race-condition.js
```

---

## 2. Chuẩn bị dữ liệu trước khi chạy (bắt buộc)

Toàn bộ hệ thống theo Zero Trust (JWT ký RS256 bởi auth-service) — k6 **không thể giả JWT**, phải đăng nhập thật bằng tài khoản có sẵn:

1. **1 tài khoản Customer đã ACTIVE** — đăng ký qua `POST /api/auth/register`, xác thực OTP qua `POST /api/auth/verify-email` (xem `docs/api-design.md` §1). Dùng cho kịch bản 1 và fallback của kịch bản 2.
2. **1 sự kiện PUBLISHED có hạng vé** (do Organizer tạo qua `POST /api/events` rồi `PATCH /{eventId}/publish`) — lấy `eventId` và `ticketClassId` để truyền vào script.
3. **(Khuyến nghị cho kịch bản 2)** Một danh sách nhiều tài khoản Customer khác nhau — copy `k6/fixtures/queue-test-accounts.example.json` thành `queue-test-accounts.json` (đã `.gitignore`) và điền thật. Queue Service định danh người xếp hàng theo `userId` trong JWT — dùng chung 1 tài khoản cho hàng nghìn VU sẽ chỉ tính là **1 người** trong hàng chờ (Redis Sorted Set), không phản ánh đúng tải/FIFO thật.

---

## 3. Cách chạy các kịch bản kiểm thử

### Kịch bản 1: Flash Sale & Chống Overbooking
```bash
k6 run \
  -e BASE_URL=http://localhost:8080 \
  -e TEST_CUSTOMER_EMAIL=customer@example.com \
  -e TEST_CUSTOMER_PASSWORD=your_password \
  -e EVENT_ID=<uuid-sự-kiện-published> \
  -e TICKET_CLASS_ID=<uuid-hạng-vé> \
  k6/burst-booking-race-condition.js
```

### Kịch bản 2: Phòng chờ ảo (Waiting Room, WebSocket STOMP)
```bash
k6 run \
  -e BASE_URL=http://localhost:8080 \
  -e WS_URL=ws://localhost:8080 \
  -e CREDENTIALS_FILE=k6/fixtures/queue-test-accounts.json \
  -e EVENT_ID=<uuid-sự-kiện> \
  k6/virtual-waiting-room-test.js
```
Sự kiện cần đang **bật phòng chờ** — bật thủ công qua `PATCH /api/admin/queue/{eventId}/config` (`{"enabled": true}`) hoặc để `QueueAutoToggleScheduler` tự bật khi đủ tải (xem `docs/virtual-waiting-room.md` §4.2).

### Kịch bản 3: Đọc tải cao danh mục sự kiện
```bash
k6 run -e BASE_URL=http://localhost:8080 -e EVENT_ID=<uuid-sự-kiện> k6/catalog-browse-test.js
```
`EVENT_ID` là optional — không truyền thì script chỉ test `GET /api/events` (danh sách), bỏ qua bước xem chi tiết.

---

## 4. Xem Metrics thời gian thực trên Grafana

```bash
k6 run --out experimental-prometheus-rw=http://localhost:9090/api/v1/write k6/burst-booking-race-condition.js
```

* **Grafana Dashboard**: [http://localhost:3000](http://localhost:3000) (user/pass trong `.env`, mặc định `GRAFANA_ADMIN_USER`/`GRAFANA_ADMIN_PASSWORD`)
* **Prometheus Targets**: [http://localhost:9090/targets](http://localhost:9090/targets)

---

## 5. Tiêu chí nghiệm thu (Acceptance Criteria)

| Tiêu chí | Mục tiêu cam kết |
| :--- | :---: |
| **Chống Overbooking** | Số vé bán + giữ chỗ ≤ Tổng số vé phát hành 100% |
| **Response Time (p95)** | Dưới 400ms |
| **Error Rate (5xx)** | Dưới 0.1% |
| **Throughput (RPS)** | Tuỳ cấu hình máy chạy test — ghi lại số đo thực tế, không đặt con số cố định |

> Kết quả đo thực tế (khi đã chạy) ghi lại ở `docs/load-testing-report.md` — file đó chỉ điền số liệu sau khi đã thực sự chạy `k6 run`, không dùng số liệu mẫu/giả định.
