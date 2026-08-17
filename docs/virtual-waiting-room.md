# 🚦 Virtual Waiting Room — Thiết kế Phòng Chờ Ảo

> Hệ thống tự động phát hiện quá tải và chuyển sang chế độ hàng chờ real-time

---

## 1. Tổng quan

### 1.1. Vấn đề

Khi sự kiện "hot" mở bán (ví dụ: concert Sơn Tùng MTP), **50.000 người** cùng truy cập trong 1 giây. Nếu để tất cả vào thẳng trang đặt vé:
- Server quá tải → crash
- Trải nghiệm tệ → ai nhanh tay thì được, chậm thì lỗi 503
- Không công bằng

### 1.2. Giải pháp: Virtual Waiting Room

```
┌───────────────────────────────────────────────────────────────────────┐
│                         LUỒNG HOẠT ĐỘNG                               │
│                                                                       │
│   50,000 người ──► [Phòng chờ ảo] ──► Cho vào từng đợt ──► Đặt vé     │
│                     "Vị trí #4523"     (500 người/đợt)              │
│                     "Còn ~9 phút"                                     │
│                                                                       │
│   ⚡ Real-time: WebSocket cập nhật vị trí liên tục                    │
│   🔌 Ngắt kết nối WebSocket = Mất chỗ trong hàng chờ                  │
│   ⏰ Vào được trang đặt vé = có 10 phút để mua                        │
└───────────────────────────────────────────────────────────────────────┘
```

---

## 2. Kiến trúc

### 2.1. Service mới: Queue Service

```
                                    ┌──────────────────┐
                                    │   API Gateway    │
                                    └────────┬─────────┘
                                             │
                    ┌────────────────────────┼────────────────────────┐
                    │                        │                        │
             ┌──────▼──────┐          ┌──────▼──────┐          ┌──────▼──────┐
             │Queue Service│          │ Booking Svc │          │  ...other   │
             │    (MỚI)    │          │             │          │  services   │
             │             │          │             │          │             │
             │    Redis    │◄────────►│    Redis    │          │             │
             │  WebSocket  │          │             │          │             │
             └─────────────┘          └─────────────┘          └─────────────┘
```

### 2.2. Thành phần chính

| Thành phần | Công nghệ | Vai trò |
|------------|-----------|---------|
| **Queue Service** | Java 21+ / Spring Boot 4.1.1+ (WebSocket STOMP) | Quản lý hàng chờ, cấp token vào |
| **Redis Sorted Set** | Redis ZADD/ZRANK | Lưu thứ tự hàng chờ (O(log N)) |
| **Redis Counter** | Redis INCR/DECR | Đếm số người đang ở trong trang đặt vé |
| **WebSocket (STOMP)** | Spring WebSocket (Spring Boot 4.1.1+) | Push real-time vị trí cho client |
| **Heartbeat** | WebSocket Ping/Pong | Phát hiện ngắt kết nối → loại khỏi hàng chờ |

---

## 3. Redis Data Structure

