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

    subgraph GatewayLayer["API Gateway Layer — Zero Trust Token Relay"]
        GW["🚪 API Gateway<br/>(Spring Cloud Gateway - Spring Boot 4.1.1+)<br/>JWT Validation (Public Key) | Rate Limiting | Token Relay"]
    end

    subgraph InfraServices["Infrastructure Support Services"]
        CONFIG["⚙️ Config Server<br/>(Spring Cloud Config - :8888)<br/>Centralized Configuration<br/>bootstrap: true (tự nạp cấu hình chung)"]
        EUREKA["🔍 Discovery Service<br/>(Spring Cloud Eureka - :8761)<br/>Service Registry & Health<br/>(Standalone — Không phụ thuộc Config Server)"]
        OBS["📈 Observability & Tracing<br/>(Prometheus / Grafana / Jaeger)<br/>Metrics & Distributed Tracing"]
    end

    subgraph BusinessServices["Business Microservices Layer (Java 21+ / Spring Boot 4.1.1+)<br/>Mỗi service tự xác thực JWT bằng Public Key (Zero Trust)"]
        AUTH["🔐 Auth Service (:8081)<br/>JWT Issuer (Private Key)<br/>RBAC & Token Management"]
        USER["👤 User Service (:8082)<br/>Profiles & KYC"]
        CATALOG["📋 Catalog Service (:8083)<br/>Events & Ticket Classes"]
        BOOKING["🎫 Booking Service (:8084)<br/>Core Order & Seat Holding"]
        PAYMENT["💳 Payment Service (:8085)<br/>MoMo Payment Gateway"]
        QUEUE["🚦 Queue Service (:8087)<br/>Virtual Waiting Room (WebSocket)"]
        NOTIF["📧 Notification Service (:8086)<br/>Email & QR Code Generation"]
        RECOMMEND["🤖 Recommend Service (:8088)<br/>(Python / FastAPI)<br/>AI Event Recommendation"]
    end

    subgraph DataLayer["Data Layer (Physical Isolation — Database per Service)"]
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
        VNPAY["💰 MoMo Gateway"]
        SMTP["📬 SMTP Mail Server"]
    end

    WEB --> CF
    MOB --> CF
    CF --> GW

    %% Gateway forwards Bearer JWT (Token Relay) to all business services
    GW -- "Bearer JWT<br/>(Token Relay)" --> AUTH
    GW -- "Bearer JWT<br/>(Token Relay)" --> USER
    GW -- "Bearer JWT<br/>(Token Relay)" --> CATALOG
    GW -- "Bearer JWT<br/>(Token Relay)" --> BOOKING
    GW -- "Bearer JWT<br/>(Token Relay)" --> PAYMENT
    GW -- "Bearer JWT<br/>(Token Relay)" --> QUEUE
    GW -- "Bearer JWT<br/>(Token Relay)" --> RECOMMEND

    %% Config-First: Business services pull config from Config Server at startup
    AUTH -- "Pull Config<br/>(bootstrap)" --> CONFIG
    USER -- "Pull Config<br/>(bootstrap)" --> CONFIG
    CATALOG -- "Pull Config<br/>(bootstrap)" --> CONFIG
    BOOKING -- "Pull Config<br/>(bootstrap)" --> CONFIG
    PAYMENT -- "Pull Config<br/>(bootstrap)" --> CONFIG
    QUEUE -- "Pull Config<br/>(bootstrap)" --> CONFIG
    NOTIF -- "Pull Config<br/>(bootstrap)" --> CONFIG
    GW -- "Pull Config<br/>(bootstrap)" --> CONFIG

    %% All services register with Eureka
    GW --> EUREKA
    AUTH --> EUREKA
    USER --> EUREKA
    CATALOG --> EUREKA
    BOOKING --> EUREKA
    PAYMENT --> EUREKA
    QUEUE --> EUREKA
    NOTIF --> EUREKA
    CONFIG --> EUREKA

    %% Data connections
    AUTH --> PG_AUTH
    USER --> PG_USER
    CATALOG --> PG_CATALOG
    BOOKING --> PG_BOOKING
    BOOKING --> REDIS
    PAYMENT --> PG_PAYMENT
    QUEUE --> REDIS
    NOTIF --> MONGO

    %% Kafka event-driven
    PAYMENT --> KAFKA
    BOOKING --> KAFKA
    KAFKA --> BOOKING
    KAFKA --> CATALOG
    KAFKA --> NOTIF
    KAFKA_UI --> KAFKA

    %% External integrations
    PAYMENT --> MOMO
    NOTIF --> SMTP

    OBS -.- GW & AUTH & BOOKING & PAYMENT & KAFKA

    style BOOKING fill:#ff6b6b,color:#fff
    style QUEUE fill:#f39c12,color:#fff
    style KAFKA fill:#231f20,color:#fff
    style REDIS fill:#dc382d,color:#fff
    style RECOMMEND fill:#3498db,color:#fff
    style CONFIG fill:#27ae60,color:#fff
    style EUREKA fill:#8e44ad,color:#fff
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

    Customer->>GW: POST /bookings (2 vé VIP) + Bearer JWT
    Note over GW: Xác thực JWT bằng Public Key<br/>Rate Limiting check
    GW->>BS: Token Relay — Forward Bearer JWT nguyên xi

    Note over BS: Tự xác thực JWT bằng Public Key<br/>Trích xuất userId, roles từ claims

    BS->>CS: GET /ticket-classes/{id}
    CS-->>BS: available_quantity = 100

    BS->>Redis: INCRBY hold_count:event:class 2 (Atomic)
    Redis-->>BS: new_count = 52

    Note over BS: Check: 100 - 52 >= 0? ✅ (Đủ vé)

    BS->>Redis: SET seat_hold:event:class:user (TTL = 600s)
    BS->>DB: INSERT booking (status: PENDING_PAYMENT, expired_at: now+10m)
    BS->>DB: INSERT 2 tickets (status: LOCKED)

    BS-->>GW: 201 Created (paymentUrl, expiredAt)
    GW-->>Customer: Trả về trang thanh toán Momo

    Note over Redis: ⏰ 10 phút sau...
    
    alt Khách không thanh toán (TTL hết hạn)
        Redis-->>BS: Keyspace notification expired
        BS->>DB: UPDATE booking → CANCELLED
        BS->>DB: UPDATE tickets → CANCELLED
        BS->>Redis: DECRBY hold_count:event:class 2 (Trả vé về kho)
    end
