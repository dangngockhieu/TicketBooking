# 🗄 Database Schema — TicketBooking

> Thiết kế cơ sở dữ liệu chi tiết cho từng Microservice

---

## Nguyên tắc thiết kế

- **Database per Service (Physical Isolation)**: Mỗi service sở hữu riêng 1 container CSDL độc lập (5 cụm PostgreSQL riêng biệt, không dùng chung container).
- **Quản lý Migration bằng Flyway**: Mã nguồn từng service tự chịu trách nhiệm chạy các file migration (`V1__init_schema.sql`), tự động bật extension (`uuid-ossp`, `pgcrypto`) khi khởi động.
- **Soft Key (Khóa ngoại mềm)**: Tham chiếu giữa các service bằng UUID, tuyệt đối không dùng FK cứng chéo database.
- **Hard FK (Khóa ngoại cứng)**: Chỉ dùng cho các bảng nằm **trong cùng 1 service**.
- **UUID Primary Key**: Tất cả bảng sử dụng UUID v4 làm PK (`gen_random_uuid()`).
- **Audit Columns**: Mọi bảng đều có `created_at` và `updated_at`.

---

## 1. Auth Service Database

> **Container**: `postgres-auth` | **Host Port**: `5433` | **DB Name**: `auth_db`

### Bảng `accounts`

| Cột | Kiểu | Ràng buộc | Mô tả |
|-----|------|-----------|--------|
| `id` | `UUID` | **PK**, DEFAULT gen_random_uuid() | Mã tài khoản |
| `email` | `VARCHAR(255)` | **UNIQUE**, NOT NULL | Email đăng nhập |
| `password_hash` | `VARCHAR(255)` | NOT NULL | Mật khẩu đã hash (BCrypt) |
| `role` | `ENUM('ADMIN','ORGANIZER','CUSTOMER')` | NOT NULL | Vai trò |
| `status` | `ENUM('ACTIVE','LOCKED','PENDING')` | NOT NULL, DEFAULT 'PENDING' 🆕 | Trạng thái tài khoản |
| `created_at` | `TIMESTAMP` | NOT NULL, DEFAULT NOW() | Ngày tạo |
| `updated_at` | `TIMESTAMP` | NOT NULL, DEFAULT NOW() | Ngày cập nhật |

```sql
CREATE TABLE accounts (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email         VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    role          VARCHAR(20)  NOT NULL CHECK (role IN ('ADMIN', 'ORGANIZER', 'CUSTOMER')),
    status        VARCHAR(20)  NOT NULL DEFAULT 'PENDING' CHECK (status IN ('ACTIVE', 'LOCKED', 'PENDING')),
    created_at    TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_accounts_email ON accounts(email);
CREATE INDEX idx_accounts_role_status ON accounts(role, status);
```

> `status` mặc định là **`PENDING`**, nhưng **chỉ áp dụng cho `CUSTOMER` tự đăng ký** qua `POST /auth/register` (chờ xác thực email OTP, xem bên dưới).
>
> **`ORGANIZER` không đi qua `PENDING`.** Organizer không tự đăng ký nên không có bước "chờ duyệt" ở trạng thái tài khoản. Admin tạo tài khoản Organizer trực tiếp qua `POST /admin/organizers` với `status = ACTIVE` ngay (đã thẩm định giấy phép ngoài hệ thống trước đó) — xem `technical-flows.md` §0.5 và `api-design.md` §1.5. Xem luồng đầy đủ ở `technical-flows.md`.

### Redis Keys — Email Verification (Auth Service) 🆕

> Không tạo bảng SQL riêng cho OTP vì bản chất là dữ liệu tạm thời, tự hết hạn — dùng Redis TTL thay vì cột `expires_at` + job dọn dẹp.

