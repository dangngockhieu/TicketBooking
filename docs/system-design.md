# 📐 Thiết Kế Hệ Thống — TicketBooking

> Tài liệu thiết kế tổng quan cho Hệ thống Đặt vé Sự kiện phân tán (Distributed Event Ticketing System)

---

## 1. Tổng quan dự án

### 1.1. Mục tiêu

**TicketBooking** là nền tảng thương mại điện tử chuyên biệt cho việc phân phối vé sự kiện. Dự án được thiết kế theo kiến trúc **Microservices** và **Event-Driven Architecture (EDA)** nhằm giải quyết triệt để các thách thức lớn:

| Thách thức | Giải pháp kỹ thuật |
| :--- | :--- |
| **Burst Traffic** (Hàng vạn người truy cập đồng thời khi mở bán) | **Virtual Waiting Room** (Queue Service) dùng WebSocket + Redis Sorted Set |
| **Overbooking** (Bán vượt quá số lượng vé phát hành) | **Atomic Seat Hold** dùng Redis `INCRBY` / Lua Script (giữ chỗ 10 phút) |
| **Eventual Consistency** (Nhất quán dữ liệu phân tán) | **Apache Kafka** (KRaft mode) + **Saga Pattern** (Rollback compensation) |
| **Single Point of Failure** (Sự cố lan truyền) | **Database per Service** với 5 container PostgreSQL độc lập, Spring Cloud Gateway |
| **Khả năng mở rộng & Quản trị** | **Spring Cloud Config** + **Eureka Discovery** + **Prometheus/Grafana** |

### 1.2. Mô hình kinh doanh

```
B2B2C (Mô hình đóng)

   ┌──────────┐     Kiểm duyệt    ┌─────────────┐     Mua vé      ┌────────────┐
   │  Admin   │──────────────────►│  Organizer  │◄────────────────│  Customer  │
   │ (Nội bộ) │   Cấp tài khoản   │(Ban tổ chức)│     Đặt vé      │(Khách hàng)│
   └──────────┘                   └─────────────┘                 └────────────┘
                                    Đăng sự kiện                   Đăng ký tự do
```

* **Admin:** Kiểm duyệt pháp lý và cấp tài khoản cho Organizer ➔ Đảm bảo an toàn, chống lừa đảo bán vé giả.
* **Organizer:** Đăng tải sự kiện, phân hạng vé (VVIP, VIP, GA), quản lý check-in cổng bằng QR Code, xem báo cáo doanh thu.
* **Customer:** Đăng ký tự do, tìm kiếm sự kiện, xếp hàng phòng chờ ảo, giữ chỗ 10 phút, thanh toán online và nhận vé QR qua email.

---

## 2. Phân tích Actor và Use Case Cốt Lõi

### 2.1. Customer (Khách hàng)
* **UC-C1: Đăng ký / Đăng nhập:** Tạo tài khoản, đăng nhập nhận JWT Access & Refresh Token.
* **UC-C2: Tìm kiếm & Lọc sự kiện:** Tìm theo Category (Âm nhạc, Thể thao...), thời gian, địa điểm, từ khóa.
* **UC-C3: Gợi ý sự kiện thông minh:** Nhận danh sách sự kiện gợi ý từ **Recommend Service (Python)** dựa trên sở thích và lịch sử.
* **UC-C4: Xếp hàng phòng chờ ảo (Waiting Room):** Khi sự kiện quá tải, tự động vào hàng chờ với số thứ tự và thời gian chờ ước tính theo thời gian thực (WebSocket).
* **UC-C5: Giữ chỗ tạm thời (Seat Holding):** Khóa số vé mong muốn trong **10 phút** để thanh toán mà không lo bị người khác cướp vé.
* **UC-C6: Thanh toán Online:** Thanh toán qua cổng VNPay Sandbox (thẻ ATM, QR Pay).
* **UC-C7: Nhận & Quản lý vé điện tử:** Nhận mã QR duy nhất qua email; xem lịch sử đặt vé trong hồ sơ cá nhân.

### 2.2. Organizer (Ban tổ chức)
* **UC-O1: Quản lý sự kiện:** Tạo sự kiện (DRAFT), đăng banner, cấu hình địa điểm, thời gian mở/đóng bán vé, Publish sự kiện.
* **UC-O2: Quản lý hạng vé:** Định nghĩa hạng vé (VVIP, VIP, GA), giá tiền và giới hạn số lượng phát hành.
* **UC-O3: Kiểm soát vé (Check-in QR):** Dùng ứng dụng quét mã QR tại cổng sự kiện để đối chiếu và xác nhận vào cổng (chặn quét trùng).
* **UC-O4: Báo cáo & Thống kê:** Xem dashboard doanh thu thực tế, số vé bán ra và tỷ lệ lấp đầy.

