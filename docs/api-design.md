# 🔌 API Design — TicketBooking

> Thiết kế RESTful API cho tất cả Microservices

---

## Quy ước chung

### Base URL
```
https://api.ticketbooking.vn/v1
```

### Response Format (Chuẩn MoMo)

**Success Response (`status: 0`):**
```json
{
  "status": 0,
  "message": "Thành công",
  "data": { ... },
  "responseTime": 1721720663942
}
```

**Error Response (`status != 0`):**
```json
{
  "status": 400,
  "message": "Validation failed.",
  "data": null,
  "errors": {
    "email": "Email không đúng định dạng",
    "password": "Mật khẩu phải từ 8 ký tự"
  },
  "responseTime": 1721720663942
}
```

**Pagination Response:**
```json
{
  "success": true,
  "data": [ ... ],
  "pagination": {
    "page": 1,
    "size": 20,
    "totalElements": 150,
    "totalPages": 8
  }
}
```

### HTTP Status Codes

| Code | Ý nghĩa |
|------|----------|
| `200` | Thành công |
| `201` | Tạo mới thành công |
| `400` | Request không hợp lệ |
| `401` | Chưa xác thực (thiếu/sai JWT) |
| `403` | Không có quyền truy cập |
| `404` | Không tìm thấy resource |
| `409` | Xung đột (vd: email đã tồn tại) |
| `422` | Dữ liệu không hợp lệ (validation error) |
| `429` | Rate limit exceeded |
| `500` | Lỗi server |

### Authentication Header
```
Authorization: Bearer <jwt_token>
```

---

## 1. Auth Service API

### 1.1. Đăng ký tài khoản

```http
POST /auth/register
```

**Request Body:**
```json
{
  "email": "customer@email.com",
  "password": "SecureP@ss123",
  "fullName": "Nguyễn Văn A"
}
```

**Response (201):**
```json
{
  "success": true,
  "data": {
    "id": "uuid",
    "email": "customer@email.com",
    "role": "CUSTOMER"
  }
}
```

### 1.2. Đăng nhập

```http
POST /auth/login
```

**Request Body:**
```json
{
  "email": "customer@email.com",
  "password": "SecureP@ss123"
}
```

**Response (200):**
```json
{
  "success": true,
  "data": {
    "accessToken": "eyJhbGciOiJIUzI1NiIs...",
    "refreshToken": "eyJhbGciOiJIUzI1NiIs...",
    "expiresIn": 3600,
    "tokenType": "Bearer",
    "user": {
      "id": "uuid",
      "email": "customer@email.com",
      "role": "CUSTOMER"
    }
  }
}
```

### 1.3. Refresh Token

```http
POST /auth/refresh
```

**Request Body:**
```json
{
  "refreshToken": "eyJhbGciOiJIUzI1NiIs..."
}
```

### 1.4. Đổi mật khẩu 🔒

```http
PUT /auth/change-password
```

**Request Body:**
```json
{
  "currentPassword": "OldP@ss123",
  "newPassword": "NewP@ss456"
}
```

---

## 2. User Service API

### 2.1. Xem profile 🔒

```http
GET /users/me
```

**Response (200):**
```json
{
  "success": true,
  "data": {
    "id": "uuid",
    "accountId": "uuid",
    "fullName": "Nguyễn Văn A",
    "phoneNumber": "0901234567",
    "avatarUrl": "https://storage.ticketbooking.vn/avatars/uuid.jpg",
    "email": "customer@email.com",
    "role": "CUSTOMER"
  }
}
```

### 2.2. Cập nhật profile 🔒

```http
PUT /users/me
```

**Request Body:**
```json
{
  "fullName": "Nguyễn Văn B",
  "phoneNumber": "0901234567"
}
```

---

## 3. Catalog Service API

### 3.1. Danh sách danh mục

```http
GET /categories
```

**Response (200):**
```json
{
  "success": true,
  "data": [
    { "id": "uuid", "name": "Âm nhạc", "slug": "am-nhac" },
    { "id": "uuid", "name": "Thể thao", "slug": "the-thao" },
    { "id": "uuid", "name": "Sân khấu", "slug": "san-khau" }
  ]
}
```