```
Key:    email_verify:{accountId}
Type:   String
Value:  "482913"                      # OTP 6 số
TTL:    300 seconds (5 phút)

Key:    email_verify_cooldown:{accountId}
Type:   String
Value:  "1"
TTL:    60 seconds                    # chống spam nút "Gửi lại mã"
```

---

## 2. User Service Database

> **Container**: `postgres-user` | **Host Port**: `5434` | **DB Name**: `user_db`

### Bảng `profiles`

| Cột | Kiểu | Ràng buộc | Mô tả |
|-----|------|-----------|--------|
| `id` | `UUID` | **PK** | Mã hồ sơ |
| `account_id` | `UUID` | **UNIQUE**, NOT NULL | 🔗 Soft Key → Auth.accounts |
| `full_name` | `VARCHAR(255)` | NOT NULL | Họ tên |
| `phone_number` | `VARCHAR(20)` | UNIQUE | Số điện thoại |
| `avatar_url` | `VARCHAR(500)` | | URL ảnh đại diện |
| `user_type` | `VARCHAR(20)` | NOT NULL | `CUSTOMER` hoặc `ORGANIZER` |
| `metadata` | `JSONB` | DEFAULT '{}'::jsonb | Dữ liệu mở rộng (sở thích nhạc cho Customer, giấy phép tổ chức cho Organizer — **KHÔNG chứa tài khoản ngân hàng**, xem bảng `organizer_bank_accounts`) |
| `created_at` | `TIMESTAMP` | NOT NULL, DEFAULT NOW() | Ngày tạo |
| `updated_at` | `TIMESTAMP` | NOT NULL, DEFAULT NOW() | Ngày cập nhật |

```sql
CREATE TABLE profiles (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    account_id    UUID         NOT NULL UNIQUE,  -- Soft Key → Auth Service
    full_name     VARCHAR(255) NOT NULL,
    phone_number  VARCHAR(20)  UNIQUE,
    avatar_url    VARCHAR(500),
    user_type     VARCHAR(20)  NOT NULL CHECK (user_type IN ('CUSTOMER', 'ORGANIZER')),
    metadata      JSONB        DEFAULT '{}'::jsonb,
    created_at    TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_profiles_account_id ON profiles(account_id);
CREATE INDEX idx_profiles_phone ON profiles(phone_number);
CREATE INDEX idx_profiles_metadata ON profiles USING GIN (metadata);
```

### Bảng `organizer_bank_accounts` 🆕

> **1 Organizer = đúng 1 tài khoản ngân hàng cố định** — ép cứng ở tầng DB bằng `UNIQUE` trên `profile_id`, không cho phép nhiều tài khoản. Tách bảng riêng (không nhét vào `profiles.metadata`) vì đây là dữ liệu nhạy cảm dùng để chi tiền thật (payout) — cần ràng buộc SQL chặt (`NOT NULL`) và cột xác minh riêng, không thể là JSONB tự do. Đổi tài khoản phải qua luồng riêng có kiểm soát (Admin xác minh lại), không phải sửa tự do như các trường hồ sơ khác — xem `technical-flows.md §4b`.

| Cột | Kiểu | Ràng buộc | Mô tả |
|-----|------|-----------|--------|
| `id` | `UUID` | **PK** | Mã bản ghi |
| `profile_id` | `UUID` | **FK** → profiles, **UNIQUE**, NOT NULL | 🔗 Hard FK (cùng DB `user_db`) — ép 1-1 với Organizer |
| `bank_name` | `VARCHAR(100)` | NOT NULL | Tên ngân hàng |
| `bank_account_number` | `VARCHAR(50)` | NOT NULL | Số tài khoản |
| `bank_account_holder` | `VARCHAR(255)` | NOT NULL | Tên chủ tài khoản |
| `verified` | `BOOLEAN` | NOT NULL, DEFAULT FALSE | Admin đã xác minh khớp với hồ sơ pháp lý Organizer chưa |
| `verified_at` | `TIMESTAMP` | | Thời điểm xác minh |
| `created_at` | `TIMESTAMP` | NOT NULL, DEFAULT NOW() | Ngày tạo |
| `updated_at` | `TIMESTAMP` | NOT NULL, DEFAULT NOW() | Ngày cập nhật (mỗi lần đổi số TK) |

