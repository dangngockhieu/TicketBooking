# 📊 Architecture Diagrams — TicketBooking

> Sơ đồ kiến trúc hệ thống sử dụng Mermaid

---

## 1. System Architecture Overview (Chuẩn Enterprise)

```mermaid
flowchart TD
    subgraph Clients["Client Layer"]
        WEB["🌐 Web App<br/>(React / Next.js)"]
        MOB["📱 Mobile App<br/>(Flutter / React Native)"]
    end

    subgraph Edge["Edge / Reverse Proxy Layer"]
        CF["☁️ Reverse Proxy<br/>(Cloudflare / Nginx)<br/>DDoS Protection | SSL Termination | Caching"]
    end

    subgraph GatewayLayer["API Gateway Layer"]
        GW["🚪 API Gateway<br/>(Spring Cloud Gateway - Spring Boot 4.1.1+)<br/>JWT Validation | Rate Limiting | Routing"]
    end

    subgraph InfraServices["Infrastructure Support Services"]
        EUREKA["🔍 Discovery Service<br/>(Spring Cloud Eureka)<br/>Service Registry & Health"]
        CONFIG["⚙️ Config Server<br/>(Spring Cloud Config)<br/>Centralized Git Configuration"]
        OBS["📈 Observability & Tracing<br/>(Prometheus / Grafana / Jaeger)<br/>Metrics & Distributed Tracing"]
    end

    subgraph BusinessServices["Business Microservices Layer (Java 21+ / Spring Boot 4.1.1+)"]
        AUTH["🔐 Auth Service<br/>(Spring Boot 4.1.1+)<br/>JWT & RBAC"]
        USER["👤 User Service<br/>(Spring Boot 4.1.1+)<br/>Profiles & KYC"]
        CATALOG["📋 Catalog Service<br/>(Spring Boot 4.1.1+)<br/>Events & Ticket Classes"]
        BOOKING["🎫 Booking Service<br/>(Spring Boot 4.1.1+)<br/>Core Order & Seat Holding"]
        PAYMENT["💳 Payment Service<br/>(Spring Boot 4.1.1+)<br/>VNPay Sandbox"]
        QUEUE["🚦 Queue Service<br/>(Spring Boot 4.1.1+ WebSocket)<br/>Virtual Waiting Room"]
        NOTIF["📧 Notification Service<br/>(Spring Boot 4.1.1+)<br/>Email & QR Code Generation"]
        RECOMMEND["🤖 Recommend Service<br/>(Python / FastAPI)<br/>AI Event Recommendation"]
    end

    subgraph DataLayer["Data Layer (Physical Isolation)"]
        PG_AUTH[("🐘 postgres-auth<br/>:5433")]
        PG_USER[("🐘 postgres-user<br/>:5434")]
        PG_CATALOG[("🐘 postgres-catalog<br/>:5435")]
        PG_BOOKING[("🐘 postgres-booking<br/>:5436")]
        PG_PAYMENT[("🐘 postgres-payment<br/>:5437")]
        REDIS[("⚡ Redis<br/>:6379<br/>Lock & Queue")]
        MONGO[("🍃 MongoDB<br/>:27017<br/>Notification Logs")]
    end

    subgraph Messaging["Event-Driven Backbone"]
        KAFKA["📨 Apache Kafka<br/>(KRaft Mode - :9092)<br/>Event-Driven Architecture"]
        KAFKA_UI["🖥️ Kafka UI<br/>(:8090)"]
    end

    subgraph External["External Services"]
        VNPAY["💰 VNPay Gateway"]
        SMTP["📬 SMTP Mail Server"]
    end

    WEB --> CF
    MOB --> CF
    CF --> GW

    GW --> EUREKA
    GW --> AUTH
    GW --> USER
    GW --> CATALOG
    GW --> BOOKING
    GW --> PAYMENT
    GW --> QUEUE
    GW --> RECOMMEND

    AUTH --> EUREKA
    USER --> EUREKA
    CATALOG --> EUREKA
    BOOKING --> EUREKA
    PAYMENT --> EUREKA
    QUEUE --> EUREKA

    AUTH --> CONFIG
    USER --> CONFIG
    CATALOG --> CONFIG
    BOOKING --> CONFIG
    PAYMENT --> CONFIG

    AUTH --> PG_AUTH
    USER --> PG_USER
    CATALOG --> PG_CATALOG
    BOOKING --> PG_BOOKING
    BOOKING --> REDIS
    PAYMENT --> PG_PAYMENT
    QUEUE --> REDIS
    NOTIF --> MONGO

    PAYMENT --> KAFKA
    BOOKING --> KAFKA
    KAFKA --> BOOKING
    KAFKA --> CATALOG
    KAFKA --> NOTIF
    KAFKA_UI --> KAFKA

    PAYMENT --> VNPAY
    NOTIF --> SMTP

    OBS -.-> GW & AUTH & BOOKING & PAYMENT & KAFKA

    style BOOKING fill:#ff6b6b,color:#fff
    style QUEUE fill:#f39c12,color:#fff
    style KAFKA fill:#231f20,color:#fff
    style REDIS fill:#dc382d,color:#fff
    style RECOMMEND fill:#3498db,color:#fff
```