```
# ═══════════════════════════════════════════════════════════════
# 1. CẤU HÌNH QUEUE CHO TỪNG EVENT
# ═══════════════════════════════════════════════════════════════

Key:    queue:config:{eventId}
Type:   Hash
Fields:
  - enabled: true/false              # Queue có bật không
  - maxConcurrent: 500               # Tối đa bao nhiêu người ở trang đặt vé cùng lúc
  - autoEnableThreshold: 1000        # Tự động bật queue khi có > 1000 người đang chờ
  - purchaseWindowSeconds: 600       # Mỗi người có 10 phút để mua sau khi vào

# ═══════════════════════════════════════════════════════════════
# 2. HÀNG CHỜ (Sorted Set — xếp hạng theo thời gian vào)
# ═══════════════════════════════════════════════════════════════

Key:    queue:waiting:{eventId}
Type:   Sorted Set
Member: {userId}
Score:  timestamp (thời điểm vào hàng chờ)    ← ai vào trước, score nhỏ hơn, rank cao hơn

Ví dụ:
  ZADD queue:waiting:event123  1700000001  "user-A"    → Vị trí #1
  ZADD queue:waiting:event123  1700000002  "user-B"    → Vị trí #2
  ZADD queue:waiting:event123  1700000003  "user-C"    → Vị trí #3

Lấy vị trí: ZRANK queue:waiting:event123 "user-B"     → 1 (0-indexed → hiển thị #2)

# ═══════════════════════════════════════════════════════════════
# 3. ĐẾM SỐ NGƯỜI ĐANG Ở TRANG ĐẶT VÉ (Active Shoppers)
# ═══════════════════════════════════════════════════════════════

Key:    queue:active:{eventId}
Type:   Sorted Set
Member: {userId}
Score:  timestamp hết hạn (now + 10 phút)

Số người đang mua: ZCARD queue:active:event123
Dọn dẹp hết hạn:  ZREMRANGEBYSCORE queue:active:event123 0 {now}

# ═══════════════════════════════════════════════════════════════
# 4. ACCESS TOKEN (Người được vào đặt vé)
# ═══════════════════════════════════════════════════════════════

Key:    queue:token:{eventId}:{userId}
Type:   String
Value:  {accessToken UUID}
TTL:    600 seconds (10 phút)

# ═══════════════════════════════════════════════════════════════
# 5. HEARTBEAT (Theo dõi kết nối WebSocket)
# ═══════════════════════════════════════════════════════════════

Key:    queue:heartbeat:{eventId}:{userId}
Type:   String
Value:  "alive"
TTL:    15 seconds (phải ping lại trước khi hết)
```

---

## 4. Luồng xử lý chi tiết

### 4.1. Luồng tổng quan

```
Customer             API Gateway          Queue Service               Redis              Booking Service
   │                     │                      │                       │                      │
   │── GET /events/123 ─►│── Forward ──────────►│                       │                      │
   │                     │                      │── Check queue status─►│                      │
   │                     │                      │◄─── enabled=true ─────│                      │
   │                     │                      │                       │                      │
   │◄── "Queue enabled"──┤◄── queueRequired ────│                       │                      │
   │                     │                      │                       │                      │
   │                     │                      │                       │                      │
   │══ WebSocket Connect ══════════════════════►│                       │                      │
   │                     │                      │─── ZADD waiting ─────►│                      │
   │                     │                      │─── SET heartbeat ────►│                      │
   │◄══ "Vị trí #4523" ═══════════════════════│                       │                      │
   │                     │                      │                       │                      │
   │   ... chờ ...       │                      │                       │                      │
   │                     │                      │                       │                      │
   │◄══ "Vị trí #523" ════════════════════════│  (cập nhật liên tục)  │                      │
   │◄══ "Vị trí #12"  ══════════════════════════│                       │                      │
   │                     │                      │                       │                      │
   │◄══ "ĐẾN LƯỢT BẠN!" ════════════════════════│                       │                      │
   │    + accessToken    │                      │─── SET token (TTL) ──►│                      │
   │                     │                      │─── ZADD active ──────►│                      │
   │                     │                      │── ─ZREM waiting ─────►│                      │
   │                     │                      │                       │                      │
   │                     │                      │                       │                      │
   │── POST /bookings ──►│                      │                       │                      │
   │  (+ accessToken)  ─ │── Validate token ───►│                       │                      │
   │                     │                      │─── GET token ────────►│                      │
   │                     │                      │◄─── valid ────────────│                      │
   │                     │◄── OK ───────────────│                       │                      │
   │                     │── Forward ─────────────────────────────────────────────────────────►│
   │◄── Booking created ─┤                      │                       │                      │
```

### 4.2. Luồng tự động bật Queue khi quá tải

```
                    ┌──────────────────────────────────────────┐
                    │         AUTO-ENABLE LOGIC                │
                    │                                          │
                    │  Mỗi 1 giây, Queue Service kiểm tra:     │
                    │                                          │
                    │  currentWaiting = ZCARD waiting:{event}  │
                    │  currentActive  = ZCARD active:{event}   │
                    │  threshold      = config.autoEnable      │
                    │                                          │
                    │  IF (currentActive > threshold)          │
                    │     → TỰ ĐỘNG BẬT QUEUE                  │
                    │     → SET config.enabled = true          │
                    │                                          │
                    │  IF (currentActive < threshold * 0.3)    │
                    │     → TỰ ĐỘNG TẮT QUEUE                  │
                    │     → SET config.enabled = false         │
                    │     → Cho tất cả waiting vào luôn        │
                    └──────────────────────────────────────────┘
```