### 2.3. Admin (Quản trị viên)
* **UC-A1: Quản lý Organizer:** Phê duyệt/khóa tài khoản Organizer sau khi kiểm tra giấy phép tổ chức.
* **UC-A2: Quản lý danh mục:** Quản lý các Category sự kiện.
* **UC-A3: Giám sát hệ thống:** Theo dõi dashboard Grafana về tải hệ thống, lượng request/giây, hàng chờ.

### 2.4. System Background (Tiến trình tự động)
* **UC-S1: Auto-Release Seat:** Tự động hủy đơn hàng và nhả vé về kho nếu người dùng không thanh toán sau 10 phút.
* **UC-S2: Heartbeat Monitor:** Tự động loại người dùng khỏi hàng chờ nếu mất kết nối WebSocket quá 15 giây.
* **UC-S3: Event-driven Notification:** Hứng Kafka event thanh toán thành công để sinh vé QR và gửi email HTML tự động.
* **UC-S4: Saga Compensation:** Tự động hoàn tiền qua VNPay nếu lỗi hệ thống xảy ra sau khi khách đã bị trừ tiền.

---

## 3. Kiến Trúc Microservices Chuẩn Enterprise

Hệ thống được thiết kế theo kiến trúc Microservices hiện đại, tương thích hoàn toàn với triển khai trên Docker lẫn Kubernetes (K8s):

```
                                ┌────────────────────────────────────────────────┐
                                │   CLIENT LAYER (Web React / Mobile Flutter)    │
                                └───────────────────────┬────────────────────────┘
                                                        │
                                         ┌──────────────▼─────────────┐
                                         │  REVERSE PROXY (Cloudflare)│
                                         └──────────────┬─────────────┘
                                                        │
                                         ┌──────────────▼─────────────┐
                                         │    API GATEWAY (Spring)    │
                                         │(Routing / JWT / Rate Limit)│
                                         └──────────────┬─────────────┘
                                                        │
                 ┌──────────────────────────────────────┼──────────────────────────────────────┐
                 │                                      │                                      │
   ┌─────────────▼─────────────┐          ┌─────────────▼─────────────┐          ┌─────────────▼─────────────┐
   │       CONFIG SERVER       │          │     DISCOVERY SERVICE     │          │   MONITORING & TRACING    │
   │   (Spring Cloud Config)   │          │   (Spring Cloud Eureka)   │          │ Prometheus/Grafana/Jaeger │
   └───────────────────────────┘          └───────────────────────────┘          └───────────────────────────┘
                 │                                      │                                      │
 ┌───────────────┴──────────────────────────────────────┴──────────────────────────────────────┴───────────────┐
 │                                              BUSINESS SERVICES                                              │
 │                                                                                                             │
 │ ┌──────────────┐ ┌──────────────┐ ┌──────────────┐ ┌──────────────┐ ┌──────────────┐ ┌───────────────────┐  │
 │ │ Auth Service │ │ User Service │ │ Catalog Svc  │ │ Booking Svc  │ │ Payment Svc  │ │   Queue Service   │  │
 │ │ (Port 8081)  │ │ (Port 8082)  │ │ (Port 8083)  │ │ (Port 8084)  │ │ (Port 8085)  │ │   (Port 8087)     │  │
 │ └──────┬───────┘ └──────┬───────┘ └──────┬───────┘ └──────┬───────┘ └──────┬───────┘ └─────────┬─────────┘  │
 │        │                │                │                │                │                   │            │
 │ ┌──────▼───────┐ ┌──────▼───────┐ ┌──────▼───────┐ ┌──────▼───────┐ ┌──────▼───────┐           │            │
 │ │postgres-auth │ │postgres-user │ │postgres-catlg│ │postgres-book │ │postgres-pay  │           │            │
 │ │ (Port 5433)  │ │ (Port 5434)  │ │ (Port 5435)  │ │ (Port 5436)  │ │ (Port 5437)  │           │            │
 │ └──────────────┘ └──────────────┘ └──────────────┘ └──────┬───────┘ └──────┬───────┘           │            │
 │                                                           │                │                   │            │
 │                                                           ▼                │                   │            │
 │                                                    ┌─────────────┐         │                   │            │
 │                                                    │    Redis    │◄────────┼───────────────────┘            │
 │                                                    │ (Port 6379) │         │                                │
 │                                                    └─────────────┘         │                                │
 │                                                                            ▼                                │
 │ ┌───────────────────┐                             ┌─────────────────────────────────┐                       │
 │ │ Recommend Service │                             │          Apache Kafka           │                       │
 │ │ (Python/FastAPI)  │                             │      (KRaft Mode - Port 9092)   │                       │
 │ └───────────────────┘                             └────────────────┬────────────────┘                       │
 │                                                                    │                                        │
 │                                                           ┌────────▼────────┐                               │
 │                                                           │Notification Svc │                               │
 │                                                           │  (Port 8086)    │                               │
 │                                                           └────────┬────────┘                               │
 │                                                                    ▼                                        │
 │                                                             ┌─────────────┐                                 │
 │                                                             │   MongoDB   │                                 │
 │                                                             │(Port 27017) │                                 │
 │                                                             └─────────────┘                                 │
 └─────────────────────────────────────────────────────────────────────────────────────────────────────────────┘
```