```sql
CREATE TABLE organizer_bank_accounts (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    profile_id           UUID         NOT NULL UNIQUE REFERENCES profiles(id),
    bank_name            VARCHAR(100) NOT NULL,
    bank_account_number  VARCHAR(50)  NOT NULL,
    bank_account_holder  VARCHAR(255) NOT NULL,
    verified             BOOLEAN      NOT NULL DEFAULT FALSE,
    verified_at          TIMESTAMP,
    created_at           TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_organizer_bank_accounts_profile ON organizer_bank_accounts(profile_id);
```

> `payment-service` không có bảng riêng chứa số tài khoản ngân hàng — khi tạo/xử lý `payout_requests`, nó gọi API sang `user-service` (soft-key `organizer_id` → `profiles.account_id`) để lấy bản ghi đã xác minh, rồi **snapshot** 3 cột `bank_name/bank_account_number/bank_account_holder` vào chính `payout_requests` tại thời điểm tạo (xem bảng bên dưới) — để giữ vết lịch sử "đã chuyển đúng số TK nào", KHÔNG phải để client tự điền.

---

## 3. Catalog Service Database

> **Container**: `postgres-catalog` | **Host Port**: `5435` | **DB Name**: `catalog_db`

### Bảng `categories`

| Cột | Kiểu | Ràng buộc | Mô tả |
|-----|------|-----------|--------|
| `id` | `UUID` | **PK** | Mã danh mục |
| `name` | `VARCHAR(100)` | NOT NULL, UNIQUE | Tên danh mục (Âm nhạc, Thể thao...) |
| `slug` | `VARCHAR(100)` | NOT NULL, UNIQUE | URL-friendly name |
| `description` | `TEXT` | | Mô tả danh mục |
| `created_at` | `TIMESTAMP` | NOT NULL, DEFAULT NOW() | Ngày tạo |

```sql
CREATE TABLE categories (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name        VARCHAR(100) NOT NULL UNIQUE,
    slug        VARCHAR(100) NOT NULL UNIQUE,
    description TEXT,
    created_at  TIMESTAMP    NOT NULL DEFAULT NOW()
);
```

### Bảng `events`

| Cột | Kiểu | Ràng buộc | Mô tả |
|-----|------|-----------|--------|
| `id` | `UUID` | **PK** | Mã sự kiện |
| `category_id` | `UUID` | **FK** → categories | 🔗 Hard FK |
| `organizer_id` | `UUID` | NOT NULL | 🔗 Soft Key → Auth.accounts |
| `title` | `VARCHAR(500)` | NOT NULL | Tên sự kiện |
| `description` | `TEXT` | | Mô tả chi tiết |
| `location` | `VARCHAR(500)` | NOT NULL | Địa điểm |
| `venue_name` | `VARCHAR(255)` | | Tên địa điểm |
| `banner_url` | `VARCHAR(500)` | | Ảnh bìa sự kiện |
| `start_time` | `TIMESTAMP` | NOT NULL | Thời gian bắt đầu |
| `end_time` | `TIMESTAMP` | NOT NULL | Thời gian kết thúc |
| `sale_start_time` | `TIMESTAMP` | | Thời gian mở bán vé |
| `sale_end_time` | `TIMESTAMP` | | Thời gian đóng bán vé |
| `status` | `ENUM` | NOT NULL, DEFAULT 'DRAFT' | Trạng thái sự kiện |
| `commission_rate` | `DECIMAL(5,4)` | NOT NULL, DEFAULT 0.05 | Tỷ lệ hoa hồng nền tảng (0..1), mặc định 5% |
| `flat_fee_per_ticket` | `DECIMAL(15,2)` | NOT NULL, DEFAULT 3000 | Phí cố định mỗi vé (VND), mặc định 3.000đ |
| `created_at` | `TIMESTAMP` | NOT NULL, DEFAULT NOW() | Ngày tạo |
| `updated_at` | `TIMESTAMP` | NOT NULL, DEFAULT NOW() | Ngày cập nhật |