```java
// Pseudo-code: Auto-enable queue
@Scheduled(fixedRate = 1000) // Mỗi 1 giây
public void checkAndToggleQueue(String eventId) {
    QueueConfig config = getConfig(eventId);
    long activeCount = redis.zcard("queue:active:" + eventId);
    long waitingCount = redis.zcard("queue:waiting:" + eventId);
    long totalDemand = activeCount + waitingCount;

    if (!config.isEnabled() && totalDemand > config.getAutoEnableThreshold()) {
        // 🔴 QUÁ TẢI → Bật queue
        config.setEnabled(true);
        saveConfig(eventId, config);
        log.warn("🚦 Queue AUTO-ENABLED for event {} (demand: {})", eventId, totalDemand);
    }

    if (config.isEnabled() && activeCount < config.getAutoEnableThreshold() * 0.3) {
        // 🟢 Tải đã giảm → Tắt queue
        config.setEnabled(false);
        saveConfig(eventId, config);
        admitAllWaiting(eventId); // Cho tất cả vào luôn
        log.info("🟢 Queue AUTO-DISABLED for event {} (active: {})", eventId, activeCount);
    }
}
```

### 4.3. Luồng cho người vào từng đợt (Admission)

```java
// Chạy mỗi 2 giây: kiểm tra và cho người vào
@Scheduled(fixedRate = 2000)
public void admitNextBatch(String eventId) {
    QueueConfig config = getConfig(eventId);

    // 1. Dọn dẹp: Xóa active users đã hết hạn 10 phút
    redis.zremrangeByScore("queue:active:" + eventId, 0, Instant.now().getEpochSecond());

    // 2. Tính số slot trống
    long currentActive = redis.zcard("queue:active:" + eventId);
    long availableSlots = config.getMaxConcurrent() - currentActive;

    if (availableSlots <= 0) return; // Đầy rồi, chờ tiếp

    // 3. Lấy N người đầu hàng chờ (người vào sớm nhất)
    Set<String> nextUsers = redis.zrange("queue:waiting:" + eventId, 0, availableSlots - 1);

    for (String userId : nextUsers) {
        // 4. Chuyển từ waiting → active
        redis.zrem("queue:waiting:" + eventId, userId);
        redis.zadd("queue:active:" + eventId,
                    Instant.now().plusSeconds(600).getEpochSecond(), userId);

        // 5. Cấp access token (TTL 10 phút)
        String accessToken = UUID.randomUUID().toString();
        redis.setex("queue:token:" + eventId + ":" + userId, 600, accessToken);

        // 6. Push WebSocket: "ĐẾN LƯỢT BẠN!"
        websocket.sendToUser(userId, new QueueMessage(
            "ADMITTED",
            accessToken,
            600  // seconds remaining
        ));
    }
}
```

---

## 5. Heartbeat — Ngắt kết nối = Mất chỗ

### 5.1. Cơ chế

```
Client (Browser)                    Queue Service                     Redis
      │                                  │                              │
      │══ WebSocket Connected ══════════►│                              │
      │                                  │─── SET heartbeat TTL=15s ───►│
      │                                  │                              │
      │   ... mỗi 10 giây ...            │                              │
      │───── PING (heartbeat) ──────────►│                              │
      │                                  │─── SET heartbeat TTL=15s ───►│ ← reset TTL
      │◄──── PONG ───────────────────────│                              │
      │                                  │                              │
      │    ... 10 giây ...               │                              │
      │─── PING ────────────────────────►│                              │
      │                                  │─── SET heartbeat TTL=15s ───►│ ← reset TTL
      │                                  │                              │
      │                                  │                              │
      │    ❌ MẤT MẠNG / ĐÓNG TAB        │                              │
      │    (không gửi PING nữa)          │                              │
      │                                  │                              │
      │                                  │   ... 15 giây sau ...        │
      │                                  │                              │── heartbeat key
      │                                  │                              │   HẾT HẠN (expired)
      │                                  │◄─── Keyspace notification ───│
      │                                  │                              │
      │                                  │─── ZREM waiting:{event} ────►│ ← XÓA KHỎI HÀNG CHỜ
      │                                  │                              │
      │                                  │    → Slot trống → Cho người  │
      │                                  │      tiếp theo vào           │
```

