# 📐 Thiết Kế Hệ Thống — TicketBooking

> Tài liệu thiết kế tổng quan cho Hệ thống Đặt vé Sự kiện phân tán

---

## 1. Tổng quan dự án

### 1.1. Mục tiêu

**TicketBooking** là nền tảng thương mại điện tử chuyên biệt cho việc phân phối vé sự kiện, được thiết kế để giải quyết các bài toán:

| Thách thức | Giải pháp |
|------------|-----------|
| Burst Traffic (hàng ngàn request/giây khi mở bán) | Kiến trúc Microservices + Horizontal Scaling |
| Overbooking (bán vượt quá số lượng vé) | Redis Distributed Lock + Atomic Operations |
| Eventual Consistency (nhất quán dữ liệu phân tán) | Apache Kafka + Saga Pattern |
| Single Point of Failure | Database per Service + Service Independence |

### 1.2. Mô hình kinh doanh

```
B2B2C (Mô hình đóng)

   ┌──────────┐     Kiểm duyệt    ┌─────────────┐     Mua vé      ┌────────────┐
   │  Admin   │──────────────────►│  Organizer  │◄────────────────│  Customer  │
   │ (Nội bộ) │   Cấp tài khoản   │(Ban tổ chức)│     Đặt vé      │(Khách hàng)│
   └──────────┘                   └─────────────┘                 └────────────┘
                                    Đăng sự kiện                   Đăng ký tự do
```

- **Admin**: Kiểm duyệt và cấp tài khoản cho Organizer → chống lừa đảo
- **Organizer**: Đăng tải sự kiện, quản lý vé, check-in
- **Customer**: Đăng ký tự do, tìm kiếm và mua vé

### 1.3. Core Technologies

| Công nghệ | Vai trò |
|------------|---------|
| RESTful API | Giao tiếp đồng bộ giữa các service |
| Apache Kafka | Message Broker cho giao tiếp bất đồng bộ |
| Redis | Distributed Lock & Caching |
| JWT | Xác thực và phân quyền |

---

## 2. Phân tích Actor và Use Case

### 2.1. Customer (Khách hàng)

| # | Use Case | Mô tả |
|---|----------|--------|
| UC-C1 | Đăng ký / Đăng nhập | Tạo tài khoản mới hoặc đăng nhập bằng email/password |
| UC-C2 | Tìm kiếm & Lọc sự kiện | Tìm theo Category (Âm nhạc, Thể thao...), thời gian, địa điểm |
| UC-C3 | Xem tình trạng vé (Real-time) | Xem số lượng vé còn trống theo thời gian thực |
| UC-C4 | Giữ chỗ tạm thời (Seat Holding) | Khóa số lượng vé mong muốn trong **10 phút** chờ thanh toán |
| UC-C5 | Thanh toán | Thanh toán qua cổng tích hợp (VNPay/Stripe) |
| UC-C6 | Xem lịch sử đặt vé | Xem danh sách các đơn hàng đã đặt |
| UC-C7 | Nhận E-Ticket | Nhận mã QR qua email sau khi thanh toán thành công |

### 2.2. Organizer (Ban tổ chức)

| # | Use Case | Mô tả |
|---|----------|--------|
| UC-O1 | Quản lý sự kiện | Tạo/sửa/xóa sự kiện, cấu hình thời gian mở/đóng bán vé |
| UC-O2 | Quản lý hạng vé | Định nghĩa loại vé (VVIP, VIP, GA), giá, số lượng phát hành |
| UC-O3 | Check-in QR | Quét mã QR tại cổng sự kiện để xác minh vé |
| UC-O4 | Xem báo cáo | Thống kê số lượng vé bán ra và doanh thu thực tế |

### 2.3. Admin (Quản trị viên)

| # | Use Case | Mô tả |
|---|----------|--------|
| UC-A1 | Quản lý Organizer | Duyệt/cấp/khóa tài khoản Organizer |
| UC-A2 | Quản lý danh mục | Tạo/sửa các Category sự kiện |
| UC-A3 | Giám sát hệ thống | Xem dashboard tổng quan |

### 2.4. System Background (Hệ thống chạy ngầm)

| # | Use Case | Mô tả |
|---|----------|--------|
| UC-S1 | Auto-Release Seat | Hủy đơn hàng & nhả vé nếu không thanh toán sau 10 phút |
| UC-S2 | Event-driven Notification | Gửi email QR code khi thanh toán thành công |
| UC-S3 | Saga Rollback/Refund | Hoàn tiền nếu hệ thống sinh vé lỗi sau khi đã trừ tiền |

---

## 3. Thiết kế Microservices

### 3.1. Tổng quan các Service

```
┌─────────────────────────────────────────────────────────────────────┐
│                         API Gateway                                 │
│                    (Spring Cloud Gateway)                           │
│              Routing, Rate Limiting, JWT Validation                 │
└─────────┬───────────┬───────────┬──────────┬───────────┬────────────┘
          │           │           │          │           │
    ┌─────▼────┐ ┌────▼─────┐ ┌───▼───┐ ┌────▼───┐ ┌─────▼────┐
    │   Auth   │ │   User   │ │Catalog│ │Booking │ │  Payment │
    │ Service  │ │  Service │ │Service│ │Service │ │  Service │
    │          │ │          │ │       │ │        │ │          │
    │PostgreSQL│ │PostgreSQL│ │ PgSQL │ │  PgSQL │ │PostgreSQL│
    └──────────┘ └──────────┘ └───────┘ │ +Redis │ └─────┬────┘
                                        └───┬────┘       │
                                            │      ┌─────▼─────┐
                                            │      │   Kafka   │
                                            │      │   Broker  │
                                            │      └─────┬─────┘
                                            │            │
                                            │     ┌──────▼───────┐
                                            │     │ Notification │
                                            │     │  Service     │
                                            │     │  MongoDB     │
                                            └─────┴──────────────┘
```