### 3.2. Danh sách sự kiện (có lọc & phân trang)

```http
GET /events?category={slug}&status=PUBLISHED&startFrom={date}&location={city}&page=1&size=20&sort=startTime,asc
```

**Query Parameters:**

| Param | Kiểu | Mô tả |
|-------|------|--------|
| `category` | string | Lọc theo slug danh mục |
| `status` | string | Lọc theo trạng thái (PUBLISHED) |
| `startFrom` | date | Sự kiện bắt đầu từ ngày |
| `startTo` | date | Sự kiện bắt đầu đến ngày |
| `location` | string | Lọc theo địa điểm |
| `keyword` | string | Tìm kiếm theo tên sự kiện |
| `page` | int | Trang (default: 1) |
| `size` | int | Số item/trang (default: 20, max: 50) |
| `sort` | string | Sắp xếp (startTime,asc / startTime,desc) |

**Response (200):**
```json
{
  "success": true,
  "data": [
    {
      "id": "uuid",
      "title": "Sơn Tùng MTP Live Concert 2024",
      "category": { "id": "uuid", "name": "Âm nhạc" },
      "location": "Sân vận động Mỹ Đình, Hà Nội",
      "bannerUrl": "https://...",
      "startTime": "2024-06-15T19:00:00Z",
      "endTime": "2024-06-15T22:00:00Z",
      "status": "PUBLISHED",
      "ticketClasses": [
        {
          "id": "uuid",
          "name": "VVIP",
          "price": 3000000,
          "availableQuantity": 50
        },
        {
          "id": "uuid",
          "name": "VIP",
          "price": 1500000,
          "availableQuantity": 200
        },
        {
          "id": "uuid",
          "name": "GA",
          "price": 500000,
          "availableQuantity": 1000
        }
      ]
    }
  ],
  "pagination": { "page": 1, "size": 20, "totalElements": 45, "totalPages": 3 }
}
```

### 3.3. Chi tiết sự kiện

```http
GET /events/{eventId}
```

### 3.4. Tạo sự kiện 🔒 (ORGANIZER)

```http
POST /events
```

**Request Body:**
```json
{
  "categoryId": "uuid",
  "title": "Sơn Tùng MTP Live Concert 2024",
  "description": "Đêm nhạc hoành tráng nhất năm...",
  "location": "Sân vận động Mỹ Đình, Hà Nội",
  "venueName": "Sân vận động Quốc gia Mỹ Đình",
  "bannerUrl": "https://...",
  "startTime": "2024-06-15T19:00:00+07:00",
  "endTime": "2024-06-15T22:00:00+07:00",
  "saleStartTime": "2024-05-01T09:00:00+07:00",
  "saleEndTime": "2024-06-15T18:00:00+07:00",
  "ticketClasses": [
    {
      "name": "VVIP",
      "description": "Khu vực sát sân khấu, quà tặng đặc biệt",
      "price": 3000000,
      "totalQuantity": 100
    },
    {
      "name": "VIP",
      "description": "Khu vực gần sân khấu",
      "price": 1500000,
      "totalQuantity": 500
    },
    {
      "name": "GA (General Admission)",
      "description": "Khu vực khán đài",
      "price": 500000,
      "totalQuantity": 2000
    }
  ]
}
```

### 3.5. Cập nhật sự kiện 🔒 (ORGANIZER)

```http
PUT /events/{eventId}
```

### 3.6. Publish sự kiện 🔒 (ORGANIZER)

```http
PATCH /events/{eventId}/publish
```

---

## 4. Booking Service API

### 4.1. Tạo đơn đặt vé (Seat Hold) 🔒 (CUSTOMER)

```http
POST /bookings
```

**Request Body:**
```json
{
  "eventId": "uuid",
  "items": [
    {
      "ticketClassId": "uuid",
      "quantity": 2
    }
  ]
}
```

