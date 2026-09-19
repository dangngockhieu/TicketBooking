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

### 1.1. Đăng ký tài khoản (chỉ dành cho **Customer**)

> ⚠️ Endpoint này **chỉ tạo tài khoản `CUSTOMER`**.

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
    "role": "CUSTOMER",
    "status": "PENDING"
  }
}
```

> Tài khoản mới tạo có `status = PENDING` (chờ xác thực email), **chưa đăng nhập được** cho tới khi xác thực OTP thành công ở mục 1.1b. Xem chi tiết luồng ở [`docs/technical-flows.md#luồng-xác-thực-email-otp`](technical-flows.md).
> ⏳ Việc gửi email OTP qua Kafka (`account.registered` → `notification-service`) **chưa triển khai**

### 1.1b. Xác thực email bằng OTP 🆕

```http
POST /auth/verify-email
```

**Request Body:**
```json
{
  "email": "customer@email.com",
  "otp": "482913"
}
```

**Response (200):** trả về như đăng nhập thành công (`AuthResponse` — tự động đăng nhập sau khi xác thực) — `status` tài khoản chuyển `PENDING → ACTIVE`.

**Error Cases:**
| Code | Error Code | Mô tả |
|------|-----------|--------|
| 400 | `OTP_INVALID` | Mã OTP sai |
| 410 | `OTP_EXPIRED` | Mã OTP đã hết hạn (TTL 5 phút) |
| 404 | `ACCOUNT_NOT_FOUND` | Không tìm thấy tài khoản ứng với email |
| 409 | `ACCOUNT_ALREADY_VERIFIED` | Tài khoản đã ở trạng thái `ACTIVE` |

### 1.1c. Gửi lại mã OTP 🆕

```http
POST /auth/resend-verification
```

**Request Body:**
```json
{ "email": "customer@email.com" }
```

> Sinh OTP mới, ghi đè key Redis cũ, giới hạn tần suất (vd. tối thiểu 60 giây/lần) để chống spam email.

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

> ⚠️ Lỗi `403 ACCOUNT_LOCKED (2003)` khi login hiện dùng chung cho **2 trường hợp khác nhau** (tài khoản `LOCKED` do Admin khóa, và Customer mới đăng ký đang `PENDING` chờ xác thực email OTP).
>
> Organizer **không rơi vào case `PENDING` chờ duyệt** nữa vì không còn tự đăng ký (xem mục 1.1, 1.5) — tài khoản Organizer do Admin tạo đã ở thẳng `status = ACTIVE`.

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

### 1.5. Admin tạo tài khoản Organizer 🔒 (ADMIN) 🆕

> Theo `system-design.md` §1.2 và §2.3 (UC-A1): Admin **thẩm định giấy phép tổ chức sự kiện ngoài hệ thống trước** (qua email/hồ sơ giấy), sau đó mới tạo tài khoản trong hệ thống — không có bước "duyệt đơn online" vì không có đơn nào được nộp online cả. Organizer **không tự đăng ký**.

```http
POST /admin/organizers
```

**Request Body:**
```json
{
  "email": "bantochuc@abc.vn",
  "fullName": "Công ty TNHH Sự kiện ABC"
}
```

**Response (201):**
```json
{
  "success": true,
  "data": {
    "id": "uuid",
    "email": "bantochuc@abc.vn",
    "role": "ORGANIZER",
    "status": "ACTIVE"
  }
}
```

> - Tài khoản tạo ra **`status = ACTIVE` ngay** (không qua `PENDING`/OTP) — vì Admin đã xác minh danh tính ngoài luồng, không cần xác thực email lại.
> - Mật khẩu: hệ thống tự sinh mật khẩu tạm ngẫu nhiên (không nhận `password` từ Admin để tránh Admin biết mật khẩu thật của Organizer).
> - ⏳ Gửi mật khẩu tạm qua email cho Organizer: phụ thuộc Kafka/`notification-service` (chưa triển khai — xem mục 1.1). Tạm thời ở môi trường dev, response trả kèm `tempPassword` khi `spring.profiles.active=dev`, hoặc Admin lấy log server.
> - Lần đăng nhập đầu tiên bằng mật khẩu tạm: response `AuthResponse` có thêm cờ `"requirePasswordChange": true` → FE bắt buộc chuyển tới `PUT /auth/change-password` trước khi cho vào các trang khác.