> `commission_rate`/`flat_fee_per_ticket` chỉ ADMIN sửa được (`PATCH /admin/events/{id}/commission`), dùng để tính phí nền tảng khi tổng hợp báo cáo doanh thu và ví Organizer — xem `technical-flows.md §4b`, `api-design.md §6.4/§7.1`. Vé giá 0đ luôn miễn phí hoàn toàn (không tính `flat_fee_per_ticket`), tính ở tầng ứng dụng, không phải constraint DB.

```sql
CREATE TABLE events (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    category_id          UUID         REFERENCES categories(id),
    organizer_id         UUID         NOT NULL,  -- Soft Key → Auth Service
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

CREATE INDEX idx_events_category ON events(category_id);
CREATE INDEX idx_events_organizer ON events(organizer_id);
CREATE INDEX idx_events_status ON events(status);
CREATE INDEX idx_events_start_time ON events(start_time);
CREATE INDEX idx_events_status_start ON events(status, start_time);
```

### Bảng `ticket_classes`

| Cột | Kiểu | Ràng buộc | Mô tả |
|-----|------|-----------|--------|
| `id` | `UUID` | **PK** | Mã hạng vé |
| `event_id` | `UUID` | **FK** → events, NOT NULL | 🔗 Hard FK |
| `name` | `VARCHAR(100)` | NOT NULL | Tên hạng vé (VIP, GA...) |
| `description` | `TEXT` | | Mô tả hạng vé |
| `price` | `DECIMAL(15,2)` | NOT NULL | Giá vé (VND) |
| `total_quantity` | `INTEGER` | NOT NULL | Tổng số vé phát hành |
| `available_quantity` | `INTEGER` | NOT NULL | Số vé còn lại có thể bán |
| `sort_order` | `INTEGER` | DEFAULT 0 | Thứ tự hiển thị |
| `created_at` | `TIMESTAMP` | NOT NULL, DEFAULT NOW() | Ngày tạo |
| `updated_at` | `TIMESTAMP` | NOT NULL, DEFAULT NOW() | Ngày cập nhật |

```sql
CREATE TABLE ticket_classes (
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

CREATE INDEX idx_ticket_classes_event ON ticket_classes(event_id);
```

---

## 4. Booking Service Database

> **Container**: `postgres-booking` | **Host Port**: `5436` | **DB Name**: `booking_db` + **Redis** (`6379`)

### Bảng `bookings`

| Cột | Kiểu | Ràng buộc | Mô tả |
|-----|------|-----------|--------|
| `id` | `UUID` | **PK** | Mã đơn hàng |
| `customer_id` | `UUID` | NOT NULL | 🔗 Soft Key → Auth.accounts |
| `event_id` | `UUID` | NOT NULL | 🔗 Soft Key → Catalog.events |
| `total_amount` | `DECIMAL(15,2)` | NOT NULL | Tổng tiền |
| `quantity` | `INTEGER` | NOT NULL | Tổng số vé đặt |
| `status` | `ENUM` | NOT NULL, DEFAULT 'PENDING_PAYMENT' | Trạng thái đơn |
| `expired_at` | `TIMESTAMP` | NOT NULL | Hết hạn giữ chỗ (created_at + 10 min) |
| `created_at` | `TIMESTAMP` | NOT NULL, DEFAULT NOW() | Ngày tạo |
| `updated_at` | `TIMESTAMP` | NOT NULL, DEFAULT NOW() | Ngày cập nhật |