---

## 2. Seat Hold Flow (Chống Overbooking với Redis Atomic)

```mermaid
sequenceDiagram
    actor Customer
    participant GW as API Gateway
    participant BS as Booking Service
    participant CS as Catalog Service
    participant Redis as Redis (:6379)
    participant DB as postgres-booking (:5436)

    Customer->>GW: POST /bookings (2 vé VIP)
    GW->>BS: Forward request

    BS->>CS: GET /ticket-classes/{id}
    CS-->>BS: available_quantity = 100

    BS->>Redis: INCRBY hold_count:event:class 2 (Atomic)
    Redis-->>BS: new_count = 52

    Note over BS: Check: 100 - 52 >= 0? ✅ (Đủ vé)

    BS->>Redis: SET seat_hold:event:class:user (TTL = 600s)
    BS->>DB: INSERT booking (status: PENDING_PAYMENT, expired_at: now+10m)
    BS->>DB: INSERT 2 tickets (status: LOCKED)

    BS-->>GW: 201 Created (paymentUrl, expiredAt)
    GW-->>Customer: Trả về trang thanh toán VNPay

    Note over Redis: ⏰ 10 phút sau...
    
    alt Khách không thanh toán (TTL hết hạn)
        Redis-->>BS: Keyspace notification expired
        BS->>DB: UPDATE booking → CANCELLED
        BS->>DB: UPDATE tickets → CANCELLED
        BS->>Redis: DECRBY hold_count:event:class 2 (Trả vé về kho)
    end
```

---

## 3. Payment & Event-Driven Flow (Kafka Choreography)

```mermaid
sequenceDiagram
    actor Customer
    participant PS as Payment Service
    participant VNPay as Cổng VNPay Sandbox
    participant Kafka as Kafka (:9092)
    participant BS as Booking Service
    participant CS as Catalog Service
    participant NS as Notification Service

    Customer->>PS: POST /payments/initiate
    PS-->>Customer: paymentUrl (Redirect sang VNPay)

    Customer->>VNPay: Thanh toán online thành công
    VNPay->>PS: IPN Callback (Instant Payment Notification)

    PS->>PS: Verify checksum HMAC-SHA512 & Save transaction (SUCCESS)
    PS->>Kafka: Publish event payment.success

    Kafka->>BS: Consume payment.success
    BS->>BS: Idempotent check: booking → PAID, tickets → ISSUED
    BS->>BS: Xóa Redis hold key
    BS->>Kafka: Publish event tickets.generated

    par Đồng bộ song song qua Kafka
        Kafka->>CS: Consume tickets.generated
        CS->>CS: Trừ vĩnh viễn available_quantity trong DB
    and
        Kafka->>NS: Consume tickets.generated
        NS->>NS: Sinh mã QR E-Ticket & Gửi email cho khách
        NS->>NS: Lưu lịch sử vào MongoDB (:27017)
    end
```