**Response (201):**
```json
{
  "success": true,
  "data": {
    "id": "booking-uuid",
    "eventId": "uuid",
    "status": "PENDING_PAYMENT",
    "totalAmount": 3000000,
    "items": [
      {
        "ticketClassId": "uuid",
        "ticketClassName": "VIP",
        "quantity": 2,
        "unitPrice": 1500000,
        "subtotal": 3000000
      }
    ],
    "expiredAt": "2024-01-01T12:10:00Z",
    "paymentUrl": "https://payment.ticketbooking.vn/checkout/booking-uuid",
    "createdAt": "2024-01-01T12:00:00Z"
  }
}
```

**Error Cases:**
| Code | Error Code | Mô tả |
|------|-----------|--------|
| 400 | `INVALID_QUANTITY` | Số lượng không hợp lệ |
| 404 | `EVENT_NOT_FOUND` | Sự kiện không tồn tại |
| 409 | `TICKET_CLASS_SOLD_OUT` | Hạng vé đã hết |
| 409 | `INSUFFICIENT_TICKETS` | Không đủ vé (yêu cầu 5, còn 3) |
| 409 | `EVENT_SALE_CLOSED` | Sự kiện chưa mở bán hoặc đã đóng bán |
| 429 | `BOOKING_RATE_LIMITED` | Đặt vé quá nhanh, vui lòng thử lại |

### 4.2. Xem chi tiết đơn hàng 🔒

```http
GET /bookings/{bookingId}
```

### 4.3. Danh sách đơn hàng của tôi 🔒 (CUSTOMER)

```http
GET /bookings/me?status={status}&page=1&size=10
```

### 4.4. Hủy đơn hàng 🔒 (CUSTOMER)

```http
DELETE /bookings/{bookingId}
```

> Chỉ được hủy khi status = `PENDING_PAYMENT`

### 4.5. Check-in vé bằng QR 🔒 (ORGANIZER)

```http
POST /bookings/check-in
```

**Request Body:**
```json
{
  "qrCodeData": "unique-qr-code-uuid"
}
```

**Response (200):**
```json
{
  "success": true,
  "data": {
    "ticketId": "uuid",
    "ticketClass": "VIP",
    "eventTitle": "Sơn Tùng MTP Live Concert 2024",
    "customerName": "Nguyễn Văn A",
    "status": "CHECKED_IN",
    "checkedInAt": "2024-06-15T18:30:00Z"
  }
}
```

**Error Cases:**
| Code | Error Code | Mô tả |
|------|-----------|--------|
| 404 | `TICKET_NOT_FOUND` | Mã QR không hợp lệ |
| 409 | `TICKET_ALREADY_CHECKED_IN` | Vé đã được check-in trước đó |
| 409 | `TICKET_NOT_ISSUED` | Vé chưa được phát hành (chưa thanh toán) |

---

## 5. Payment Service API

### 5.1. Khởi tạo thanh toán 🔒 (CUSTOMER)

```http
POST /payments/initiate
```

**Request Body:**
```json
{
  "bookingId": "booking-uuid",
  "paymentMethod": "VNPAY",
  "returnUrl": "https://ticketbooking.vn/payment/result"
}
```

**Response (200):**
```json
{
  "success": true,
  "data": {
    "transactionId": "txn-uuid",
    "paymentUrl": "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html?...",
    "expiredAt": "2024-01-01T12:10:00Z"
  }
}
```

### 5.2. VNPay Callback (IPN — Instant Payment Notification)

```http
GET /payments/vnpay/callback?vnp_TxnRef=...&vnp_ResponseCode=00&vnp_Amount=...
```

> Endpoint này được VNPay gọi tự động sau khi khách thanh toán. Service sẽ xác minh checksum, lưu giao dịch và bắn Kafka event.

### 5.3. Xem lịch sử giao dịch 🔒

```http
GET /payments/history?page=1&size=10
```

---

## 6. Admin API

### 6.1. Danh sách Organizer chờ duyệt 🔒 (ADMIN)

```http
GET /admin/organizers?status=PENDING&page=1&size=20
```