---

## 4. Chi Tiết Các Microservices

> Toàn bộ các backend Java services được phát triển trên nền tảng **Java 21+** và **Spring Boot 4.1.1+**, tận dụng sức mạnh của **Virtual Threads (Project Loom)** và hệ sinh thái Spring Cloud hiện đại.

| Service | Công nghệ | Cổng Host | Database | Trách nhiệm chính |
| :--- | :--- | :--- | :--- | :--- |
| **API Gateway** | Spring Cloud Gateway (Spring Boot 4.1.1+) | `8080` | - | Định tuyến request, xác thực JWT tập trung, Rate Limiting chống spam |
| **Config Server** | Spring Cloud Config (Spring Boot 4.1.1+) | `8888` | Git / Local | Quản lý tập trung toàn bộ cấu hình `application.yml` của các service |
| **Discovery Svc** | Spring Cloud Eureka (Spring Boot 4.1.1+) | `8761` | In-Memory | Đăng ký & phát hiện dịch vụ (Service Registration & Discovery), kiểm tra Health Check |
| **Auth Service** | Java 21+ / Spring Boot 4.1.1+ + Security | `8081` | PostgreSQL (`5433`) | Đăng ký, đăng nhập, cấp Access/Refresh Token, phân quyền RBAC |
| **User Service** | Java 21+ / Spring Boot 4.1.1+ + Data JPA | `8082` | PostgreSQL (`5434`) | Quản lý hồ sơ cá nhân, CCCD, thông tin doanh nghiệp/ngân hàng (JSONB) |
| **Catalog Service** | Java 21+ / Spring Boot 4.1.1+ + Redis Cache | `8083` | PostgreSQL (`5435`) | Quản lý danh mục sự kiện, cấu hình hạng vé, tối ưu truy vấn bằng Cache |
| **Booking Service** | Java 21+ / Spring Boot 4.1.1+ + Redis | `8084` | PostgreSQL (`5436`) | Giữ chỗ nguyên tử 10 phút, tạo đơn hàng, quản lý vé QR, auto-release |
| **Payment Service** | Java 21+ / Spring Boot 4.1.1+ | `8085` | PostgreSQL (`5437`) | Tích hợp cổng VNPay Sandbox, kiểm tra checksum HMAC, xuất bản Kafka event |
| **Queue Service** | Java 21+ / Spring Boot 4.1.1+ WebSocket + Redis | `8087` | Redis (`6379`) | Phòng chờ ảo real-time, xếp hàng bằng Sorted Set, quản lý Heartbeat 15s |
| **Notification Svc**| Java 21+ / Spring Boot 4.1.1+ + JavaMail | `8086` | MongoDB (`27017`) | Nghe Kafka event, tạo QR code hình ảnh, gửi email vé điện tử, lưu log |
| **Recommend Svc** | Python 3.11+ / FastAPI | `8088` | - | Thuật toán AI gợi ý sự kiện phù hợp cho người dùng |

---

## 5. Kiến Trúc Dữ Liệu & Cách Ly (Database Isolation)

Tuân thủ nghiêm ngặt mô hình **Database per Service**:
1. **5 Container PostgreSQL độc lập về phần cứng:** Mỗi service chạy 1 container riêng (`postgres-auth`, `postgres-user`, `postgres-catalog`, `postgres-booking`, `postgres-payment`).
2. **Cấu hình động qua `.env`:** Tên DB, User, Password, Port và Host đều được cấu hình qua file `.env`. Khi chuyển từ Localhost lên VPS hay Cloud RDS, **không cần sửa một dòng code nào**.
3. **Database Migration bằng Flyway:** Các câu lệnh tạo bảng, tạo index và kích hoạt extension (`uuid-ossp`, `pgcrypto`) được quản lý bằng Flyway trong mã nguồn backend, giúp hệ thống 100% độc lập với môi trường Docker bên ngoài.

---

## 6. Apache Kafka Events & Saga Choreography