**Status Flow:**
```
PENDING_PAYMENT → PAID → (done)
PENDING_PAYMENT → CANCELLED (hết hạn hoặc user hủy)
PAID → REFUNDED (Saga compensation)
```

```sql
CREATE TABLE bookings (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    customer_id     UUID           NOT NULL,  -- Soft Key → Auth Service
    event_id        UUID           NOT NULL,  -- Soft Key → Catalog Service
    total_amount    DECIMAL(15, 2) NOT NULL CHECK (total_amount >= 0),
    quantity        INTEGER        NOT NULL CHECK (quantity > 0),
    status          VARCHAR(30)    NOT NULL DEFAULT 'PENDING_PAYMENT'
                    CHECK (status IN ('PENDING_PAYMENT', 'PAID', 'CANCELLED', 'REFUNDED')),
    expired_at      TIMESTAMP      NOT NULL,
    created_at      TIMESTAMP      NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP      NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_bookings_customer ON bookings(customer_id);
CREATE INDEX idx_bookings_event ON bookings(event_id);
CREATE INDEX idx_bookings_status ON bookings(status);
CREATE INDEX idx_bookings_expired ON bookings(status, expired_at)
    WHERE status = 'PENDING_PAYMENT';
```

### Bảng `tickets`

| Cột | Kiểu | Ràng buộc | Mô tả |
|-----|------|-----------|--------|
| `id` | `UUID` | **PK** | Mã vé |
| `booking_id` | `UUID` | **FK** → bookings, NOT NULL | 🔗 Hard FK |
| `ticket_class_id` | `UUID` | NOT NULL | 🔗 Soft Key → Catalog.ticket_classes |
| `ticket_class_name` | `VARCHAR(100)` | NOT NULL | Snapshot tên hạng vé tại thời điểm đặt 🆕 |
| `unit_price` | `DECIMAL(15,2)` | NOT NULL | Snapshot đơn giá tại thời điểm đặt — không đổi dù Organizer sửa giá sau đó 🆕 |
| `qr_code_data` | `VARCHAR(255)` | UNIQUE, NOT NULL | Mã QR duy nhất (UUID) |
| `status` | `ENUM` | NOT NULL, DEFAULT 'LOCKED' | Trạng thái vé |
| `checked_in_at` | `TIMESTAMP` | | Thời điểm check-in |
| `created_at` | `TIMESTAMP` | NOT NULL, DEFAULT NOW() | Ngày tạo |
| `updated_at` | `TIMESTAMP` | NOT NULL, DEFAULT NOW() | Ngày cập nhật |

**Status Flow:**
```
LOCKED → ISSUED (khi thanh toán thành công)
LOCKED → CANCELLED (khi booking bị hủy)
ISSUED → CHECKED_IN (khi quét QR tại sự kiện)
```

```sql
CREATE TABLE tickets (
    id                UUID           PRIMARY KEY DEFAULT gen_random_uuid(),
    booking_id        UUID           NOT NULL REFERENCES bookings(id) ON DELETE CASCADE,
    ticket_class_id   UUID           NOT NULL,  -- Soft Key → Catalog Service
    ticket_class_name VARCHAR(100)   NOT NULL,  -- Snapshot tại thời điểm đặt
    unit_price        DECIMAL(15, 2) NOT NULL CHECK (unit_price >= 0), -- Snapshot tại thời điểm đặt
    qr_code_data      VARCHAR(255)   NOT NULL UNIQUE,
    status            VARCHAR(20)    NOT NULL DEFAULT 'LOCKED'
                      CHECK (status IN ('LOCKED', 'ISSUED', 'CANCELLED', 'CHECKED_IN')),
    checked_in_at     TIMESTAMP,
    created_at        TIMESTAMP      NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMP      NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_tickets_booking ON tickets(booking_id);
CREATE INDEX idx_tickets_ticket_class ON tickets(ticket_class_id);
CREATE INDEX idx_tickets_qr_code ON tickets(qr_code_data);
CREATE INDEX idx_tickets_status ON tickets(status);
```

