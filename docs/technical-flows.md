# ⚡ Technical Flows — TicketBooking

> Luồng xử lý kỹ thuật chi tiết cho các nghiệp vụ cốt lõi

---

## 0. Luồng Xác thực Email (OTP) — Đăng ký tài khoản Customer 🆕

> ⚠️ **Chỉ áp dụng cho `CUSTOMER` tự đăng ký qua `POST /auth/register`.** Organizer **không** đi qua luồng này — Admin tạo tài khoản Organizer trực tiếp (`ACTIVE` ngay, không OTP)
>
> Trạng thái: **Redis + REST đã chốt thiết kế**; **Kafka + gửi mail thật CHƯA triển khai** vì `notification-service` chưa tồn tại trong repo.

### 0.1. Vì sao đổi

Trước đây `AuthServiceImpl.register()` set `status = ACTIVE` ngay lập tức — không xác minh email có thật/thuộc về người đăng ký hay không. Thêm bước xác thực OTP để:
- Chặn spam đăng ký bằng email giả/email người khác.
- Đảm bảo địa chỉ nhận vé QR (gửi qua email — UC-S3) là email hợp lệ.

### 0.2. Luồng

```
Customer                    Auth Service                   Redis                Kafka / Notification (⏳ chưa làm)
   │                             │                           │                           │
   │── POST /auth/register ─────►│                           │                           │
   │                             │── INSERT accounts ────────┤ status = PENDING          │
   │                             │── sinh OTP 6 số ─────────►│                           │
   │                             │  SETEX email_verify:{id} 300 "482913"                 │
   │                             │── (dự kiến) publish "account.registered" ────────────►│
   │                             │     { accountId, email, otp }                         │
   │◄── 201 { id, email, role, status: PENDING } ────────────│                           │
   │                             │                           │                           │
   │  (nhận OTP qua email/console) │                         │                           │
   │── POST /auth/verify-email {email, otp} ────────────────►│                           │
   │                             │── GET email_verify:{id} ─►│                           │
   │                             │◄── "482913" ──────────────│                           │
   │                             │  so khớp OK                                           │
   │                             │── UPDATE accounts SET status = ACTIVE ────────────────│
   │                             │── DEL email_verify:{id} ─►│                           │
   │◄── 200 AuthResponse (tự động đăng nhập) ────────────────│                           │
```

### 0.3. Quy tắc

| Quy tắc | Giá trị |
|---|---|
| Độ dài OTP | 6 chữ số, sinh ngẫu nhiên (`SecureRandom`, không dùng `Math.random()`) |
| TTL OTP | 300 giây (5 phút) |
| Cooldown gửi lại | 60 giây/lần (key `email_verify_cooldown:{accountId}`, xem `database-schema.md`) |
| Số lần thử sai | Đề xuất giới hạn 5 lần/OTP, khóa tạm 5 phút nếu vượt (chống brute-force OTP 6 số — chưa cài đặt) |
| Đăng nhập khi `PENDING` | Bị chặn, trả `403 ACCOUNT_LOCKED (2003)` — xem ghi chú ở `api-design.md` §1 |
| Tài khoản không xác thực sau X ngày | Đề xuất job dọn dẹp định kỳ xóa account `PENDING` quá 7 ngày (chưa cài đặt) |

### 0.4. Việc cần làm để hoàn thiện

1. **auth-service**: thêm dependency `spring-boot-starter-data-redis`, `RedisTemplate<String,String>`, sinh/verify OTP, 2 endpoint mới (`/auth/verify-email`, `/auth/resend-verification`).
2. **auth-service**: sửa `AuthServiceImpl.register()` — bỏ `.status(AccountStatus.ACTIVE)`, dùng mặc định `PENDING` từ entity.
3. **Kafka producer** ở auth-service: publish event `account.registered` sau khi tạo account (⏳ **để sau** — chỉ làm khi bắt tay vào GĐ4/notification-service, tránh thêm dependency Kafka vào auth-service khi chưa có consumer nào tồn tại).
4. **notification-service** (chưa tồn tại): consumer nghe `account.registered`, gửi email OTP thật qua JavaMail, ghi `notification_logs` (MongoDB) — cùng đợt xây dựng với `tickets.generated` (UC-S3).
5. Cho tới khi bước 3–4 xong: môi trường dev có thể **log OTP ra console** hoặc **trả kèm trong response** (chỉ khi `spring.profiles.active=dev`) để test luồng verify mà không cần email thật.

#### 0.5. Luồng cấp tài khoản Organizer & Tự động gửi Gmail mật khẩu tạm 🆕

> Luồng này dành riêng cho bên tổ chức sự kiện (**Organizer**). Organizer **không tự đăng ký** trên website, không qua `PENDING` và không dùng OTP — tài khoản do Quản trị viên (Admin) khởi tạo trực tiếp sau khi đã thẩm định hồ sơ pháp lý. Hệ thống tự sinh mật khẩu tạm ngẫu nhiên và gửi thông báo kích hoạt tài khoản qua **Gmail SMTP**.

