# ⚡ Technical Flows — TicketBooking

> Luồng xử lý kỹ thuật chi tiết cho các nghiệp vụ cốt lõi

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
   │                   │                  │                    │                   │
   │                   │                  │──[Check: avail_qty - new_count >= 0?]  │
   │                   │                  │                    │                   │
   │                   │                  │  IF YES:           │                   │
   │                   │                  │──SET seat_hold key (TTL=600s)─────────►│
   │                   │                  │──INSERT booking (PENDING_PAYMENT)      │
   │                   │                  │──INSERT tickets (LOCKED)               │
   │                   │                  │                    │                   │
   │◄─────201 Created──┤◄─── booking ─────│                    │                   │
   │  (paymentUrl,     │     response     │                    │                   │
   │   expiredAt)      │                  │                    │                   │
   │                   │                  │  IF NO (hết vé):   │                   │
   │                   │                  │──DECRBY hold_count (rollback)─────────►│
   │◄─────409 Conflict─┤◄───SOLD_OUT──────│                    │                   │
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

## 3. Luồng Thanh toán (Payment Flow — Kafka Event-Driven)

### 3.1. Sequence Diagram

```
Customer       Payment Service     VNPay Gateway       Kafka           Booking Svc      Catalog Svc    Notification Svc
   │                │                    │                │                │                │                │
   │──Initiate pay─►│                    │                │                │                │                │
   │                │──Create payment───►│                │                │                │                │
   │◄──paymentUrl───│                    │                │                │                │                │
   │                │                    │                │                │                │                │
   │──Pay on VNPay──────────────────────►│                │                │                │                │
   │                │                    │                │                │                │                │
   │                │◄──IPN Callback─────│                │                │                │                │
   │                │                    │                │                │                │                │
   │                │──Verify checksum   │                │                │                │                │
   │                │──Save transaction  │                │                │                │                │
   │                │  (status=SUCCESS)  │                │                │                │                │
   │                │                    │                │                │                │                │
   │                │──────payment.success───────────────►│                │                │                │
   │                │                    │                │                │                │                │
   │                │                    │                │──consume──────►│                │                │
   │                │                    │                │                │──Update booking│                │
   │                │                    │                │                │  (PAID)        │                │
   │                │                    │                │                │──Update tickets│                │
   │                │                    │                │                │  (ISSUED)      │                │
   │                │                    │                │                │──Delete Redis  │                │
   │                │                    │                │                │  hold keys     │                │
   │                │                    │                │                │                │                │
   │                │                    │                │◄──tickets.generated─────────────│                │
   │                │                    │                │                │                │                │
   │                │                    │                │──consume───────────────────────►│                │
   │                │                    │                │                │                │──DECRBY        │
   │                │                    │                │                │                │  available_qty │
   │                │                    │                │                │                │                │
   │                │                    │                │──consume────────────────────────────────────────►│
   │                │                    │                │                │                │                │──Send email
   │                │                    │                │                │                │                │  with QR code
```

### 3.2. Idempotency Handling

Kafka có thể gửi cùng 1 event nhiều lần (at-least-once delivery). Mỗi consumer phải xử lý idempotent:

```java
// Booking Service - consume payment.success
@KafkaListener(topics = "payment.success")
public void onPaymentSuccess(PaymentSuccessEvent event) {
    // Idempotency check: nếu booking đã PAID thì skip
    Booking booking = bookingRepo.findById(event.getBookingId());
    if (booking.getStatus() == BookingStatus.PAID) {
        log.info("Booking {} already PAID, skipping duplicate event", booking.getId());
        return;
    }

    // Process normally
    booking.setStatus(BookingStatus.PAID);
    bookingRepo.save(booking);
    // ... update tickets, publish tickets.generated event
}
```

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
      │──Call VNPay Refund │                      │
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
 Service            │  └─────────────────┘                          │           Service
                    │                                               │
                    │  ┌──────────────────────┐                     │
 Booking ──────────►│  │booking.refund-request│                     │──────────► Payment
 Service            │  └──────────────────────┘                     │           Service
                    │                                               │
                    └───────────────────────────────────────────────┘
```

---

> 📄 Xem thêm: [System Design](system-design.md) | [Database Schema](database-schema.md) | [API Design](api-design.md)