---

## 4. Saga Compensation Flow (Hoàn tiền khi lỗi hệ thống)

```mermaid
stateDiagram-v2
    [*] --> PendingPayment: Customer giữ chỗ thành công

    PendingPayment --> Processing: payment.success received
    PendingPayment --> Cancelled: Hết hạn 10 phút / User tự hủy

    Processing --> Completed: Tickets issued thành công (Happy path)
    Processing --> Compensating: ❌ Lỗi khi sinh vé (DB lỗi/Timeout)

    Compensating --> Refunded: VNPay Refund thành công

    Completed --> [*]
    Cancelled --> [*]
    Refunded --> [*]

    note right of Compensating
        1. Booking Service bắn event booking.refund-requested
        2. Payment Service gọi API hoàn tiền của VNPay
        3. Payment Service cập nhật transaction = REFUNDED
        4. Booking Service cập nhật booking = REFUNDED & nhả vé
    end note
```

---

## 5. Database Relationships (ER Diagram)

```mermaid
erDiagram
    accounts ||--o| profiles : "1:1 (soft key account_id)"
    accounts ||--o{ events : "organizer tạo"

    categories ||--o{ events : "phân loại"
    events ||--o{ ticket_classes : "có nhiều hạng vé"

    accounts ||--o{ bookings : "customer đặt vé"
    events ||--o{ bookings : "đặt cho sự kiện"
    bookings ||--|{ tickets : "chứa từng vé cụ thể"
    ticket_classes ||--o{ tickets : "loại vé (soft key)"

    bookings ||--o| transactions : "thanh toán bằng"

    accounts {
        uuid id PK
        varchar email UK
        varchar password_hash
        enum role
        enum status
    }

    profiles {
        uuid id PK
        uuid account_id FK
        varchar full_name
        varchar phone_number
        jsonb metadata
    }

    categories {
        uuid id PK
        varchar name UK
        varchar slug UK
    }

    events {
        uuid id PK
        uuid category_id FK
        uuid organizer_id
        varchar title
        timestamp start_time
        timestamp end_time
        enum status
    }

    ticket_classes {
        uuid id PK
        uuid event_id FK
        varchar name
        decimal price
        int total_quantity
        int available_quantity
    }

    bookings {
        uuid id PK
        uuid customer_id
        uuid event_id
        decimal total_amount
        int quantity
        enum status
        timestamp expired_at
    }

    tickets {
        uuid id PK
        uuid booking_id FK
        uuid ticket_class_id
        varchar qr_code_data UK
        enum status
        timestamp checked_in_at
    }

    transactions {
        uuid id PK
        uuid booking_id
        decimal amount
        varchar payment_method
        varchar gateway_trans_id UK
        enum status
    }
```

---

## 6. Deployment Architecture (Docker Compose Mới — 5 Postgres Độc Lập)