### Redis Keys (Tạm thời — Seat Holding)

```
# Key pattern: giữ chỗ tạm thời cho user
Key:    seat_hold:{event_id}:{ticket_class_id}:{customer_id}
Value:  { "bookingId": "uuid", "quantity": 2 }
TTL:    600 seconds (10 phút)

# Key pattern: đếm tổng số vé đang bị hold cho 1 ticket class
Key:    hold_count:{event_id}:{ticket_class_id}
Value:  integer (atomic counter, dùng INCRBY/DECRBY)
TTL:    không set (clean up bằng background job)
```

> **Lưu ý**: Dùng `hold_count` để tính `realAvailable = available_quantity - hold_count`. Điều này giúp hiển thị chính xác số vé còn trống cho người dùng khác.

---

## 5. Payment Service Database

> **Container**: `postgres-payment` | **Host Port**: `5437` | **DB Name**: `payment_db`

### Bảng `transactions`

| Cột | Kiểu | Ràng buộc | Mô tả |
|-----|------|-----------|--------|
| `id` | `UUID` | **PK** | Mã giao dịch nội bộ |
| `booking_id` | `UUID` | NOT NULL | 🔗 Soft Key → Booking.bookings |
| `event_id` | `UUID` | NULL | 🔗 Soft Key → Catalog.events — snapshot tại `initiate()`. MoMo IPN không mang JWT khách nên không forward được sang Booking Service để tra lại; cần trực tiếp để tính phí ví Organizer 🆕 |
| `quantity` | `INTEGER` | NULL | Snapshot số lượng vé tại `initiate()` — dùng tính `flat_fee_per_ticket` 🆕 |
| `amount` | `DECIMAL(15,2)` | NOT NULL | Số tiền thanh toán |
| `payment_method` | `VARCHAR(50)` | NOT NULL | Phương thức (MOMO) |
| `gateway_trans_id` | `VARCHAR(255)` | UNIQUE | Mã giao dịch từ cổng thanh toán |
| `status` | `ENUM` | NOT NULL, DEFAULT 'PENDING' | Trạng thái giao dịch |
| `gateway_response` | `JSONB` | | Response gốc từ cổng thanh toán |
| `paid_at` | `TIMESTAMP` | | Thời điểm thanh toán thành công |
| `created_at` | `TIMESTAMP` | NOT NULL, DEFAULT NOW() | Ngày tạo |
| `updated_at` | `TIMESTAMP` | NOT NULL, DEFAULT NOW() | Ngày cập nhật |

```sql
CREATE TABLE transactions (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    booking_id        UUID           NOT NULL,  -- Soft Key → Booking Service
    event_id          UUID,                     -- Soft Key → Catalog Service, snapshot tại initiate()
    quantity          INTEGER,                  -- Snapshot số lượng vé tại initiate()
    amount            DECIMAL(15, 2) NOT NULL CHECK (amount > 0),
    payment_method    VARCHAR(50)    NOT NULL,
    gateway_trans_id  VARCHAR(255)   UNIQUE,
    status            VARCHAR(20)    NOT NULL DEFAULT 'PENDING'
                      CHECK (status IN ('PENDING', 'SUCCESS', 'FAILED', 'REFUNDED')),
    gateway_response  JSONB,
    paid_at           TIMESTAMP,
    created_at        TIMESTAMP      NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMP      NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_transactions_booking ON transactions(booking_id);
CREATE INDEX idx_transactions_status ON transactions(status);
CREATE INDEX idx_transactions_gateway ON transactions(gateway_trans_id);
```

### Bảng `organizer_wallets` 🆕