**Error Cases:**
| Code | Error Code | Mô tả |
|------|-----------|--------|
| 409 | `EMAIL_ALREADY_EXISTS` | Email đã tồn tại tài khoản khác |
| 400 | `INVALID_INPUT_DATA` | Thiếu email/fullName |

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

## 5. Payment Service API

> **Cổng thanh toán:** [MoMo Payment Gateway](https://developers.momo.vn) — tích hợp qua endpoint `/v2/gateway/api/create`.
> Khách chọn nguồn tiền (ví MoMo, thẻ ATM/Napas, thẻ quốc tế Visa/MC) ngay trên trang thanh toán do MoMo hiển thị — **backend chỉ tích hợp một API duy nhất**, không cần phân biệt từng phương thức.

---

### 5.1. Khởi tạo thanh toán 🔒 (CUSTOMER)

```http
POST /payments/initiate
```

**Điều kiện tiên quyết:**
* Booking tồn tại, thuộc về Customer đang đăng nhập.
* Booking có `status = PENDING_PAYMENT` và chưa hết hạn (`expired_at > now()`).
* Chưa có transaction `PENDING` hoặc `SUCCESS` nào cho booking này (idempotency).

**Request Body:**
```json
{
  "bookingId": "booking-uuid",
  "returnUrl": "https://ticketbooking.vn/payment/result"
}
```

**Xử lý phía backend (`Payment Service`):**
1. Validate booking hợp lệ (gọi nội bộ sang Booking Service).
2. Tạo `Transaction` record với `status = PENDING`.
3. Tạo `requestId` = UUID mới (dùng cho idempotency với MoMo).
4. Gọi MoMo API:
```http
POST https://payment.momo.vn/v2/gateway/api/create
Content-Type: application/json

{
  "partnerCode": "${MOMO_PARTNER_CODE}",
  "requestId":   "<uuid>",
  "amount":      3000000,
  "orderId":     "<bookingId>",
  "orderInfo":   "Thanh toán vé sự kiện - <bookingId>",
  "redirectUrl": "<returnUrl>",
  "ipnUrl":      "https://api.ticketbooking.vn/v1/payments/momo/ipn",
  "requestType": "captureWallet",
  "extraData":   "",
  "lang":        "vi",
  "signature":   "<HMAC-SHA256>"
}
```
5. Chữ ký `signature` được tạo bằng **HMAC-SHA256** trên chuỗi raw (đúng chuẩn MoMo AIO — bắt buộc có `accessKey`, thiếu sẽ bị MoMo từ chối với lỗi checksum:
```
rawSignature = "accessKey=...&amount=3000000&extraData=&ipnUrl=...&orderId=...&orderInfo=...&partnerCode=MOMO&redirectUrl=...&requestId=...&requestType=captureWallet"
signature = HMAC_SHA256(rawSignature, secretKey)
```
   `accessKey` cũng là một field bắt buộc trong JSON body gửi lên MoMo (không chỉ dùng để ký).
6. Lưu `payUrl` từ MoMo vào transaction, trả về client.

**Response (200):**
```json
{
  "success": true,
  "data": {
    "transactionId": "txn-uuid",
    "bookingId":     "booking-uuid",
    "amount":        3000000,
    "paymentUrl":    "https://payment.momo.vn/v2/gateway/pay?t=...",
    "expiredAt":     "2024-06-15T12:10:00Z"
  }
}
```

**Error Cases:**
| HTTP | Error Code | Mô tả |
|------|-----------|--------|
| 404 | `BOOKING_NOT_FOUND` | Booking không tồn tại |
| 403 | `BOOKING_NOT_OWNED` | Booking không thuộc về Customer này |
| 409 | `BOOKING_EXPIRED` | Booking đã quá 10 phút, không thể thanh toán |
| 409 | `BOOKING_ALREADY_PAID` | Booking đã thanh toán trước đó |
| 502 | `MOMO_GATEWAY_ERROR` | Lỗi kết nối cổng MoMo |

---

### 5.2. MoMo IPN Callback — Instant Payment Notification

> **Đây là endpoint server-to-server do MoMo tự động gọi** khi khách thanh toán xong — không phải redirect trình duyệt.
> URL này phải được đăng ký trong MoMo Developer Portal: `https://api.ticketbooking.vn/v1/payments/momo/ipn`
> **Bắt buộc trả về `HTTP 204` trong vòng 5 giây**, nếu không MoMo sẽ retry tối đa 5 lần.

```http
POST /payments/momo/ipn
```

**Request Body (MoMo gửi đến `ipnUrl`):**
```json
{
  "partnerCode": "MOMO",
  "orderId":     "booking-uuid",
  "requestId":   "req-uuid",
  "amount":      3000000,
  "orderInfo":   "Thanh toán vé sự kiện - booking-uuid",
  "orderType":   "momo_wallet",
  "transId":     4123456789,
  "resultCode":  0,
  "message":     "Successful.",
  "payType":     "qr",
  "responseTime": 1718448000000,
  "extraData":   "",
  "signature":   "..."
}
```

**Xử lý phía backend:**
1. **Verify chữ ký HMAC-SHA256** trên raw string (có `accessKey`, giống §5.1):
   ```
   rawSignature = "accessKey=...&amount=3000000&extraData=&message=Successful.&orderId=...&orderInfo=...&orderType=momo_wallet&partnerCode=MOMO&payType=qr&requestId=...&responseTime=...&resultCode=0&transId=4123456789"
   ```
   Nếu `signature` không khớp → **trả 400, dừng xử lý** (ngăn giả mạo callback).
2. Kiểm tra `amount` khớp với booking (chống tấn công thay đổi số tiền).
3. **Idempotency check:** nếu `transId` đã tồn tại trong DB → bỏ qua, trả 204.
4. Nếu `resultCode = 0` (thành công):
   * Cập nhật `Transaction.status = SUCCESS`, lưu `transId`, `paidAt`.
   * Publish Kafka event `payment.success`.
5. Nếu `resultCode ≠ 0` (thất bại):
   * Cập nhật `Transaction.status = FAILED`.
   * Publish Kafka event `payment.failed`.
6. Trả về `HTTP 204` cho MoMo.

**resultCode quan trọng của MoMo:**
| resultCode | Ý nghĩa |
|-----------|---------|
| `0` | Thành công |
| `1000` | Đang chờ xác nhận (pending) |
| `1001` | Giao dịch thất bại (không đủ số dư) |
| `1006` | Người dùng huỷ thanh toán |
| `1005` | Token/URL hết hạn |
| `9000` | Giao dịch bị từ chối bởi ngân hàng |

---

### 5.3. MoMo Return URL (Redirect trình duyệt)

> Sau khi khách thanh toán xong, **MoMo redirect trình duyệt** về `returnUrl` (frontend URL) với các query params:

```
GET https://ticketbooking.vn/payment/result
    ?partnerCode=MOMO
    &orderId=booking-uuid
    &requestId=req-uuid
    &amount=3000000
    &orderInfo=...
    &orderType=momo_wallet
    &transId=4123456789
    &resultCode=0
    &message=Successful.
    &payType=qr
    &responseTime=1718448000000
    &extraData=
    &signature=...
```

> ⚠️ **Frontend không được tin tuyệt đối** vào redirect này (có thể bị giả mạo). Sau khi nhận redirect, frontend gọi API `GET /bookings/{id}` để lấy `status` thật từ DB — chỉ hiển thị kết quả theo trạng thái booking thực tế, không theo query params của redirect.

---

### 5.4. Hoàn tiền (Refund) — Saga Compensation

> Được kích hoạt tự động khi Booking Service publish event `booking.refund-requested`.
> **Không có API public** — chỉ là Kafka consumer nội bộ.

**Luồng hoàn tiền:**
```
booking.refund-requested (Kafka)
        ↓
Payment Service consume
        ↓
POST https://payment.momo.vn/v2/gateway/api/refund
{
  "partnerCode": "MOMO",
  "accessKey":   "...",
  "orderId":     "<bookingId>-refund-<timestamp>",
  "requestId":   "<uuid>",
  "amount":      3000000,
  "transId":     4123456789,   ← transId MoMo gốc
  "lang":        "vi",
  "description": "Hoàn tiền đơn hàng booking-uuid do lỗi hệ thống",
  "signature":   "<HMAC-SHA256>"
}
        ↓
resultCode = 0 → Transaction.status = REFUNDED
        ↓
Publish Kafka event: payment.refunded
        ↓
Booking Service: booking = REFUNDED, giải phóng vé
```

`signature` ký trên chuỗi raw (cùng chuẩn `accessKey` như §5.1/§5.2):
```
rawSignature = "accessKey=...&amount=3000000&description=...&orderId=...&partnerCode=MOMO&requestId=...&transId=4123456789"
```

---

### 5.5. Xem lịch sử giao dịch 🔒 (CUSTOMER)

```http
GET /payments/history?page=1&size=10
```

**Response (200):**
```json
{
  "success": true,
  "data": [
    {
      "transactionId":  "txn-uuid",
      "bookingId":      "booking-uuid",
      "amount":         3000000,
      "status":         "SUCCESS",
      "payType":        "qr",
      "momoTransId":    4123456789,
      "paidAt":         "2024-06-15T12:05:32Z",
      "eventTitle":     "Sơn Tùng MTP Live Concert"
    }
  ],
  "pagination": { "page": 1, "size": 10, "totalElements": 3, "totalPages": 1 }
}
```

---

### 5.6. Chi tiết giao dịch 🔒 (CUSTOMER)

```http
GET /payments/transactions/{transactionId}
```

**Response (200):**
```json
{
  "success": true,
  "data": {
    "transactionId":  "txn-uuid",
    "bookingId":      "booking-uuid",
    "amount":         3000000,
    "status":         "SUCCESS",
    "paymentMethod":  "MOMO",
    "payType":        "qr",
    "momoTransId":    4123456789,
    "gatewayResponse": { "resultCode": 0, "message": "Successful." },
    "paidAt":         "2024-06-15T12:05:32Z",
    "createdAt":      "2024-06-15T12:00:10Z"
  }
}
```

---

## 6. Admin API

### 6.1. Danh sách tài khoản Organizer 🔒 (ADMIN)

> ⚠️ Đổi từ "chờ duyệt" thành liệt kê chung + lọc theo `status`, vì Organizer không còn ở trạng thái `PENDING` chờ duyệt (xem mục 1.5 — Admin tạo tài khoản là `ACTIVE` ngay). Endpoint này dùng để Admin xem lại/khóa các Organizer đã tạo, không phải hàng đợi phê duyệt.

```http
GET /admin/organizers?status=ACTIVE&page=1&size=20
```

### 6.1b. Tạo tài khoản Organizer 🔒 (ADMIN) 🆕

Xem chi tiết đầy đủ ở mục **1.5**.

```http
POST /admin/organizers
```

### 6.2. Khóa / Mở khóa tài khoản 🔒 (ADMIN)

> Đổi tên từ "Duyệt / Khóa" — vì không còn bước "duyệt" (chỉ còn khóa/mở khóa tài khoản đã `ACTIVE`).

```http
PATCH /admin/accounts/{accountId}/status
```

**Request Body:**
```json
{
  "status": "LOCKED",
  "reason": "Vi phạm điều khoản sử dụng"
}
```

### 6.3. Quản lý danh mục 🔒 (ADMIN)

```http
POST   /admin/categories          # Tạo danh mục
PUT    /admin/categories/{id}     # Sửa danh mục
DELETE /admin/categories/{id}     # Xóa danh mục
```

### 6.4. Sự kiện & phí nền tảng 🔒 (ADMIN) 🆕

> Admin xem mọi sự kiện của mọi Organizer (mọi trạng thái) và sửa phí nền tảng riêng cho từng sự kiện. Xem công thức phí ở mục 7.1.

```http
GET /admin/events?status={status}&keyword={keyword}&page=1&size=20
```

```http
PATCH /admin/events/{eventId}/commission
```

**Request Body:**
```json
{
  "commissionRate": 0.05,
  "flatFeePerTicket": 3000
}
```

> Mặc định khi tạo sự kiện: `commissionRate = 0.05` (5%), `flatFeePerTicket = 3000` (VND). Chỉ Admin sửa được — Organizer chỉ xem.

### 6.5. Duyệt yêu cầu rút tiền (Payout) 🔒 (ADMIN) 🆕

> Xem chi tiết state machine và tích hợp MoMo Disbursement ở mục 7.4.

```http
GET /admin/payouts?status={status}&page=1&size=20
```

```http
PATCH /admin/payouts/{requestId}/status
```

**Request Body:**
```json
{
  "status": "APPROVED",
  "reason": null
}
```

> `status` ∈ `APPROVED | REJECTED | PAID | HOLD | PENDING`. Bắt buộc có `reason` khi `REJECTED` hoặc `HOLD`.

---

## 7. Organizer Report & Payout API

### 7.1. Báo cáo doanh thu 🔒 (ORGANIZER)

```http
GET /organizer/events/{eventId}/report
```

> **Phí nền tảng (hoa hồng):** mỗi vé bán được bị thu phí `phí/vé = giá vé × commissionRate + flatFeePerTicket` (vé giá 0đ luôn được miễn phí hoàn toàn, không tính `flatFeePerTicket`). `commissionRate`/`flatFeePerTicket` mặc định 5% + 3.000đ/vé, Admin có thể sửa riêng theo từng sự kiện (mục 6.4). `netRevenue = totalRevenue - totalPlatformFee` là số tiền thực sự được cộng vào ví Organizer.

**Response (200):**
```json
{
  "success": true,
  "data": {
    "eventId": "uuid",
    "eventTitle": "Sơn Tùng MTP Live Concert 2024",
    "summary": {
      "totalRevenue": 1250000000,
      "totalPlatformFee": 68500000,
      "netRevenue": 1181500000,
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

### 7.2. Xem ví 🔒 (ORGANIZER) 🆕

```http
GET /organizer/wallet
```

**Response (200):**
```json
{
  "success": true,
  "data": {
    "availableBalance": 125000000,
    "pendingPayout": 8000000,
    "totalWithdrawn": 200000000,
    "updatedAt": "2026-09-24T10:00:00+07:00"
  }
}
```

> `availableBalance` = Σ `netRevenue` của mọi sự kiện đã `COMPLETED`, trừ đi các payout đang `PENDING`/`APPROVED`/`HOLD`/`PAID`.

### 7.3. Tài khoản ngân hàng nhận tiền 🔒 (ORGANIZER) 🆕

> **1 Organizer = đúng 1 tài khoản ngân hàng cố định** (ép ở tầng DB, xem `database-schema.md` bảng `organizer_bank_accounts`). Client **không** được gửi thông tin ngân hàng kèm mỗi request payout — phải thiết lập/xác minh trước qua API riêng này.

```http
GET /organizer/bank-account
```

**Response (200):**
```json
{
  "success": true,
  "data": {
    "bankName": "Vietcombank",
    "bankAccountNumber": "0071000123456",
    "bankAccountHolder": "CONG TY TNHH ABC",
    "verified": true,
    "verifiedAt": "2026-09-20T09:00:00+07:00"
  }
}
```

> `data: null` nếu Organizer chưa từng thiết lập tài khoản ngân hàng.

```http
PUT /organizer/bank-account
```

**Request Body:**
```json
{
  "bankName": "Vietcombank",
  "bankAccountNumber": "0071000123456",
  "bankAccountHolder": "CONG TY TNHH ABC"
}
```

> Tạo mới hoặc **thay thế** tài khoản hiện có (upsert theo `profile_id`, không có khái niệm "nhiều tài khoản"). Mỗi lần thay thế, `verified` reset về `false` — bắt buộc Admin xác minh lại trước khi dùng được cho payout (chống chiếm đoạt tài khoản rồi đổi ngân hàng nhận tiền ngay lập tức). Trong lúc `verified = false`, mọi request `POST /organizer/payouts` (§7.4) bị từ chối (409 `BANK_ACCOUNT_NOT_VERIFIED`).

```http
PATCH /admin/organizers/{organizerId}/bank-account/verify
```

> **ADMIN** xác minh thủ công (đối chiếu giấy phép kinh doanh/CCCD đã thẩm định lúc `POST /admin/organizers`) → `verified = true`, `verifiedAt = now()`.

### 7.4. Yêu cầu rút tiền 🔒 (ORGANIZER) 🆕

```http
POST /organizer/payouts
```

**Request Body:**
```json
{
  "amount": 50000000
}
```

> Tạo yêu cầu `status: PENDING`, `source: MANUAL`. Backend **tự tra cứu** tài khoản ngân hàng đã xác minh của Organizer (§7.3) rồi snapshot vào `payout_requests.bank_name/bank_account_number/bank_account_holder` — client không gửi, không chọn được tài khoản khác. Từ chối nếu: `amount > availableBalance` (409 `INSUFFICIENT_BALANCE`), `amount ≤ 0` (400), hoặc chưa có tài khoản ngân hàng đã xác minh (409 `BANK_ACCOUNT_NOT_VERIFIED`).

```http
GET /organizer/payouts?status={status}&page=1&size=20
```

### 7.5. Payout tự động & Chi trả qua MoMo Disbursement 🆕

> Theo mô hình Ticketbox/Eventbrite: tiền tự động về cho Organizer, không cần chủ động xin.

```
Job nền (Scheduled, chạy mỗi ngày):
  quét events có status=COMPLETED và endTime + 7 ngày ≤ now
    và chưa có payout AUTO ứng với event đó
  → tạo PayoutRequest { status: PENDING, source: AUTO, eventId, amount: netRevenue của event }
```

Khi Admin duyệt (`PATCH /admin/payouts/{id}/status` → `APPROVED` rồi → `PAID`), backend gọi **MoMo Business Disbursement API** để tự động chi tiền vào tài khoản ngân hàng/ví MoMo của Organizer, thay vì Admin tự chuyển khoản thủ công:

```http
POST https://payment.momo.vn/v2/gateway/api/disburse
```

- Request ký bằng `signature` (HMAC-SHA256) như các API MoMo khác, gồm `partnerCode`, `accessKey`, `requestId`, `orderId` (= `payoutRequestId`), `amount`, `receiver` (số tài khoản ngân hàng hoặc số điện thoại ví MoMo người nhận), `description`. Raw string (alphabetical, cùng chuẩn `accessKey` như §5.1/§5.2/§5.4):
  ```
  rawSignature = "accessKey=...&amount=50000000&description=...&orderId=<payoutRequestId>&partnerCode=MOMO&receiver=...&requestId=..."
  ```
- Kết quả trả về đồng bộ (`resultCode`) hoặc callback bất đồng bộ tùy loại giao dịch (chuyển khoản ngân hàng thường xử lý bất đồng bộ, có IPN riêng cho Disbursement khác với IPN thanh toán ở mục 5.2 — **chưa triển khai ở bản hiện tại**, tạm xử lý đồng bộ theo `resultCode`).
- Thành công → `PayoutRequest.status = PAID`, `processedAt = now()`.
- Thất bại → giữ nguyên `APPROVED`, ghi log lỗi để Admin xử lý lại hoặc chuyển khoản thủ công dự phòng.

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