### 3.2. Chi tiết từng Service

| Service | Trách nhiệm | Database | Giao tiếp |
|---------|-------------|----------|-----------|
| **Auth Service** | Đăng nhập, cấp JWT, phân quyền (RBAC) | PostgreSQL | Sync (REST) |
| **User Service** | Quản lý profile người dùng | PostgreSQL | Sync (REST) |
| **Catalog Service** | Quản lý danh mục, sự kiện, hạng vé | PostgreSQL | Sync (REST) + Kafka Consumer |
| **Booking Service** | Đặt vé, giữ chỗ, quản lý đơn hàng | PostgreSQL + Redis | Sync (REST) + Kafka Producer/Consumer |
| **Payment Service** | Xử lý thanh toán, tích hợp VNPay | PostgreSQL | Sync (REST) + Kafka Producer |
| **Notification Service** | Gửi email, lưu log thông báo | MongoDB | Kafka Consumer only |

### 3.3. Nguyên tắc thiết kế

- **Database per Service**: Mỗi service có CSDL riêng, không dùng chung
- **Soft Key (Khóa ngoại mềm)**: Tham chiếu giữa các service bằng ID, không dùng FK cứng
- **Async by Default**: Ưu tiên giao tiếp bất đồng bộ qua Kafka cho các luồng không cần response ngay
- **Idempotency**: Mọi Kafka consumer phải xử lý idempotent (nhận cùng 1 event 2 lần không gây side effect)

---

## 4. Kafka Topics & Events

### 4.1. Danh sách Topics

| Topic | Producer | Consumer(s) | Mô tả |
|-------|----------|-------------|--------|
| `payment.success` | Payment Service | Booking Service | Thanh toán thành công |
| `payment.failed` | Payment Service | Booking Service | Thanh toán thất bại |
| `tickets.generated` | Booking Service | Catalog Service, Notification Service | Vé đã được phát hành |
| `booking.cancelled` | Booking Service | Catalog Service | Đơn hàng bị hủy (hết hạn hold) |
| `booking.refund-requested` | Booking Service | Payment Service | Yêu cầu hoàn tiền (Saga compensation) |

### 4.2. Event Schema (Ví dụ)

```json
// Payment Success Event
{
  "eventId": "uuid-v4",
  "eventType": "PAYMENT_SUCCESS",
  "timestamp": "2024-01-01T12:00:00Z",
  "payload": {
    "bookingId": "booking-uuid",
    "transactionId": "txn-uuid",
    "amount": 500000,
    "paymentMethod": "VNPAY"
  }
}

// Tickets Generated Event
{
  "eventId": "uuid-v4",
  "eventType": "TICKETS_GENERATED",
  "timestamp": "2024-01-01T12:00:05Z",
  "payload": {
    "bookingId": "booking-uuid",
    "eventId": "event-uuid",
    "tickets": [
      {
        "ticketId": "ticket-uuid-1",
        "ticketClassId": "class-uuid",
        "qrCodeData": "unique-qr-data-1"
      }
    ],
    "customerEmail": "customer@email.com"
  }
}
```

---

## 5. Bảo mật & Xác thực

### 5.1. JWT Flow

```
Customer                API Gateway              Auth Service
   │                        │                        │
   │── POST /auth/login ───►│── Forward ────────────►│
   │                        │                        │── Validate credentials
   │                        │◄── JWT Token ──────────│
   │◄── JWT Token ──────────│                        │
   │                        │                        │
   │── GET /events ────────►│── Validate JWT ───────►│
   │   (Bearer Token)       │◄── Token Valid ────────│
   │                        │── Forward to Catalog──►│
   │◄── Event List ─────────│                        │
```

### 5.2. Role-Based Access Control (RBAC)

| Endpoint Pattern | ADMIN | ORGANIZER | CUSTOMER | PUBLIC |
|-----------------|-------|-----------|----------|--------|
| `POST /auth/**` | - | - | - | ✅ |
| `GET /events/**` | ✅ | ✅ | ✅ | ✅ |
| `POST /events` | ✅ | ✅ | ❌ | ❌ |
| `POST /bookings` | ❌ | ❌ | ✅ | ❌ |
| `POST /admin/**` | ✅ | ❌ | ❌ | ❌ |
| `GET /organizer/reports` | ✅ | ✅ | ❌ | ❌ |

---

## 6. Deployment Architecture

### 6.1. Docker Compose (Development)

```
docker-compose.yml
├── PostgreSQL (port 5432)
├── MongoDB (port 27017)
├── Redis (port 6379)
├── Zookeeper (port 2181)
├── Kafka Broker (port 9092)
├── API Gateway (port 8080)
├── Auth Service (port 8081)
├── User Service (port 8082)
├── Catalog Service (port 8083)
├── Booking Service (port 8084)
├── Payment Service (port 8085)
└── Notification Service (port 8086)
```

### 6.2. Scaling Strategy (Production)

| Service | Scaling | Lý do |
|---------|---------|-------|
| Booking Service | Horizontal (3-5 instances) | Chịu tải cao nhất khi mở bán |
| Catalog Service | Horizontal (2-3 instances) + Redis Cache | Read-heavy service |
| Payment Service | Horizontal (2-3 instances) | Đảm bảo throughput thanh toán |
| Auth Service | Horizontal (2 instances) | Stateless, JWT tự xác thực |
| Notification Service | Horizontal (2 instances) | Kafka consumer group tự balance |

---

> 📄 Xem thêm: [Database Schema](database-schema.md) | [API Design](api-design.md) | [Technical Flows](technical-flows.md)