### 5.2. Client-side JavaScript

```javascript
class WaitingRoomClient {
    constructor(eventId) {
        this.eventId = eventId;
        this.ws = null;
        this.heartbeatInterval = null;
    }

    connect() {
        this.ws = new WebSocket(`wss://api.ticketbooking.vn/ws/queue/${this.eventId}`);

        this.ws.onopen = () => {
            console.log('🟢 Đã kết nối phòng chờ');
            // Gửi heartbeat mỗi 10 giây
            this.heartbeatInterval = setInterval(() => {
                this.ws.send(JSON.stringify({ type: 'HEARTBEAT' }));
            }, 10000);
        };

        this.ws.onmessage = (event) => {
            const msg = JSON.parse(event.data);

            switch (msg.type) {
                case 'POSITION_UPDATE':
                    // Cập nhật UI: "Bạn đang ở vị trí #1234"
                    this.updateQueueUI(msg.position, msg.totalWaiting, msg.estimatedWaitSeconds);
                    break;

                case 'ADMITTED':
                    // 🎉 ĐẾN LƯỢT! Chuyển sang trang đặt vé
                    clearInterval(this.heartbeatInterval);
                    this.ws.close();
                    window.location.href = `/events/${this.eventId}/booking?token=${msg.accessToken}`;
                    break;

                case 'EVENT_SOLD_OUT':
                    // 😢 Hết vé
                    this.showSoldOutMessage();
                    break;
            }
        };

        this.ws.onclose = () => {
            clearInterval(this.heartbeatInterval);
            console.log('🔴 Mất kết nối — bạn đã bị loại khỏi hàng chờ');
            this.showDisconnectedMessage();
        };

        // ⚠️ Cảnh báo khi user đóng tab
        window.addEventListener('beforeunload', (e) => {
            e.preventDefault();
            e.returnValue = 'Bạn sẽ mất vị trí trong hàng chờ nếu rời trang!';
        });
    }

    updateQueueUI(position, total, estimatedSeconds) {
        document.getElementById('position').textContent = `#${position}`;
        document.getElementById('total').textContent = total;
        document.getElementById('eta').textContent = this.formatTime(estimatedSeconds);

        // Progress bar
        const progress = ((total - position) / total) * 100;
        document.getElementById('progress-bar').style.width = `${progress}%`;
    }

    formatTime(seconds) {
        const minutes = Math.floor(seconds / 60);
        if (minutes < 1) return 'Dưới 1 phút';
        if (minutes === 1) return 'Khoảng 1 phút';
        return `Khoảng ${minutes} phút`;
    }
}

// Sử dụng
const room = new WaitingRoomClient('event-uuid-123');
room.connect();
```

---

## 6. Ước tính thời gian chờ

```java
// Công thức ước tính thời gian chờ
public long estimateWaitSeconds(String eventId, long position) {
    QueueConfig config = getConfig(eventId);

    // Trung bình mỗi người mất bao lâu để mua xong hoặc hết hạn?
    // Giả sử: 70% mua trong 3 phút, 30% hết hạn sau 10 phút
    // → Trung bình ~5 phút/người
    long avgPurchaseSeconds = 300; // 5 phút

    // Số slot mở ra mỗi giây
    double slotsPerSecond = (double) config.getMaxConcurrent() / avgPurchaseSeconds;

    // Thời gian chờ = vị trí / tốc độ giải phóng slot
    return (long) (position / slotsPerSecond);
}

// Ví dụ:
// maxConcurrent = 500, avgPurchaseTime = 300s
// slotsPerSecond = 500 / 300 = 1.67 slots/giây
// Vị trí #1000 → chờ ≈ 1000 / 1.67 ≈ 600 giây ≈ 10 phút
```

---

## 7. WebSocket Messages Schema

### Server → Client

```json
// Cập nhật vị trí (mỗi 3 giây)
{
    "type": "POSITION_UPDATE",
    "position": 1234,
    "totalWaiting": 48500,
    "estimatedWaitSeconds": 420,
    "timestamp": "2024-06-15T08:59:30Z"
}

// Đến lượt!
{
    "type": "ADMITTED",
    "accessToken": "uuid-access-token",
    "expiresInSeconds": 600,
    "message": "Đến lượt bạn! Bạn có 10 phút để hoàn tất đặt vé."
}