```mermaid
flowchart LR
    subgraph HostEnvironment["Docker Host (Localhost / VPS)"]
        subgraph Databases["5 Cụm Database Độc Lập (Physical Isolation)"]
            PG_A["🐘 postgres-auth<br/>Host: 5433"]
            PG_U["🐘 postgres-user<br/>Host: 5434"]
            PG_C["🐘 postgres-catalog<br/>Host: 5435"]
            PG_B["🐘 postgres-booking<br/>Host: 5436"]
            PG_P["🐘 postgres-payment<br/>Host: 5437"]
        end

        subgraph StorageQueue["Storage & Message Broker"]
            REDIS["⚡ Redis 7<br/>Host: 6379"]
            MONGO["🍃 MongoDB 7<br/>Host: 27017"]
            KAFKA["📨 Kafka (KRaft)<br/>Host: 9092"]
            KAFKA_UI["🖥️ Kafka UI<br/>Host: 8090"]
        end

        subgraph ApplicationServices["Application Microservices"]
            GW["🚪 Gateway<br/>:8080"]
            AUTH["🔐 Auth<br/>:8081"]
            USER["👤 User<br/>:8082"]
            CATALOG["📋 Catalog<br/>:8083"]
            BOOKING["🎫 Booking<br/>:8084"]
            PAYMENT["💳 Payment<br/>:8085"]
            NOTIF["📧 Notification<br/>:8086"]
            QUEUE["🚦 Queue<br/>:8087"]
            REC["🤖 Recommend<br/>:8088"]
        end
    end

    GW --> AUTH & USER & CATALOG & BOOKING & PAYMENT & QUEUE & REC
    AUTH --> PG_A
    USER --> PG_U
    CATALOG --> PG_C
    BOOKING --> PG_B & REDIS
    PAYMENT --> PG_P
    QUEUE --> REDIS
    NOTIF --> MONGO
    PAYMENT & BOOKING --> KAFKA
    KAFKA --> BOOKING & CATALOG & NOTIF
    KAFKA_UI --> KAFKA

    style BOOKING fill:#ff6b6b,color:#fff
    style QUEUE fill:#f39c12,color:#fff
    style KAFKA fill:#231f20,color:#fff
    style REDIS fill:#dc382d,color:#fff
```

---

## 7. Use Case Diagram Tổng Quan

```mermaid
flowchart TD
    subgraph Actors["Tác Nhân (Actors)"]
        ADMIN["👨‍💼 Admin"]
        ORG["🏢 Organizer"]
        CUST["👤 Customer"]
        SYS["⚙️ System Background"]
    end

    subgraph UC_Admin["Admin Use Cases"]
        A1["Duyệt/Khóa Organizer"]
        A2["Quản lý danh mục sự kiện"]
        A3["Giám sát hệ thống (Dashboard)"]
    end

    subgraph UC_Org["Organizer Use Cases"]
        O1["Đăng tải & Publish sự kiện"]
        O2["Cấu hình hạng vé (VVIP, VIP, GA)"]
        O3["Quét mã QR Check-in cổng"]
        O4["Xem báo cáo doanh thu & lấp đầy"]
    end

    subgraph UC_Cust["Customer Use Cases"]
        C1["Đăng ký / Đăng nhập JWT"]
        C2["Tìm kiếm & Lọc sự kiện"]
        C3["Nhận gợi ý sự kiện (AI Recommend)"]
        C4["Xếp hàng phòng chờ ảo (Queue)"]
        C5["Giữ chỗ tạm thời (Seat Hold 10m)"]
        C6["Thanh toán VNPay Sandbox"]
        C7["Nhận vé E-Ticket qua email"]
    end

    subgraph UC_Sys["System Use Cases"]
        S1["Tự động nhả vé khi quá 10 phút"]
        S2["Tự động kick khỏi queue khi mất mạng (15s)"]
        S3["Gửi email vé điện tử tự động (Kafka)"]
        S4["Saga Rollback: Tự động hoàn tiền khi lỗi"]
    end

    ADMIN --> A1 & A2 & A3
    ORG --> O1 & O2 & O3 & O4
    CUST --> C1 & C2 & C3 & C4 & C5 & C6 & C7
    SYS --> S1 & S2 & S3 & S4

    C5 -.->|"trigger"| S1
    C4 -.->|"trigger"| S2
    C6 -.->|"trigger"| S3
    C6 -.->|"on failure"| S4
```

---

> 📄 Xem tiếp:
> * [Thiết kế hệ thống tổng quan](system-design.md)
> * [Thiết kế cơ sở dữ liệu chi tiết](database-schema.md)
> * [Đặc tả RESTful API](api-design.md)
> * [Luồng kỹ thuật chi tiết](technical-flows.md)
> * [Thiết kế phòng chờ ảo](virtual-waiting-room.md)
