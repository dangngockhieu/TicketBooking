# 🗄 Database Schema — TicketBooking

> Thiết kế cơ sở dữ liệu chi tiết cho từng Microservice

---

## Nguyên tắc thiết kế

- **Database per Service**: Mỗi service sở hữu CSDL riêng biệt
- **Soft Key (Khóa ngoại mềm)**: Tham chiếu giữa service bằng UUID, không dùng FK ràng buộc
- **Hard FK (Khóa ngoại cứng)**: Chỉ dùng cho các bảng **trong cùng 1 service**
- **UUID Primary Key**: Tất cả bảng sử dụng UUID v4 làm PK
- **Audit Columns**: Mọi bảng đều có `created_at` và `updated_at`

---

## 1. Auth Service Database

> **Engine**: PostgreSQL | **Schema**: `auth`

### Bảng `accounts`

| Cột | Kiểu | Ràng buộc | Mô tả |
|-----|------|-----------|--------|
| `id` | `UUID` | **PK**, DEFAULT gen_random_uuid() | Mã tài khoản |
| `email` | `VARCHAR(255)` | **UNIQUE**, NOT NULL | Email đăng nhập |
| `password_hash` | `VARCHAR(255)` | NOT NULL | Mật khẩu đã hash (BCrypt) |
| `role` | `ENUM('ADMIN','ORGANIZER','CUSTOMER')` | NOT NULL | Vai trò |
| `status` | `ENUM('ACTIVE','LOCKED','PENDING')` | NOT NULL, DEFAULT 'ACTIVE' | Trạng thái tài khoản |
| `created_at` | `TIMESTAMP` | NOT NULL, DEFAULT NOW() | Ngày tạo |
| `updated_at` | `TIMESTAMP` | NOT NULL, DEFAULT NOW() | Ngày cập nhật |

```sql
CREATE TABLE accounts (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email         VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    role          VARCHAR(20)  NOT NULL CHECK (role IN ('ADMIN', 'ORGANIZER', 'CUSTOMER')),
    status        VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'LOCKED', 'PENDING')),
    created_at    TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_accounts_email ON accounts(email);
CREATE INDEX idx_accounts_role_status ON accounts(role, status);
```

---

## 2. User Service Database

> **Engine**: PostgreSQL | **Schema**: `user_profile`

### Bảng `profiles`

| Cột | Kiểu | Ràng buộc | Mô tả |
|-----|------|-----------|--------|
| `id` | `UUID` | **PK** | Mã hồ sơ |
| `account_id` | `UUID` | **UNIQUE**, NOT NULL | 🔗 Soft Key → Auth.accounts |
| `full_name` | `VARCHAR(255)` | NOT NULL | Họ tên |
| `phone_number` | `VARCHAR(20)` | | Số điện thoại |
| `avatar_url` | `VARCHAR(500)` | | URL ảnh đại diện |
| `created_at` | `TIMESTAMP` | NOT NULL, DEFAULT NOW() | Ngày tạo |
| `updated_at` | `TIMESTAMP` | NOT NULL, DEFAULT NOW() | Ngày cập nhật |

```sql
CREATE TABLE profiles (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    account_id    UUID         NOT NULL UNIQUE,  -- Soft Key → Auth Service
    full_name     VARCHAR(255) NOT NULL,
    phone_number  VARCHAR(20),
    avatar_url    VARCHAR(500),
    created_at    TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_profiles_account_id ON profiles(account_id);
```

---

## 3. Catalog Service Database

> **Engine**: PostgreSQL | **Schema**: `catalog`

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
| `created_at` | `TIMESTAMP` | NOT NULL, DEFAULT NOW() | Ngày tạo |
| `updated_at` | `TIMESTAMP` | NOT NULL, DEFAULT NOW() | Ngày cập nhật |

```sql
CREATE TABLE events (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    category_id     UUID         REFERENCES categories(id),
    organizer_id    UUID         NOT NULL,  -- Soft Key → Auth Service
    title           VARCHAR(500) NOT NULL,
    description     TEXT,
    location        VARCHAR(500) NOT NULL,
    venue_name      VARCHAR(255),
    banner_url      VARCHAR(500),
    start_time      TIMESTAMP    NOT NULL,
    end_time        TIMESTAMP    NOT NULL,
    sale_start_time TIMESTAMP,
    sale_end_time   TIMESTAMP,
    status          VARCHAR(20)  NOT NULL DEFAULT 'DRAFT'
                    CHECK (status IN ('DRAFT', 'PUBLISHED', 'CANCELLED', 'COMPLETED')),
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP    NOT NULL DEFAULT NOW()
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

> **Engine**: PostgreSQL + Redis | **Schema**: `booking`

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
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    booking_id      UUID         NOT NULL REFERENCES bookings(id) ON DELETE CASCADE,
    ticket_class_id UUID         NOT NULL,  -- Soft Key → Catalog Service
    qr_code_data    VARCHAR(255) NOT NULL UNIQUE,
    status          VARCHAR(20)  NOT NULL DEFAULT 'LOCKED'
                    CHECK (status IN ('LOCKED', 'ISSUED', 'CANCELLED', 'CHECKED_IN')),
    checked_in_at   TIMESTAMP,
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_tickets_booking ON tickets(booking_id);
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

> **Engine**: PostgreSQL | **Schema**: `payment`

### Bảng `transactions`

| Cột | Kiểu | Ràng buộc | Mô tả |
|-----|------|-----------|--------|
| `id` | `UUID` | **PK** | Mã giao dịch nội bộ |
| `booking_id` | `UUID` | NOT NULL | 🔗 Soft Key → Booking.bookings |
| `amount` | `DECIMAL(15,2)` | NOT NULL | Số tiền thanh toán |
| `payment_method` | `VARCHAR(50)` | NOT NULL | Phương thức (VNPAY, STRIPE...) |
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

---

## 6. Notification Service Database

> **Engine**: MongoDB | **Collection**: `notification_logs`

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
      ┌──────────────────┐         ┌───────────────────┐
      │  Auth Service    │         │   User Service    │
      │                  │         │                   │
      │  ┌────────────┐  │  soft   │  ┌─────────────┐  │
      │  │  accounts  │◄─┼─────────┼──│  profiles   │  │
      │  └────────────┘  │   key   │  └─────────────┘  │
      └────────┬─────────┘         └───────────────────┘
               │ soft key
               ▼
┌────────────────────────────────────────────────────────────────┐
│                       Catalog Service                          │
│                                                                │
│ ┌──────────┐    FK     ┌────────┐    FK     ┌────────────────┐ │
│ │categories│◄──────────│ events │◄──────────│ ticket_classes │ │
│ └──────────┘           └────────┘           └────────────────┘ │
└──────────────────────────────┬─────────────────────────────────┘
                               │ soft key
                               ▼
        ┌────────────────────────────────────────────────┐
        │                Booking Service                 │
        │                                                │
        │       ┌──────────┐    FK     ┌─────────┐       │
        │       │ bookings │◄──────────│ tickets │       │
        │       └──────────┘           └─────────┘       │
        │              + Redis (seat holding)            │
        └──────────────────────┬─────────────────────────┘
                               │ soft key
                               ▼
                ┌───────────────────────────────┐
                │       Payment Service         │
                │                               │
                │       ┌──────────────┐        │
                │       │ transactions │        │
                │       └──────────────┘        │
                └───────────────────────────────┘
```

---

> 📄 Xem thêm: [System Design](system-design.md) | [API Design](api-design.md) | [Technical Flows](technical-flows.md)