### 6.2. Duyệt / Khóa tài khoản 🔒 (ADMIN)

```http
PATCH /admin/accounts/{accountId}/status
```

**Request Body:**
```json
{
  "status": "ACTIVE",
  "reason": "Đã xác minh giấy phép tổ chức sự kiện"
}
```

### 6.3. Quản lý danh mục 🔒 (ADMIN)

```http
POST   /admin/categories          # Tạo danh mục
PUT    /admin/categories/{id}     # Sửa danh mục
DELETE /admin/categories/{id}     # Xóa danh mục
```

---

## 7. Organizer Report API

### 7.1. Báo cáo doanh thu 🔒 (ORGANIZER)

```http
GET /organizer/events/{eventId}/report
```

**Response (200):**
```json
{
  "success": true,
  "data": {
    "eventId": "uuid",
    "eventTitle": "Sơn Tùng MTP Live Concert 2024",
    "summary": {
      "totalRevenue": 1250000000,
      "totalTicketsSold": 1850,
      "totalTicketsCheckedIn": 1200,
      "checkInRate": 64.86
    },
    "byTicketClass": [
      {
        "ticketClassId": "uuid",
        "name": "VVIP",
        "price": 3000000,
        "totalQuantity": 100,
        "sold": 100,
        "checkedIn": 85,
        "revenue": 300000000
      },
      {
        "ticketClassId": "uuid",
        "name": "VIP",
        "price": 1500000,
        "totalQuantity": 500,
        "sold": 450,
        "checkedIn": 380,
        "revenue": 675000000
      },
      {
        "ticketClassId": "uuid",
        "name": "GA",
        "price": 500000,
        "totalQuantity": 2000,
        "sold": 1300,
        "checkedIn": 735,
        "revenue": 650000000
      }
    ]
  }
}
```

---

## 8. Queue Service API (Virtual Waiting Room)

### 8.1. Kiểm tra trạng thái hàng chờ sự kiện

```http
GET /queue/events/{eventId}/status
```

**Response (200):**
```json
{
  "success": true,
  "data": {
    "eventId": "uuid",
    "queueEnabled": true,
    "totalWaiting": 1540,
    "estimatedWaitTimeSeconds": 450
  }
}
```

### 8.2. WebSocket Connection (STOMP Protocol)

* **Endpoint:** `wss://api.ticketbooking.vn/ws/queue/{eventId}`
* **Subscription Topic:** `/user/queue/position`
* **Heartbeat Client -> Server:** Gửi frame `HEARTBEAT` mỗi 10 giây.
* **Server Message: Cập nhật vị trí:**
```json
{
  "type": "POSITION_UPDATE",
  "position": 145,
  "totalWaiting": 1540,
  "estimatedWaitSeconds": 90
}
```
* **Server Message: Được cấp phép vào mua (Admitted):**
```json
{
  "type": "ADMITTED",
  "accessToken": "queue-jwt-or-uuid-token",
  "expiresInSeconds": 600
}
```

---

## 9. Recommend Service API (AI Gợi Ý Sự Kiện)

### 9.1. Gợi ý sự kiện cá nhân hóa 🔒 (CUSTOMER)

```http
GET /recommendations/events/for-you?limit=10
```

**Response (200):**
```json
{
  "success": true,
  "data": [
    {
      "eventId": "uuid",
      "title": "Hà Anh Tuấn Live Concert - Chân Trời Rực Rỡ",
      "categoryName": "Âm nhạc",
      "matchScore": 0.94,
      "reasons": ["Phù hợp với sở thích Acoustic/Pop", "Dựa trên lịch sử xem sự kiện tương tự"]
    }
  ]
}
```

### 9.2. Sự kiện tương tự (Similar Events)

```http
GET /recommendations/events/{eventId}/similar?limit=5
```

---

> 🔒 = Yêu cầu JWT Authentication
>
> 📄 Xem thêm: [System Design](system-design.md) | [Database Schema](database-schema.md) | [Technical Flows](technical-flows.md) | [Virtual Waiting Room](virtual-waiting-room.md)
