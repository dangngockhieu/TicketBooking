# 📊 Architecture Diagrams — TicketBooking

> Sơ đồ kiến trúc hệ thống sử dụng Mermaid

---

## 1. System Architecture Overview

```mermaid
flowchart TD
    subgraph Clients
        WEB["🌐 Web App<br/>(React/Next.js)"]
        MOB["📱 Mobile App<br/>(Organizer Check-in)"]
    end

    subgraph Gateway["API Gateway Layer"]
        GW["🚪 API Gateway<br/>(Spring Cloud Gateway)<br/>Rate Limiting | JWT Validation | Routing"]
    end

    subgraph Services["Microservices Layer"]
        AUTH["🔐 Auth Service<br/>JWT | RBAC"]
        USER["👤 User Service<br/>Profiles"]
        CATALOG["📋 Catalog Service<br/>Events | Ticket Classes"]
        BOOKING["🎫 Booking Service<br/>Orders | Seat Hold"]
        PAYMENT["💳 Payment Service<br/>VNPay Integration"]
        NOTIF["📧 Notification Service<br/>Email | QR Code"]
    end

    subgraph Data["Data Layer"]
        PG_AUTH[("🐘 PostgreSQL<br/>Auth DB")]
        PG_USER[("🐘 PostgreSQL<br/>User DB")]
        PG_CATALOG[("🐘 PostgreSQL<br/>Catalog DB")]
        PG_BOOKING[("🐘 PostgreSQL<br/>Booking DB")]
        PG_PAYMENT[("🐘 PostgreSQL<br/>Payment DB")]
        MONGO[("🍃 MongoDB<br/>Notification Logs")]
        REDIS[("⚡ Redis<br/>Seat Hold & Cache")]
    end

    subgraph Messaging["Event Bus"]
        KAFKA["📨 Apache Kafka<br/>Event-Driven Communication"]
    end

    subgraph External["External Services"]
        VNPAY["💰 VNPay Gateway"]
        SMTP["📬 SMTP Server"]
    end

    WEB --> GW
    MOB --> GW

    GW --> AUTH
    GW --> USER
    GW --> CATALOG
    GW --> BOOKING
    GW --> PAYMENT

    AUTH --> PG_AUTH
    USER --> PG_USER
    CATALOG --> PG_CATALOG
    BOOKING --> PG_BOOKING
    BOOKING --> REDIS
    PAYMENT --> PG_PAYMENT
    NOTIF --> MONGO

    PAYMENT --> KAFKA
    BOOKING --> KAFKA
    KAFKA --> BOOKING
    KAFKA --> CATALOG
    KAFKA --> NOTIF

    PAYMENT --> VNPAY
    NOTIF --> SMTP

    style BOOKING fill:#ff6b6b,color:#fff
    style KAFKA fill:#231f20,color:#fff
    style REDIS fill:#dc382d,color:#fff
```

---

## 2. Seat Hold Flow (Chống Overbooking)

```mermaid
sequenceDiagram
    actor Customer
    participant GW as API Gateway
    participant BS as Booking Service
    participant CS as Catalog Service
    participant Redis
    participant DB as Booking DB

    Customer->>GW: POST /bookings (2 vé VIP)
    GW->>BS: Forward request

    BS->>CS: GET /ticket-classes/{id}
    CS-->>BS: available_quantity = 100

    BS->>Redis: INCRBY hold_count:event:class 2
    Redis-->>BS: new_count = 52

    Note over BS: Check: 100 - 52 >= 0? ✅

    BS->>Redis: SET seat_hold:event:class:user (TTL=600s)
    BS->>DB: INSERT booking (PENDING_PAYMENT)
    BS->>DB: INSERT 2 tickets (LOCKED)

    BS-->>GW: 201 Created
    GW-->>Customer: booking response + paymentUrl

    Note over Redis: ⏰ 10 phút sau...

    alt Không thanh toán (TTL hết hạn)
        Redis-->>BS: Key expired notification
        BS->>DB: UPDATE booking → CANCELLED
        BS->>DB: UPDATE tickets → CANCELLED
        BS->>Redis: DECRBY hold_count:event:class 2
    end
```

---

## 3. Payment & Event-Driven Flow

```mermaid
sequenceDiagram
    actor Customer
    participant PS as Payment Service
    participant VNPay
    participant Kafka
    participant BS as Booking Service
    participant CS as Catalog Service
    participant NS as Notification Service

    Customer->>PS: POST /payments/initiate
    PS-->>Customer: paymentUrl (VNPay redirect)

    Customer->>VNPay: Thanh toán
    VNPay->>PS: IPN Callback (success)

    PS->>PS: Verify checksum & Save transaction
    PS->>Kafka: Publish payment.success

    Kafka->>BS: Consume payment.success
    BS->>BS: booking → PAID, tickets → ISSUED
    BS->>BS: Delete Redis hold keys
    BS->>Kafka: Publish tickets.generated

    par Parallel Processing
        Kafka->>CS: Consume tickets.generated
        CS->>CS: DECRBY available_quantity
    and
        Kafka->>NS: Consume tickets.generated
        NS->>NS: Generate QR email & Send
    end
```