```
                    ┌──────────────────────────────────────────────┐
                    │                KAFKA BROKER                  │
                    │                                              │
                    │  ┌─────────────────┐  ┌────────────────────┐ │
 Payment ──────────►│  │ payment.success │  │ payment.failed     │ │──────────► Booking
 Service            │  └─────────────────┘  └────────────────────┘ │           Service
                    │                                              │
                    │  ┌─────────────────┐  ┌────────────────────┐ │
 Booking ──────────►│  │tickets.generated│  │ booking.cancelled  │ │──────────► Catalog
 Service            │  └─────────────────┘  └────────────────────┘ │           Service
                    │                                              │
                    │  ┌─────────────────┐                         │
 Booking ──────────►│  │tickets.generated│                         │──────────► Notification
 Service            │  └─────────────────┘                         │           Service
                    │                                              │
                    │  ┌──────────────────────┐                    │
 Booking ──────────►│  │booking.refund-request│                    │──────────► Payment
 Service            │  └──────────────────────┘                    │           Service
                    │                                              │
                    └──────────────────────────────────────────────┘
```

* **`payment.success`:** Payment Service bắn event sau khi nhận IPN thành công từ VNPay ➔ Booking Service cập nhật đơn `PAID` và vé `ISSUED`.
* **`tickets.generated`:** Booking Service bắn event ➔ Catalog Service trừ vĩnh viễn `available_quantity`, Notification Service gửi email vé kèm mã QR.
* **`booking.refund-requested` (Saga Rollback):** Nếu sau khi thanh toán mà Booking Service bị lỗi sinh vé ➔ Yêu cầu Payment Service tự động gọi VNPay hoàn tiền cho khách.
* **Idempotent Consumers:** Mọi Kafka consumer đều kiểm tra trạng thái trước khi thực hiện để đảm bảo nếu nhận trùng event (do network retry) cũng không bị trừ 2 lần vé hay hoàn tiền 2 lần.

---

## 7. Giám Sát Hệ Thống (Observability)

* **Prometheus & Grafana:** Thu thập số liệu RPS, tỷ lệ lỗi HTTP 5xx, độ trễ p95/p99, dung lượng RAM/CPU của từng container, số người đang đợi trong phòng chờ ảo.
* **OpenTelemetry & Jaeger / Zipkin:** Truy vết phân tán (Distributed Tracing). Mỗi request được gắn một `TraceId` duy nhất để theo dõi toàn bộ hành trình từ Gateway qua các service và Kafka.

---

## 8. Kiến Trúc Bảo Mật Zero Trust (Token Relay & Asymmetric Keys)

Hệ thống áp dụng mô hình bảo mật **Zero Trust (Defense-in-Depth - Không tin tưởng ngầm định bất kỳ thành phần nào, kể cả trong mạng nội bộ)**:

```
[Client] ──(Bearer JWT)──► [API Gateway] ──(Token Relay: Giữ nguyên Bearer JWT)──► [Microservices]
                                │                                                        │
                      (Kiểm tra sơ bộ/Rate Limit)                              (Tự xác thực độc lập)
                                │                                                        │
                      (Chỉ giữ Public Key)                                     (Chỉ giữ Public Key)
```

1. **Khóa bất đối xứng (RSA Asymmetric Keys / JWKS):**
   * **`auth-service`**: Nơi duy nhất nắm giữ **Private Key** để ký (sign) Access Token khi người dùng đăng nhập.
   * **`api-gateway` & Microservices con**: Chỉ nắm giữ **Public Key** (hoặc đồng bộ qua endpoint JWKS `/.well-known/jwks.json` có in-memory caching). Public key chỉ có thể giải mã và xác thực chữ ký, hoàn toàn không thể giả mạo để tạo token mới.
2. **Cơ chế Token Relay (Chuyển tiếp Token):**
   * API Gateway không bóc tách dữ liệu thành header trần (`X-User-Id`), mà chuyển tiếp nguyên xi chuỗi Bearer JWT qua các service nội bộ.
   * Mỗi service con (`booking-service`, `user-service`,...) tự giải mã chữ ký bằng Public Key và tự kiểm tra quyền hạn (`@PreAuthorize`), triệt tiêu hoàn toàn nguy cơ giả mạo request nội bộ (Internal Header Spoofing).
3. **Bộ xử lý ngoại lệ đồng nhất (`common-library`):**
   * Các ngoại lệ bảo mật (`UnauthorizedException`, `InvalidTokenException`, `ForbiddenException`) được chuẩn hóa và bắt tự động bởi `GlobalExceptionHandler`, trả về response chuẩn định dạng số (`status != 0`).

---

> 📄 Xem tiếp:
> * [Sơ đồ kiến trúc Mermaid](architecture-diagrams.md)
> * [Thiết kế cơ sở dữ liệu chi tiết](database-schema.md)
> * [Thiết kế phòng chờ ảo](virtual-waiting-room.md)
> * [Luồng kỹ thuật chi tiết](technical-flows.md)
> * [Đặc tả API](api-design.md)