```

---

## 3. Zero Trust Security Flow (Token Relay & Asymmetric Keys)

```mermaid
sequenceDiagram
    actor Client
    participant GW as API Gateway<br/>(Public Key only)
    participant Auth as Auth Service<br/>(Private Key + Public Key)
    participant Svc as Any Business Service<br/>(Public Key only)

    Note over Auth: Nơi DUY NHẤT giữ Private Key<br/>để ký (sign) JWT

    %% Login Flow
    rect rgb(230, 245, 255)
        Note over Client,Auth: 🔑 Authentication Flow (Đăng nhập)
        Client->>GW: POST /auth/login (email, password)
        GW->>Auth: Forward request (public endpoint — no JWT needed)
        Auth->>Auth: Xác thực credentials<br/>Ký JWT bằng Private Key (RSA)
        Auth-->>GW: 200 OK { accessToken, refreshToken }
        GW-->>Client: Trả về tokens
    end

    %% Authenticated Request Flow
    rect rgb(255, 245, 230)
        Note over Client,Svc: 🛡️ Zero Trust Request Flow (Token Relay)
        Client->>GW: GET /bookings + Authorization: Bearer {JWT}
        GW->>GW: Xác thực JWT bằng Public Key<br/>(Kiểm tra chữ ký + hết hạn chưa)
        Note over GW: ✅ JWT hợp lệ → Token Relay<br/>Chuyển tiếp nguyên xi Bearer JWT<br/>❌ KHÔNG bóc tách thành X-User-Id header
        GW->>Svc: Forward Bearer JWT nguyên xi
        Svc->>Svc: TỰ xác thực JWT bằng Public Key<br/>Trích xuất userId, roles từ claims<br/>Kiểm tra @PreAuthorize
        Svc-->>GW: 200 OK { data }
        GW-->>Client: Response
    end

    %% Internal Spoofing Prevention
    rect rgb(255, 230, 230)
        Note over Client,Svc: 🚫 Tại sao KHÔNG dùng X-User-Id header?
        Note over GW,Svc: Nếu dùng header X-User-Id:<br/>Kẻ tấn công bypass Gateway → gửi thẳng<br/>X-User-Id: admin_uuid → chiếm quyền!<br/><br/>Với Token Relay:<br/>Không có Private Key → Không thể giả JWT<br/>→ Zero Trust: TRIỆT TIÊU header spoofing
    end