#### 0.5.1. Sơ đồ tuần tự chi tiết (End-to-End Sequence Diagram)

```
Organizer               Admin                 Auth Service            Kafka Broker          Notification Svc          Gmail SMTP Server        MongoDB
   │                      │                        │                       │                       │                          │                   │
   │── [1] Nộp giấy phép ─►│                        │                       │                       │                          │                   │
   │   tổ chức sự kiện    │                        │                       │                       │                          │                   │
   │                      │── [2] Thẩm định duyệt ─►│                       │                       │                          │                   │
   │                      │   POST /admin/         │                       │                       │                          │                   │
   │                      │   organizers           │                       │                       │                          │                   │
   │                      │   {email, fullName}    │                       │                       │                          │                   │
   │                      │                        │── [3] Sinh mật khẩu   │                       │                          │                   │
   │                      │                        │   tạm (SecureRandom)  │                       │                          │                   │
   │                      │                        │── [4] INSERT accounts │                       │                          │                   │
   │                      │                        │   role=ORGANIZER,     │                       │                          │                   │
   │                      │                        │   status=ACTIVE,      │                       │                          │                   │
   │                      │                        │   reqPasswordChange=T │                       │                          │                   │
   │                      │                        │                       │                       │                          │                   │
   │                      │                        │── [5] Publish event ─►│                       │                          │                   │
   │                      │                        │   "auth.organizer-    │                       │                          │                   │
   │                      │                        │    created"           │                       │                          │                   │
   │                      │                        │   {email,tempPassword}│                       │                          │                   │
   │                      │◄── 201 Created ────────┤                       │                       │                          │                   │
   │                      │   {userInfo,           │                       │                       │                          │                   │
   │                      │    tempPassword}       │                       │                       │                          │                   │
   │                      │                        │                       │── [6] Consume ───────►│                          │                   │
   │                      │                        │                       │   event               │                          │                   │
   │                      │                        │                       │                       │── [7] Render Thymeleaf   │                   │
   │                      │                        │                       │                       │   "organizer-welcome"    │                   │
   │                      │                        │                       │                       │   (HTML email đẹp mắt)   │                   │
   │                      │                        │                       │                       │                          │                   │
   │                      │                        │                       │                       │── [8] Gửi email qua ────►│                   │
   │                      │                        │                       │                       │   TLS cổng 587           │                   │
   │                      │                        │                       │                       │   (App Password 16 số)   │                   │
   │◄── [9] Nhận thư Gmail chào mừng kèm Mật khẩu tạm ─────────────────────────────────────────────│                          │                   │
   │    "Tài khoản Organizer TicketBooking của bạn đã sẵn sàng"                                    │                          │                   │
   │                      │                        │                       │                       │                          │                   │
   │                      │                        │                       │                       │── [10] Ghi nhật ký ─────────────────────────►│
   │                      │                        │                       │                       │    EmailLog (status:SENT,│                   │
   │                      │                        │                       │                       │    type:ORGANIZER_WELCOME)                   │
   │                      │                        │                       │                       │                          │                   │
   │── [11] Đăng nhập lần đầu với Mật khẩu tạm (POST /auth/login) ────────►│                       │                          │                   │
   │◄── 200 OK: AuthResponse { accessToken, requirePasswordChange: true } ─┤                       │                          │                   │
   │                                                                       │                       │                          │                   │
   │── [12] Bắt buộc đổi mật khẩu mới (PUT /auth/change-password) ────────►│                       │                          │                   │
   │◄── 200 OK: "Mật khẩu đã được cập nhật thành công" (requirePasswordChange: false) ─────────────┤                       │                          │                   │
```

#### 0.5.2. Các bước xử lý kỹ thuật chi tiết

1. **Thẩm định hồ sơ ngoại tuyến (Offline Verification):**
   * Doanh nghiệp hoặc Ban tổ chức gửi hồ sơ pháp lý (Giấy phép ĐKKD, giấy phép biểu diễn nghệ thuật) cho Admin phê duyệt thủ công ngoài hệ thống.
   * Admin đăng nhập hệ thống với quyền `ROLE_ADMIN` và gọi API `POST /api/admin/organizers`.

2. **Khởi tạo tài khoản & Sinh mật khẩu tạm (Auth Service):**
   * Hệ thống tự động sinh một mật khẩu tạm ngẫu nhiên an toàn bằng `TempPasswordGenerator` (sử dụng `SecureRandom`, bao gồm chữ hoa, chữ thường, số và ký tự đặc biệt).
   * Mật khẩu tạm được băm bằng `BCryptPasswordEncoder` trước khi lưu vào bảng `accounts` của PostgreSQL Auth DB (`postgres-auth:5433`).
   * Tài khoản được gán trạng thái `status = ACTIVE` ngay lập tức (không cần qua bước kích hoạt OTP) và thiết lập cờ `require_password_change = true`.
   * Admin nhận được response chứa `userInfo` và bản rõ `tempPassword` để dự phòng bàn giao trực tiếp nếu email gặp sự cố mạng.