// Hết vé
{
    "type": "EVENT_SOLD_OUT",
    "message": "Rất tiếc, tất cả vé đã được bán hết."
}
```

### Client → Server

```json
// Heartbeat (mỗi 10 giây)
{
    "type": "HEARTBEAT"
}
```

---

## 8. Booking Service — Validate Access Token

Khi Queue đang bật, Booking Service **phải kiểm tra access token** trước khi cho đặt vé:

```java
// Booking Service - tạo booking
public BookingResponse createBooking(BookingRequest request, String accessToken) {
    String eventId = request.getEventId();
    String customerId = getCurrentUserId();

    // 1. Kiểm tra queue có đang bật không
    boolean queueEnabled = queueClient.isQueueEnabled(eventId);

    if (queueEnabled) {
        // 2. Validate access token
        String tokenKey = "queue:token:" + eventId + ":" + customerId;
        String storedToken = redis.get(tokenKey);

        if (storedToken == null || !storedToken.equals(accessToken)) {
            throw new ForbiddenException("ACCESS_TOKEN_INVALID",
                "Token không hợp lệ hoặc đã hết hạn. Vui lòng quay lại hàng chờ.");
        }
    }

    // 3. Tiếp tục luồng đặt vé bình thường (INCRBY, tạo booking...)
    return processBooking(request);
}
```

---

## 9. UI Phòng chờ — Giao diện tham khảo

```
┌─────────────────────────────────────────────────────────┐
│                                                         │
│               SƠN TÙNG MTP LIVE CONCERT                 │
│                   Phòng chờ mua vé                      │
│                                                         │
│         ┌─────────────────────────────────┐             │
│         │                                 │             │
│         │        Vị trí của bạn           │             │
│         │          #1,234                 │             │
│         │                                 │             │
│         │    trong tổng 48,500 người      │             │
│         │                                 │             │
│         └─────────────────────────────────┘             │
│                                                         │
│         ████████████░░░░░░░░░░░░░░░░░░░░  35%           │
│                                                         │
│           Thời gian chờ ước tính: ~7 phút               │
│                                                         │
│         ─────────────────────────────────               │
│                                                         │
│              Đừng đóng trang này!                       │
│           Bạn sẽ mất vị trí nếu rời khỏi.               │
│                                                         │
│                Kết nối: Ổn định                         │
│                                                         │
└─────────────────────────────────────────────────────────┘
```

---

## 10. Tổng kết kiến trúc với Queue Service

```
Customer ──► Mở trang sự kiện
                │
                ▼
        ┌───────────────┐      Tải thấp      ┌─────────────┐
        │  API Gateway  │ ──────────────────►│ Booking Svc │──► Đặt vé
        │               │                    └─────────────┘
        │  Check queue  │
        │  enabled?     │      Tải cao       ┌────────────────┐
        │               │ ──────────────────►│ Queue Service  │
        └───────────────┘                    │                │
                                             │ WebSocket      │
                                             │ Redis SortedSet│
                                             │ Heartbeat      │
                                             │                │
                                             │ Đến lượt →     │
                                             │ Cấp token →    │──► Booking Svc ──► Đặt vé
                                             │                │    (validate token)
                                             └────────────────┘
```

---

## 11. So sánh với hệ thống thực tế

| Tính năng | TicketBooking (thiết kế) | Ticketmaster | Queue-it |
|-----------|--------------------------|-------------|----------|
| Virtual Queue | ✅ Redis Sorted Set | ✅ Proprietary | ✅ SaaS |
| Real-time position | ✅ WebSocket | ✅ SSE/Polling | ✅ WebSocket |
| Disconnect = Out | ✅ Heartbeat 15s | ✅ | ✅ |
| Auto-enable | ✅ Threshold-based | ✅ | ✅ Manual |
| Time-limited access | ✅ 10 min token | ✅ ~15 min | ✅ Configurable |
| Estimated wait | ✅ Slot throughput | ✅ | ✅ |
| Fairness (FIFO) | ✅ Sorted Set by time | ✅ Randomized | ✅ FIFO |

---

> 📄 Xem thêm: [System Design](system-design.md) | [Technical Flows](technical-flows.md) | [API Design](api-design.md)