```

---

## 4. Config-First Boot Sequence (Thứ Tự Khởi Động Hệ Thống)

```mermaid
sequenceDiagram
    participant Eureka as Discovery Service<br/>(:8761 — Standalone)
    participant Config as Config Server<br/>(:8888 — bootstrap: true)
    participant Auth as Auth Service<br/>(:8081)
    participant GW as API Gateway<br/>(:8080)
    participant Others as Other Services

    Note over Eureka: 🟢 KHỞI ĐỘNG ĐẦU TIÊN<br/>Hoàn toàn độc lập<br/>Không cần Config Server

    rect rgb(230, 255, 230)
        Note over Eureka,Config: Bước 1: Infrastructure Services
        Eureka->>Eureka: Start với config nội bộ<br/>application.yaml riêng
        Config->>Config: Start với bootstrap: true<br/>Tự nạp configurations/application.yaml<br/>cho chính mình (Virtual Threads, Eureka, Actuator...)
        Config->>Eureka: Đăng ký vào Service Registry
    end

    rect rgb(230, 245, 255)
        Note over Auth,Others: Bước 2: Business Services (Sau khi Config Server sẵn sàng)
        Auth->>Config: Pull config (auth-service.yaml + application.yaml)
        Config-->>Auth: DB connection, JWT config, Eureka, Virtual Threads...
        Auth->>Eureka: Đăng ký vào Service Registry

        GW->>Config: Pull config (gateway.yaml + application.yaml)
        Config-->>GW: Routes, Rate Limit, JWT Public Key config...
        GW->>Eureka: Đăng ký vào Service Registry

        Others->>Config: Pull config ({service-name}.yaml + application.yaml)
        Config-->>Others: Service-specific + shared configuration
        Others->>Eureka: Đăng ký vào Service Registry
    end

    Note over Eureka,Others: ✅ Hệ thống sẵn sàng phục vụ request
```

---

## 5. Payment & Event-Driven Flow (Kafka Choreography)

```mermaid
sequenceDiagram
    actor Customer
    participant PS as Payment Service
    participant MoMo as Cổng MoMo Payment Gateway
    participant Kafka as Kafka (:9092)
    participant BS as Booking Service
    participant CS as Catalog Service
    participant NS as Notification Service

    Customer->>PS: POST /payments/initiate
    PS-->>Customer: paymentUrl (Redirect sang MoMo)

    Customer->>MoMo: Thanh toán online thành công
    MoMo->>PS: IPN Callback (server-to-server)

    PS->>PS: Verify signature HMAC-SHA256 & Save transaction (SUCCESS)
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

## 6. Saga Compensation Flow (Hoàn tiền khi lỗi hệ thống)

```mermaid
stateDiagram-v2
    [*] --> PendingPayment: Customer giữ chỗ thành công

    PendingPayment --> Processing: payment.success received
    PendingPayment --> Cancelled: Hết hạn 10 phút / User tự hủy

    Processing --> Completed: Tickets issued thành công (Happy path)
    Processing --> Compensating: ❌ Lỗi khi sinh vé (DB lỗi/Timeout)

    Compensating --> Refunded: MoMo Refund thành công

    Completed --> [*]
    Cancelled --> [*]
    Refunded --> [*]

    note right of Compensating
        1. Booking Service bắn event booking.refund-requested
        2. Payment Service gọi API hoàn tiền của MoMo
        3. Payment Service cập nhật transaction = REFUNDED
        4. Booking Service cập nhật booking = REFUNDED & nhả vé
    end note
```

---

## 7. Database Relationships (ER Diagram)

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

## 8. Deployment Architecture (Docker Compose — 5 Postgres Độc Lập)

```mermaid
flowchart LR
    subgraph HostEnvironment["Docker Host (Localhost / VPS)"]
        subgraph InfraLayer["Infrastructure Layer"]
            EUREKA["🔍 Discovery<br/>:8761<br/>(Standalone)"]
            CONFIG["⚙️ Config Server<br/>:8888<br/>(bootstrap: true)"]
        end

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

        subgraph ApplicationServices["Application Microservices (Zero Trust — JWT Verification)"]
            GW["🚪 Gateway<br/>:8080<br/>(Token Relay)"]
            AUTH["🔐 Auth<br/>:8081<br/>(JWT Issuer)"]
            USER["👤 User<br/>:8082"]
            CATALOG["📋 Catalog<br/>:8083"]
            BOOKING["🎫 Booking<br/>:8084"]
            PAYMENT["💳 Payment<br/>:8085"]
            NOTIF["📧 Notification<br/>:8086"]
            QUEUE["🚦 Queue<br/>:8087"]
            REC["🤖 Recommend<br/>:8088"]
        end
    end

    %% Config-First: services pull config
    AUTH & USER & CATALOG & BOOKING & PAYMENT & QUEUE & NOTIF & GW --> CONFIG
    CONFIG --> EUREKA

    %% Gateway routes with Token Relay
    GW -- "Token Relay" --> AUTH & USER & CATALOG & BOOKING & PAYMENT & QUEUE & REC

    %% Data layer
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
    style CONFIG fill:#27ae60,color:#fff
    style EUREKA fill:#8e44ad,color:#fff
```

---

## 9. Use Case Diagram Tổng Quan

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

    C5 -.-|"trigger"| S1
    C4 -.-|"trigger"| S2
    C6 -.-|"trigger"| S3
    C6 -.-|"on failure"| S4
```

---

> 📄 Xem tiếp:
> * [Thiết kế hệ thống tổng quan](system-design.md)
> * [Thiết kế cơ sở dữ liệu chi tiết](database-schema.md)
> * [Đặc tả RESTful API](api-design.md)
> * [Luồng kỹ thuật chi tiết](technical-flows.md)
> * [Thiết kế phòng chờ ảo](virtual-waiting-room.md)