---

## 4. Saga Compensation Flow (Hoàn tiền)

```mermaid
stateDiagram-v2
    [*] --> PendingPayment: Customer đặt vé

    PendingPayment --> Processing: payment.success received
    PendingPayment --> Cancelled: Hết 10 phút / User hủy

    Processing --> Completed: Tickets issued thành công
    Processing --> Compensating: ❌ Lỗi khi issue tickets

    Compensating --> Refunded: Hoàn tiền thành công

    Completed --> [*]
    Cancelled --> [*]
    Refunded --> [*]

    note right of Compensating
        1. Booking Service bắn booking.refund-requested
        2. Payment Service gọi VNPay Refund API
        3. Payment Service bắn payment.refunded
        4. Booking Service cập nhật REFUNDED
    end note
```

---

## 5. Database Relationships (ER Diagram)

```mermaid
erDiagram
    accounts ||--o| profiles : "1:1 (soft key)"
    accounts ||--o{ events : "organizer creates"

    categories ||--o{ events : "has many"
    events ||--o{ ticket_classes : "has many"

    accounts ||--o{ bookings : "customer books"
    events ||--o{ bookings : "booked for"
    bookings ||--|{ tickets : "contains"
    ticket_classes ||--o{ tickets : "type of (soft key)"

    bookings ||--o| transactions : "paid by"

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
    }

    categories {
        uuid id PK
        varchar name
        varchar slug
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
        enum status
        timestamp expired_at
    }

    tickets {
        uuid id PK
        uuid booking_id FK
        uuid ticket_class_id
        varchar qr_code_data UK
        enum status
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

## 6. Deployment Architecture (Docker Compose)

```mermaid
flowchart LR
    subgraph Docker["Docker Compose Environment"]
        subgraph Infra["Infrastructure"]
            PG["🐘 PostgreSQL<br/>:5432"]
            MONGO["🍃 MongoDB<br/>:27017"]
            REDIS["⚡ Redis<br/>:6379"]
            ZK["🦓 Zookeeper<br/>:2181"]
            KAFKA["📨 Kafka<br/>:9092"]
        end

        subgraph Apps["Application Services"]
            GW["🚪 Gateway<br/>:8080"]
            A["🔐 Auth<br/>:8081"]
            U["👤 User<br/>:8082"]
            C["📋 Catalog<br/>:8083"]
            B["🎫 Booking<br/>:8084"]
            P["💳 Payment<br/>:8085"]
            N["📧 Notification<br/>:8086"]
        end
    end

    GW --> A & U & C & B & P
    A & U & C & B & P --> PG
    B --> REDIS
    N --> MONGO
    P & B --> KAFKA
    KAFKA --> B & C & N
    ZK --> KAFKA

    style B fill:#ff6b6b,color:#fff
    style KAFKA fill:#231f20,color:#fff
```

---

## 7. Use Case Diagram

```mermaid
flowchart TD
    subgraph Actors
        ADMIN["👨‍💼 Admin"]
        ORG["🏢 Organizer"]
        CUST["👤 Customer"]
        SYS["⚙️ System"]
    end

    subgraph UC_Admin["Admin Use Cases"]
        A1["Quản lý Organizer<br/>(Duyệt/Khóa)"]
        A2["Quản lý danh mục"]
        A3["Giám sát hệ thống"]
    end

    subgraph UC_Org["Organizer Use Cases"]
        O1["Quản lý sự kiện"]
        O2["Quản lý hạng vé"]
        O3["Check-in QR"]
        O4["Xem báo cáo doanh thu"]
    end

    subgraph UC_Cust["Customer Use Cases"]
        C1["Đăng ký / Đăng nhập"]
        C2["Tìm kiếm sự kiện"]
        C3["Xem tình trạng vé"]
        C4["Đặt vé (Seat Hold)"]
        C5["Thanh toán"]
        C6["Xem lịch sử"]
        C7["Nhận E-Ticket"]
    end

    subgraph UC_Sys["System Background"]
        S1["Auto-Release Seat"]
        S2["Gửi Email QR"]
        S3["Saga Rollback"]
    end

    ADMIN --> A1 & A2 & A3
    ORG --> O1 & O2 & O3 & O4
    CUST --> C1 & C2 & C3 & C4 & C5 & C6 & C7
    SYS --> S1 & S2 & S3

    C4 -.->|"triggers"| S1
    C5 -.->|"triggers"| S2
    C5 -.->|"on failure"| S3
```

---

> 📄 Xem thêm: [System Design](system-design.md) | [Database Schema](database-schema.md) | [API Design](api-design.md) | [Technical Flows](technical-flows.md)