| Cột | Kiểu | Ràng buộc | Mô tả |
|-----|------|-----------|--------|
| `id` | `UUID` | **PK** | |
| `organizer_id` | `UUID` | **UNIQUE**, NOT NULL | 🔗 Soft Key → Auth.accounts |
| `available_balance` | `DECIMAL(15,2)` | NOT NULL, DEFAULT 0 | Số dư khả dụng, có thể xin rút |
| `pending_payout` | `DECIMAL(15,2)` | NOT NULL, DEFAULT 0 | Đang chờ xử lý payout |
| `total_withdrawn` | `DECIMAL(15,2)` | NOT NULL, DEFAULT 0 | Tổng đã rút thành công (lũy kế) |
| `updated_at` | `TIMESTAMP` | NOT NULL, DEFAULT NOW() | |

```sql
CREATE TABLE organizer_wallets (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organizer_id       UUID           NOT NULL UNIQUE,  -- Soft Key → Auth Service
    available_balance  DECIMAL(15, 2) NOT NULL DEFAULT 0,
    pending_payout     DECIMAL(15, 2) NOT NULL DEFAULT 0,
    total_withdrawn    DECIMAL(15, 2) NOT NULL DEFAULT 0,
    updated_at         TIMESTAMP      NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_organizer_wallets_organizer ON organizer_wallets(organizer_id);
```

### Bảng `payout_requests` 🆕

| Cột | Kiểu | Ràng buộc | Mô tả |
|-----|------|-----------|--------|
| `id` | `UUID` | **PK** | |
| `organizer_id` | `UUID` | NOT NULL | 🔗 Soft Key → Auth.accounts |
| `amount` | `DECIMAL(15,2)` | NOT NULL | Số tiền yêu cầu rút |
| `bank_name` | `VARCHAR(100)` | NOT NULL | **Snapshot tự động** từ `organizer_bank_accounts` (User Service) tại thời điểm tạo — KHÔNG do client điền |
| `bank_account_number` | `VARCHAR(50)` | NOT NULL | Snapshot tự động, xem trên |
| `bank_account_holder` | `VARCHAR(255)` | NOT NULL | Snapshot tự động, xem trên |
| `status` | `ENUM` | NOT NULL, DEFAULT 'PENDING' | `PENDING`/`APPROVED`/`REJECTED`/`PAID`/`HOLD` |
| `source` | `ENUM` | NOT NULL | `AUTO` (job nền 7 ngày sau event) / `MANUAL` (Organizer tự xin) |
| `event_id` | `UUID` | NULL | 🔗 Soft Key → Catalog.events (chỉ khi `source=AUTO`) |
| `reason` | `TEXT` | NULL | Bắt buộc khi `REJECTED` hoặc `HOLD` |
| `momo_disbursement_id` | `VARCHAR(255)` | NULL, UNIQUE | `requestId` gửi lên MoMo Disbursement API, dùng để đối soát/tránh gọi trùng |
| `created_at` | `TIMESTAMP` | NOT NULL, DEFAULT NOW() | |
| `processed_at` | `TIMESTAMP` | NULL | Thời điểm chuyển sang trạng thái cuối (`PAID`/`REJECTED`) |