3. **Bắn sự kiện Kafka (Choreography Event):**
   * Auth Service publish event `auth.organizer-created` (lớp `OrganizerWelcomeEvent`) vào Kafka topic:
     ```json
     {
       "eventId": "3c90a1b2-...",
       "timestamp": "2026-09-28T10:15:30Z",
       "eventType": "auth.organizer-created",
       "email": "organizer@company.com",
       "tempPassword": "P@ssw0rdRandom#2026"
     }
     ```

4. **Xử lý Consumer & Gửi Gmail (Notification Service):**
   * `OrganizerWelcomeEmailListener` lắng nghe topic `auth.organizer-created`.
   * Template engine (Thymeleaf) nạp file mẫu HTML responsive [`organizer-welcome.html`](file:///D:/Project/TicketBooking/services/notification/src/main/resources/templates/organizer-welcome.html) với các biến context:
     * `email`: Địa chỉ hộp thư nhận.
     * `tempPassword`: Mật khẩu tạm để hiển thị trong khung bảo mật.
     * `supportEmail`: Email hỗ trợ kỹ thuật (`support@ticketbooking.vn`).
     * `year`: Năm hiện tại (`2026`).
   * `EmailServiceImpl` kết nối tới máy chủ **Gmail SMTP (`smtp.gmail.com:587`)**, sử dụng giao thức bảo mật STARTTLS và xác thực bằng **Mật khẩu ứng dụng (Google App Password 16 ký tự)**.
   * Email gửi đi có tiêu đề: `Tài khoản Organizer TicketBooking của bạn đã sẵn sàng`.

5. **Lưu vết nhật ký (Audit Log - MongoDB):**
   * Ngay sau khi gửi thành công, `Notification Service` ghi một bản ghi vào collection `email_logs` của MongoDB:
     * `type`: `"ORGANIZER_WELCOME"`
     * `recipient`: `"organizer@company.com"`
     * `status`: `"SENT"` (hoặc `"FAILED"` kèm thông báo lỗi chi tiết nếu rớt mạng)
     * `sentAt`: Timestamp gửi.

6. **Đăng nhập lần đầu & Buộc đổi mật khẩu (First Login Guard):**
   * Organizer nhận email trong hộp thư Gmail, mở thư và sao chép mật khẩu tạm.
   * Thực hiện đăng nhập qua `POST /auth/login`.
   * Do `requirePasswordChange = true`, payload `AuthResponse` trả về cờ yêu cầu đổi mật khẩu. Frontend điều hướng bắt buộc người dùng tới trang `PUT /auth/change-password` để đặt mật khẩu riêng tư mới trước khi được phép truy cập bất kỳ tính năng quản trị sự kiện nào.

#### 0.5.3. Cấu hình Gmail SMTP trong hệ thống

Notification Service đọc cấu hình SMTP tập trung từ `config-server` (hoặc biến môi trường `.env`):

```properties
# Cấu hình Gmail SMTP trong notification-service.yaml / .env
SMTP_HOST=smtp.gmail.com
SMTP_PORT=587
SMTP_USERNAME=your_system_email@gmail.com
SMTP_PASSWORD=xxxx xxxx xxxx xxxx   # App Password 16 ký tự của Google (bật 2FA)
MAIL_FROM=no-reply@ticketbooking.vn
SUPPORT_EMAIL=support@ticketbooking.vn
```

---

## 1. Luồng Giữ chỗ & Chống Overbooking (Seat Hold Flow)

### 1.1. Mô tả

Đây là luồng quan trọng nhất của hệ thống. Khi hàng ngàn người cùng mua vé, hệ thống phải đảm bảo:
- ❌ **Không Overbooking**: Không bán vượt quá số lượng vé phát hành
- ⏰ **Giữ chỗ 10 phút**: Vé được "khóa" tạm thời chờ thanh toán
- 🔄 **Tự động nhả**: Nếu không thanh toán trong 10 phút, vé tự động trả về kho

### 1.2. Sequence Diagram

```
Customer           API Gateway     Booking Service       Catalog Service         Redis
   │                   │                  │                     │                  │
   │──POST /bookings──►│──Forward────────►│                     │                  │
   │                   │                  │                     │                  │
   │                   │                  │──GET /ticket-class─►│                  │
   │                   │                  │  (check avail.)     │                  │
   │                   │                  │◄──available_qty─────│                  │
   │                   │                  │                     │                  │
   │                   │                  │──INCRBY hold_count────────────────────►│
   │                   │                  │  (atomic increment)                    │
   │                   │                  │◄─────────new_count─────────────────────│
   │                   │                  │                     │                  │
   │                   │                  │──[Check: avail_qty  - new_count >= 0?] │
   │                   │                  │                     │                  │
   │                   │                  │  IF YES:            │                  │
   │                   │                  │──SET seat_hold key (TTL=600s)─────────►│
   │                   │                  │──INSERT booking (PENDING_PAYMENT)      │
   │                   │                  │──INSERT tickets (LOCKED)               │
   │                   │                  │                     │                  │
   │◄─────201 Created──┤◄─── booking ─────│                     │                  │
   │  (paymentUrl,     │     response     │                     │                  │
   │   expiredAt)      │                  │                     │                  │
   │                   │                  │  IF NO (hết vé):    │                  │
   │                   │                  │──DECRBY hold_count (rollback)─────────►│
   │◄─────409 Conflict─┤◄───SOLD_OUT──────│                     │                  │
```

### 1.3. Chi tiết kỹ thuật: Atomic Check with Redis

```java
// Pseudo-code: Atomic seat hold using Redis
public BookingResponse createBooking(BookingRequest request) {
    String holdCountKey = "hold_count:" + request.eventId + ":" + request.ticketClassId;

    // Step 1: Lấy available_quantity từ Catalog Service
    int availableQty = catalogClient.getAvailableQuantity(request.ticketClassId);

    // Step 2: Atomic increment trên Redis
    Long newHoldCount = redis.incrBy(holdCountKey, request.quantity);

    // Step 3: Check xem còn đủ không
    // realAvailable = availableQty (DB) - newHoldCount (Redis)
    if (availableQty - newHoldCount < 0) {
        // Rollback: trả lại hold count
        redis.decrBy(holdCountKey, request.quantity);
        throw new SoldOutException("Không đủ vé");
    }

    // Step 4: Tạo seat hold key (TTL 10 phút)
    String seatHoldKey = "seat_hold:" + request.eventId + ":"
                        + request.ticketClassId + ":" + customerId;
    redis.setEx(seatHoldKey, 600, bookingId);

    // Step 5: Tạo booking & tickets trong PostgreSQL
    Booking booking = createBookingRecord(request, "PENDING_PAYMENT");
    createTicketRecords(booking, request.quantity, "LOCKED");

    return new BookingResponse(booking);
}
```

### 1.4. Tại sao dùng Redis INCRBY mà không dùng Database Lock?

| Tiêu chí | Redis INCRBY | Database SELECT FOR UPDATE |
|----------|-------------|---------------------------|
| Tốc độ | ~0.1ms | ~5-50ms |
| Throughput | 100,000+ ops/sec | ~1,000 ops/sec |
| Blocking | Non-blocking (atomic) | Blocking (row lock) |
| Phù hợp cho | Burst traffic (mở bán vé) | Low traffic operations |

---

## 2. Luồng Tự động Nhả vé (Auto-Release Flow)

### 2.1. Mô tả

Khi TTL của Redis key hết hạn (10 phút), hệ thống tự động:
1. Hủy booking (status → CANCELLED)
2. Hủy tickets (status → CANCELLED)
3. Giảm hold_count trên Redis
4. Bắn event `booking.cancelled` để Catalog Service biết

### 2.2. Implementation: Redis Keyspace Notification + Scheduled Job

```
┌─────────────────┐          ┌──────────────────┐          ┌───────────────┐
│     Redis       │          │ Booking Service  │          │    Kafka      │
│                 │          │  (Scheduler)     │          │               │
│  TTL expired    │          │                  │          │               │
│  seat_hold:*    │──notify─►│ Cancel booking   │          │               │
│                 │          │ Cancel tickets   │          │               │
│  DECRBY         │◄─────────│ DECRBY count     │          │               │
│  hold_count     │          │                  │          │               │
│                 │          │──booking.cancelled─────────►│               │
│                 │          │                  │          │               │
└─────────────────┘          └──────────────────┘          └───────┬───────┘
                                                                   │
                                                           ┌───────▼───────┐
                                                           │Catalog Service│
                                                           │               │
                                                           │ (Không cần    │
                                                           │  làm gì vì    │
                                                           │  available_qty│
                                                           │  chưa bị trừ) │
                                                           └───────────────┘
```

**Phương án bổ sung — Scheduled Job (Safety net):**

```java
// Chạy mỗi 1 phút, quét các booking hết hạn chưa được xử lý
@Scheduled(fixedRate = 60000)
public void releaseExpiredBookings() {
    List<Booking> expired = bookingRepo.findByStatusAndExpiredAtBefore(
        "PENDING_PAYMENT", Instant.now()
    );

    for (Booking booking : expired) {
        cancelBookingAndReleaseSeats(booking);
        kafkaProducer.send("booking.cancelled", new BookingCancelledEvent(booking));
    }
}
```

---

## 3. Luồng Thanh toán (Payment Flow — MoMo + Kafka Event-Driven)

### 3.0. Cấu hình MoMo Sandbox (`.env`)

```properties
# MoMo Sandbox Credentials — lấy từ https://developers.momo.vn
MOMO_PARTNER_CODE=MOMOBKUN20180529
MOMO_ACCESS_KEY=klm05TvNBzhg7h7j
MOMO_SECRET_KEY=at67qH6mk8w5Y1nAyMoTKhpAoNTVMkSf
MOMO_API_CREATE_URL=https://test-payment.momo.vn/v2/gateway/api/create
MOMO_API_REFUND_URL=https://test-payment.momo.vn/v2/gateway/api/refund
MOMO_IPN_URL=https://api.ticketbooking.vn/v1/payments/momo/ipn
```

> Sandbox dùng `test-payment.momo.vn`, Production dùng `payment.momo.vn`. Chỉ đổi biến `.env`, không sửa code.

---

### 3.1. Tạo chữ ký HMAC-SHA256

MoMo yêu cầu ký mọi request bằng HMAC-SHA256, các field phải **sắp xếp theo thứ tự alphabet** nối bằng `&`:

**Khi khởi tạo thanh toán (`/create`):**
```
rawSignature = "accessKey=<>&amount=<>&extraData=<>&ipnUrl=<>&orderId=<>&orderInfo=<>&partnerCode=<>&redirectUrl=<>&requestId=<>&requestType=captureWallet"
signature    = HmacSHA256(rawSignature, secretKey)
```

**Khi verify IPN Callback:**
```
rawSignature = "accessKey=<>&amount=<>&extraData=<>&message=<>&orderId=<>&orderInfo=<>&orderType=<>&partnerCode=<>&payType=<>&requestId=<>&responseTime=<>&resultCode=<>&transId=<>"
```

**Java Implementation:**
```java
public static String hmacSHA256(String data, String key) throws Exception {
    Mac mac = Mac.getInstance("HmacSHA256");
    mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
    byte[] rawHmac = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
    return Hex.encodeHexString(rawHmac); // Apache Commons Codec
}
```

---

### 3.2. Sequence Diagram đầy đủ

```
Customer       Payment Service     MoMo Gateway           Kafka          Booking Svc      Catalog Svc    Notification Svc
   │                │                    │                  │                │                │                │
   │──POST          │                    │                  │                │                │                │
   │  /payments/────►                    │                  │                │                │                │
   │  initiate      │──[1] Validate booking ────────────────────────────────►│                │                │
   │                │◄── booking OK ──────────────────────────────────────────────────────────│                │
   │                │──[2] INSERT Transaction (PENDING)     │                │                │                │
   │                │──[3] rawSignature + HMAC-SHA256       │                │                │                │
   │                │──[4] POST /api/create ───────────────►│                │                │                │
   │                │      {partnerCode, requestId,         │                │                │                │
   │                │       orderId=bookingId, amount,      │                │                │                │
   │                │       redirectUrl, ipnUrl,            │                │                │                │
   │                │       requestType=captureWallet,      │                │                │                │
   │                │       signature}                      │                │                │                │
   │                │◄── {resultCode:0, payUrl} ────────────│                │                │                │
   │                │──[5] Lưu payUrl vào Transaction       │                │                │                │
   │◄── {paymentUrl}│                    │                  │                │                │                │
   │                │                    │                  │                │                │                │
   │── Redirect trình duyệt → MoMo ─────►│                  │                │                │                │
   │   (khách chọn: ví MoMo / thẻ ATM / QR)                 │                │                │                │
   │                │                    │                  │                │                │                │
   │                │◄──[6] IPN POST /payments/momo/ipn─────│                │                │                │
   │                │   (server-to-server, ngay sau TT)     │                │                │                │
   │                │──[7] Verify HMAC-SHA256 chữ ký IPN    │                │                │                │
   │                │──[8] Check amount == booking.amount   │                │                │                │
   │                │──[9] Idempotency: transId chưa tồn tại DB?             │                │                │
   │                │──[10] UPDATE Transaction (SUCCESS, transId, paidAt)    │                │                │
   │                │──[11] HTTP 204 cho MoMo (< 5 giây!)   │                │                │                │
   │                │──[12] Publish payment.success ───────►│                │                │                │
   │                │                    │                  │──consume──────►│                │                │
   │                │                    │                  │                │──UPDATE booking (PAID)          │
   │                │                    │                  │                │──UPDATE tickets (ISSUED)        │
   │                │                    │                  │                │──DEL Redis seat_hold:*          │
   │                │                    │                  │◄──tickets.generated─────────────│                │
   │                │                    │                  │──consume───────────────────────►│                │
   │                │                    │                  │                │                │──DECRBY avail_qty
   │                │                    │                  │──consume────────────────────────────────────────►│
   │                │                    │                  │                │                │                │──Tạo QR image
   │                │                    │                  │                │                │                │──Gửi email HTML
   │                │                    │                  │                │                │                │──Lưu MongoDB log
   │                │                    │                  │                │                │                │
   │── MoMo redirect trình duyệt về returnUrl (GET + query params) ────────────────────────────────────────│
   │── GET /bookings/{id} ──────────────────────────────────────────────────►│                │                │
   │◄── { status: "PAID", tickets: [...] } ──────────────────────────────────│                │                │
```

> **resultCode ≠ 0 (thất bại):** Payment Service publish `payment.failed`, Booking Service bỏ qua — booking vẫn `PENDING_PAYMENT`, khách có thể thử lại đến khi hết 10 phút giữ chỗ.

**resultCode quan trọng của MoMo:**

| resultCode | Ý nghĩa | Hành động |
|-----------|---------|-----------|
| `0` | Thành công | Publish `payment.success` |
| `1001` | Không đủ số dư ví | Publish `payment.failed` |
| `1006` | Người dùng huỷ | Publish `payment.failed` |
| `1005` | URL/Token hết hạn | Publish `payment.failed` |
| `9000` | Ngân hàng từ chối | Publish `payment.failed` |

---

### 3.3. Idempotency — Xử lý event trùng lặp

Kafka at-least-once delivery: consumer có thể nhận cùng 1 event nhiều lần → mọi consumer **phải idempotent**:

```java
// Booking Service — consume payment.success
@KafkaListener(topics = "payment.success", groupId = "booking-service")
public void onPaymentSuccess(PaymentSuccessEvent event) {
    Booking booking = bookingRepo.findById(event.getBookingId())
        .orElseThrow(() -> new AppException(ErrorCode.BOOKING_NOT_FOUND));

    // Idempotency: đã PAID rồi thì bỏ qua
    if (booking.getStatus() == BookingStatus.PAID) {
        log.warn("Duplicate payment.success for booking {}, skipping", booking.getId());
        return;
    }

    booking.setStatus(BookingStatus.PAID);
    bookingRepo.save(booking);

    ticketRepo.updateStatusByBookingId(booking.getId(), TicketStatus.ISSUED);

    redisTemplate.delete("seat_hold:" + booking.getEventId() + ":" + booking.getCustomerId());
    redisTemplate.opsForValue().decrement("hold_count:" + booking.getEventId() + ":" + booking.getTicketClassId(), booking.getQuantity());

    kafkaTemplate.send("tickets.generated", TicketsGeneratedEvent.builder()
        .bookingId(booking.getId())
        .customerId(booking.getCustomerId())
        .eventId(booking.getEventId())
        .quantity(booking.getQuantity())
        .build());
}
```

---

### 3.4. Switch Sandbox → Production

Chỉ cần sửa file `.env`, không cần sửa một dòng code:
```properties
MOMO_API_CREATE_URL=https://payment.momo.vn/v2/gateway/api/create
MOMO_API_REFUND_URL=https://payment.momo.vn/v2/gateway/api/refund
MOMO_PARTNER_CODE=<production_code>
MOMO_ACCESS_KEY=<production_access_key>
MOMO_SECRET_KEY=<production_secret_key>
```

---

---

## 4. Luồng Saga — Hoàn tiền khi lỗi (Compensation Flow)

### 4.1. Kịch bản

Khách thanh toán thành công → Payment Service bắn `payment.success` → Booking Service cập nhật nhưng **lỗi khi sinh vé** (ví dụ: DB timeout) → Cần hoàn tiền cho khách.

### 4.2. Compensation Sequence

```
Payment Service          Kafka              Booking Service
      │                    │                      │
      │──payment.success──►│                      │
      │                    │──consume────────────►│
      │                    │                      │──Try update booking
      │                    │                      │  ❌ FAILED (DB error)
      │                    │                      │
      │                    │◄──booking.refund─────│
      │                    │   -requested         │
      │◄──consume──────────│                      │
      │                    │                      │
      │──Call MoMo Refund  │                      │
      │  (POST /v2/gateway/api/refund)            │
      │──Update txn status │                      │
      │  (REFUNDED)        │                      │
      │                    │                      │
      │──payment.refunded─►│                      │
      │                    │──consume────────────►│
      │                    │                      │──Update booking
      │                    │                      │  (REFUNDED)
      │                    │                      │──Release seats
```

### 4.3. Saga State Machine

```
                          ┌─────────────┐
                          │   CREATED   │
                          └──────┬──────┘
                                 │ payment.success
                                 ▼
                          ┌─────────────┐
                     ┌────│  PROCESSING │────┐
                     │    └─────────────┘    │
              success│                       │failure
                     ▼                       ▼
              ┌─────────────┐         ┌─────────────┐
              │  COMPLETED  │         │COMPENSATING │
              └─────────────┘         └──────┬──────┘
                                             │ refund complete
                                             ▼
                                      ┌─────────────┐
                                      │  REFUNDED   │
                                      └─────────────┘
```

---

## 4b. Luồng Payout — Chi trả tự động cho Organizer qua MoMo Disbursement 🆕

### 4b.1. Phí nền tảng (hoa hồng)

Mỗi vé bán được (`tickets.generated`) bị trừ phí nền tảng khi tính `netRevenue` cho Organizer:

```
phí/vé = giá vé × commissionRate + flatFeePerTicket
```

- Mặc định `commissionRate = 0.05` (5%), `flatFeePerTicket = 3000` (VND) — set khi `events` được tạo (Catalog Service).
- Vé giá 0đ **luôn miễn phí hoàn toàn**, không tính `flatFeePerTicket`.
- Chỉ ADMIN sửa được `commissionRate`/`flatFeePerTicket`, sửa riêng theo từng sự kiện (`PATCH /admin/events/{eventId}/commission`) — dùng khi đàm phán đối tác lớn hoặc sự kiện thiện nguyện.

### 4b.2. Thiết lập & xác minh tài khoản ngân hàng nhận tiền 🆕

> **1 Organizer = đúng 1 tài khoản ngân hàng cố định**, lưu ở **User Service** (bảng `organizer_bank_accounts`, xem `database-schema.md`), KHÔNG lưu ở Payment Service và KHÔNG do client gửi kèm mỗi request payout — xem `api-design.md §7.3`.

```
Organizer                     User Service                  Admin
    │                              │                           │
    │──PUT /organizer/bank-account►│                           │
    │  { bankName, accountNumber,  │──upsert theo profile_id    │
    │    accountHolder }           │  (UNIQUE constraint)       │
    │                              │──verified = false ◄────────┤ (reset mỗi lần đổi TK)
    │◄──200 { verified: false }────│                           │
    │                              │                           │
    │           (Admin đối chiếu giấy phép kinh doanh/CCCD ngoài hệ thống)
    │                              │◄──PATCH .../bank-account/verify
    │                              │──verified = true, verifiedAt = now()
```

- Đổi tài khoản ngân hàng **luôn reset `verified = false`** — chống kịch bản tài khoản Organizer bị chiếm đoạt rồi đổi ngay số TK nhận tiền để rút trộm.
- Payment Service (cả luồng AUTO lẫn MANUAL ở dưới) **bắt buộc gọi `GET` nội bộ sang User Service** để lấy tài khoản ngân hàng đã `verified = true` trước khi tạo/duyệt payout — chưa xác minh thì không tạo được payout.

### 4b.3. Job nền tạo Payout tự động

```java
// Chạy 1 lần/ngày, quét các event COMPLETED đã đủ 7 ngày kể từ endTime
@Scheduled(cron = "0 0 3 * * *") // 3h sáng mỗi ngày
public void createAutoPayouts() {
    List<Event> eligibleEvents = eventRepo.findCompletedEventsWithoutAutoPayout(
        Instant.now().minus(7, ChronoUnit.DAYS)
    );

    for (Event event : eligibleEvents) {
        BigDecimal netRevenue = calculateNetRevenue(event); // Σ (giá vé - phí/vé) × sold
        if (netRevenue.compareTo(BigDecimal.ZERO) <= 0) continue;

        // Tra cứu tài khoản ngân hàng đã xác minh của Organizer (gọi User Service)
        Optional<VerifiedBankAccount> bankAccount = userServiceClient.getVerifiedBankAccount(event.getOrganizerId());

        PayoutRequest.PayoutRequestBuilder builder = PayoutRequest.builder()
            .organizerId(event.getOrganizerId())
            .amount(netRevenue)
            .source(PayoutSource.AUTO)
            .eventId(event.getId());

        if (bankAccount.isPresent()) {
            // Snapshot bank info tại thời điểm tạo — không tham chiếu ngược lại User Service nữa
            builder.status(PayoutStatus.PENDING)
                   .bankName(bankAccount.get().bankName())
                   .bankAccountNumber(bankAccount.get().accountNumber())
                   .bankAccountHolder(bankAccount.get().accountHolder());
        } else {
            // Chưa có tài khoản ngân hàng đã xác minh → HOLD, không được PAID cho tới khi Organizer thiết lập
            builder.status(PayoutStatus.HOLD)
                   .reason("Organizer chưa thiết lập/xác minh tài khoản ngân hàng nhận tiền.");
        }
        payoutRepo.save(builder.build());
    }
}
```

### 4b.4. Duyệt & chi trả qua MoMo Disbursement

```
Organizer                Admin                Payout/Payment Service               MoMo Disbursement API
    │                       │                           │                                    │
    │  (payout AUTO tự tạo, hoặc Organizer bấm          │                                    │
    │   "Yêu cầu rút tiền" tạo payout MANUAL)           │                                    │
    │                       │──PATCH .../status────────►│                                    │
    │                       │  { status: APPROVED }     │                                    │
    │                       │                           │──lưu status=APPROVED               │
    │                       │──PATCH .../status────────►│                                    │
    │                       │  { status: PAID }         │                                    │
    │                       │                           │──POST /v2/gateway/api/disburse────►│
    │                       │                           │  (partnerCode, requestId,          │
    │                       │                           │   orderId=payoutId, amount,        │
    │                       │                           │   receiver, signature)             │
    │                       │                           │◄──resultCode/IPN Disbursement──────│
    │                       │                           │──resultCode=0 → PayoutRequest.status=PAID, processedAt=now()
    │                       │                           │──resultCode≠0 → giữ APPROVED, log lỗi, Admin retry hoặc chuyển khoản tay
    │◄──nhận tiền vào TK ngân hàng/ví MoMo───────────────────────────────────────────────────│
```

- **HOLD**: Admin có thể tạm giữ payout ở bất kỳ trạng thái nào trước `PAID` (nghi ngờ gian lận/khiếu nại) — bắt buộc nhập `reason`. Cũng tự động xảy ra khi tạo payout AUTO mà Organizer chưa có tài khoản ngân hàng đã xác minh (§4b.2). Mở lại → về `PENDING`.
- **REJECTED**: chỉ áp dụng cho payout `source=MANUAL` (Organizer tự xin) — bắt buộc nhập `reason`.
- Payout `source=MANUAL` (`POST /organizer/payouts`, `api-design.md §7.4`) bị từ chối ngay tại API (409 `BANK_ACCOUNT_NOT_VERIFIED`) nếu Organizer chưa có tài khoản ngân hàng `verified = true` — không tạo ra bản ghi `PENDING` rồi mới HOLD như luồng AUTO.
- `PAID` là trạng thái cuối, không đổi được nữa.
- IPN của Disbursement là endpoint **riêng** với IPN thanh toán (mục 5.2 `api-design.md`) — MoMo phân biệt 2 luồng callback khác nhau dù cùng chữ ký HMAC-SHA256.

---

## 5. Luồng Check-in QR Code

### 5.1. Sequence Diagram

```
Organizer App      API Gateway       Booking Service       Database
     │                  │                  │                   │
     │──POST /check-in─►│──Forward────────►│                   │
     │  {qrCodeData}    │                  │                   │
     │                  │                  │──SELECT ticket    │
     │                  │                  │  WHERE qr_code =  │
     │                  │                  │  ? AND status =   │
     │                  │                  │  'ISSUED'         │
     │                  │                  │◄──ticket data─────│
     │                  │                  │                   │
     │                  │                  │──[Validate]       │
     │                  │                  │  - Ticket exists? │
     │                  │                  │  - Status=ISSUED? │
     │                  │                  │  - Event matches? │
     │                  │                  │  - Not expired?   │
     │                  │                  │                   │
     │                  │                  │──UPDATE ticket    │
     │                  │                  │  SET status =     │
     │                  │                  │  'CHECKED_IN',    │
     │                  │                  │  checked_in_at =  │
     │                  │                  │  NOW()            │
     │                  │                  │◄──────────────────│
     │                  │                  │                   │
     │◄─── 200 OK ──────┤◄── ticket info ──│                   │
     │  (customer name, │                  │                   │
     │   ticket class)  │                  │                   │
```

### 5.2. Validation Rules

| Rule | Check | Error |
|------|-------|-------|
| Ticket tồn tại | `qr_code_data` khớp trong DB | `TICKET_NOT_FOUND` |
| Ticket đã phát hành | `status = ISSUED` | `TICKET_NOT_ISSUED` |
| Chưa check-in | `status ≠ CHECKED_IN` | `TICKET_ALREADY_CHECKED_IN` |
| Đúng sự kiện | `ticket.event_id = organizer's event` | `UNAUTHORIZED_EVENT` |

---

## 6. Tổng hợp Kafka Event Flow

```
                    ┌───────────────────────────────────────────────┐
                    │                KAFKA BROKER                   │
                    │                                               │
                    │  ┌────────────────────────┐                   │
 Auth ─────────────►│  │ auth.organizer-created │                   │──────────► Notification
 Service            │  ├────────────────────────┤                   │            Service
                    │  │ auth.otp-requested     │                   │            (Send Email)
                    │  └────────────────────────┘                   │
                    │                                               │
                    │  ┌─────────────────┐  ┌────────────────────┐  │
 Payment ──────────►│  │ payment.success │  │ payment.failed     │  │──────────► Booking
 Service            │  └─────────────────┘  └────────────────────┘  │           Service
                    │                                               │
                    │  ┌─────────────────┐  ┌────────────────────┐  │
 Booking ──────────►│  │tickets.generated│  │ booking.cancelled  │  │──────────► Catalog
 Service            │  └─────────────────┘  └────────────────────┘  │           Service
                    │                                               │
                    │  ┌─────────────────┐                          │
 Booking ──────────►│  │tickets.generated│                          │──────────► Notification
 Service            │  └─────────────────┘                          │           Service (QR Mail)
                    │                                               │
                    │  ┌──────────────────────┐                     │
 Booking ──────────►│  │booking.refund-request│                     │──────────► Payment
 Service            │  └──────────────────────┘                     │           Service
                    │                                               │
                    └───────────────────────────────────────────────┘
```

---

> 📄 Xem thêm: [System Design](system-design.md) | [Database Schema](database-schema.md) | [API Design](api-design.md)