```sql
CREATE TABLE payout_requests (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organizer_id          UUID           NOT NULL,  -- Soft Key → Auth Service
    amount                DECIMAL(15, 2) NOT NULL CHECK (amount > 0),
    -- 3 cột dưới là snapshot tự động lấy từ organizer_bank_accounts (User Service)
    -- tại thời điểm tạo request — KHÔNG phải field client tự điền
    bank_name             VARCHAR(100)   NOT NULL,
    bank_account_number   VARCHAR(50)    NOT NULL,
    bank_account_holder   VARCHAR(255)   NOT NULL,
    status                VARCHAR(20)    NOT NULL DEFAULT 'PENDING'
                          CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'PAID', 'HOLD')),
    source                VARCHAR(10)    NOT NULL CHECK (source IN ('AUTO', 'MANUAL')),
    event_id              UUID,          -- Soft Key → Catalog Service, chỉ khi source=AUTO
    reason                TEXT,
    momo_disbursement_id  VARCHAR(255)   UNIQUE,
    created_at            TIMESTAMP      NOT NULL DEFAULT NOW(),
    processed_at          TIMESTAMP,

    CONSTRAINT chk_reason_required CHECK (status NOT IN ('REJECTED', 'HOLD') OR reason IS NOT NULL)
);

CREATE INDEX idx_payout_requests_organizer ON payout_requests(organizer_id);
CREATE INDEX idx_payout_requests_status ON payout_requests(status);
CREATE UNIQUE INDEX idx_payout_requests_event_auto ON payout_requests(event_id)
    WHERE source = 'AUTO';  -- mỗi event chỉ có tối đa 1 payout AUTO
```

---

## 6. Notification Service Database

> **Container**: `mongodb` | **Host Port**: `27017` | **DB Name**: `notification_db` | **Collection**: `notification_logs`

### Collection `notification_logs`

```json
{
  "_id": "ObjectId",
  "recipientEmail": "customer@email.com",
  "recipientId": "customer-uuid",
  "eventType": "TICKET_ISSUED",
  "subject": "🎫 Vé của bạn đã sẵn sàng - Sơn Tùng MTP Live Concert",
  "content": {
    "bookingId": "booking-uuid",
    "eventTitle": "Sơn Tùng MTP Live Concert",
    "tickets": [
      {
        "ticketId": "ticket-uuid",
        "ticketClass": "VIP",
        "qrCodeData": "unique-qr-data"
      }
    ]
  },
  "channel": "EMAIL",
  "status": "SENT",
  "retryCount": 0,
  "errorMessage": null,
  "sentAt": "2024-01-01T12:00:10Z",
  "createdAt": "2024-01-01T12:00:05Z"
}
```

**Indexes:**
```javascript
db.notification_logs.createIndex({ recipientEmail: 1 });
db.notification_logs.createIndex({ eventType: 1, status: 1 });
db.notification_logs.createIndex({ createdAt: 1 }, { expireAfterSeconds: 7776000 }); // TTL 90 days
```

---

## ER Diagram (Tổng quan quan hệ)

```
      ┌──────────────────┐         ┌──────────────────┐
      │  Auth Service    │         │   User Service   │
      │                  │         │                  │
      │  ┌────────────┐  │  soft   │  ┌─────────────┐ │
      │  │  accounts  │◄─┼─────────┼──│  profiles   │ │
      │  └────────────┘  │   key   │  └─────────────┘ │
      └────────┬─────────┘         └──────────────────┘
               │ soft key
               ▼
┌────────────────────────────────────────────────────────────────┐
│                       Catalog Service                          │
│                                                                │
│ ┌──────────┐    FK     ┌────────┐    FK     ┌────────────────┐ │
│ │categories│◄──────────│ events │◄──────────│ ticket_classes │ │
│ └──────────┘           └────────┘           └────────────────┘ │
└──────────────────────────┬─────────────────────────────────────┘
                           │ soft key
                           ▼
        ┌──────────────────────────────────────────────┐
        │                Booking Service               │
        │                                              │
        │       ┌──────────┐    FK     ┌─────────┐     │
        │       │ bookings │◄──────────│ tickets │     │
        │       └──────────┘           └─────────┘     │
        │              + Redis (seat holding)          │
        └──────────────────────┬───────────────────────┘
                               │ soft key
                               ▼
                ┌──────────────────────────────┐
                │       Payment Service        │
                │                              │
                │       ┌──────────────┐       │
                │       │ transactions │       │
                │       └──────────────┘       │
                └──────────────────────────────┘
```

---

> 📄 Xem thêm: [System Design](system-design.md) | [API Design](api-design.md) | [Technical Flows](technical-flows.md)
