# 🎫 TicketBooking

> **Hệ thống Đặt vé Sự kiện phân tán — Distributed Event Ticketing System**

[![Java](https://img.shields.io/badge/Java-21%2B-orange?logo=openjdk)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.1.1%2B-brightgreen?logo=spring-boot)](https://spring.io/projects/spring-boot)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16+-blue?logo=postgresql)](https://www.postgresql.org/)
[![Redis](https://img.shields.io/badge/Redis-7+-red?logo=redis)](https://redis.io/)
[![Kafka](https://img.shields.io/badge/Apache%20Kafka-KRaft-black?logo=apache-kafka)](https://kafka.apache.org/)
[![Docker](https://img.shields.io/badge/Docker-Compose-blue?logo=docker)](https://docs.docker.com/compose/)
[![License](https://img.shields.io/badge/License-MIT-yellow)](LICENSE)

---

## 📖 Mục lục

- [Tổng quan](#-tổng-quan)
- [Kiến trúc hệ thống](#-kiến-trúc-hệ-thống)
- [Kiến trúc Database & Bảo mật](#-kiến-trúc-database--bảo-mật)
- [Tính năng chính](#-tính-năng-chính)
- [Tech Stack](#-tech-stack)
- [Cấu trúc dự án](#-cấu-trúc-dự-án)
- [Cài đặt & Khởi chạy](#-cài-đặt--khởi-chạy)
- [Tài liệu chi tiết](#-tài-liệu-chi-tiết)
- [License](#-license)

---

## 🎯 Tổng quan

**TicketBooking** là nền tảng thương mại điện tử chuyên biệt cho việc phân phối vé sự kiện. Dự án được thiết kế theo kiến trúc **Microservices** kết hợp **Event-Driven Architecture (EDA)** nhằm giải quyết các bài toán kỹ thuật phức tạp:

- 🔥 **Burst Traffic** — Hàng ngàn đến hàng vạn người cùng truy cập khi sự kiện "hot" mở bán.
- 🚦 **Virtual Waiting Room** — Tự động kích hoạt phòng chờ ảo khi quá tải, xếp hàng real-time qua WebSocket.
- 🔒 **Anti-Overbooking** — Cơ chế giữ chỗ nguyên tử (Atomic Seat Hold) với Redis `INCRBY` đảm bảo không bán vượt quá số vé.
- 🔄 **Eventual Consistency** — Đồng bộ dữ liệu bất đồng bộ giữa các dịch vụ qua Apache Kafka và Saga Pattern.

### Mô hình kinh doanh

```
B2B2C (Mô hình đóng)
├── Admin         → Phê duyệt & cấp tài khoản cho Ban tổ chức (chống lừa đảo)
├── Organizer     → Tạo sự kiện, cấu hình hạng vé, quét mã QR check-in, xem báo cáo
└── Customer      → Đăng ký tự do, tìm kiếm sự kiện, giữ chỗ và thanh toán trực tuyến
```

---

## 🏗 Kiến trúc hệ thống

```
                           ┌─────────────────────────┐
                           │       API Gateway       │
                           │ (Spring Cloud Gateway)  │
                           └────────────┬────────────┘
                                        │
        ┌───────────────┬───────────────┼──────────────┬───────────────┐
        │               │               │              │               │
 ┌──────▼───────┐┌──────▼───────┐┌──────▼──────┐┌──────▼──────┐┌───────▼───────┐
 │ Auth Service ││ User Service ││ Catalog Svc ││ Booking Svc ││ Queue Service │
 │  (JWT/RBAC)  ││  (Profiles)  ││ (Events/Tix)││  (Orders)   ││ (Waiting Room)│
 │   Postgres   ││   Postgres   ││  Postgres   ││Postgres+Rds ││Redis+WebSocket│
 └──────────────┘└──────────────┘└─────────────┘└──────┬──────┘└───────────────┘
                                                       │
                                       ┌───────────────┤
                                       │               │
                                ┌──────▼──────┐┌───────▼──────┐
                                │ Payment Svc ││ Notification │
                                │   (VNPay)   ││   Service    │
                                │   Postgres  ││   MongoDB    │
                                └──────┬──────┘└───────▲──────┘
                                       │               │
                                ┌──────▼───────────────┴──────┐
                                │        Apache Kafka         │
                                │   (Event-Driven Backbone)   │
                                └─────────────────────────────┘
```

> 📄 Xem chi tiết tại [System Design](docs/system-design.md) và [Architecture Diagrams](docs/architecture-diagrams.md)

---

## 🔐 Kiến trúc Database & Bảo mật

Hệ thống tuân thủ nghiêm ngặt nguyên tắc **Database per Service** và **Đặc quyền tối thiểu (Principle of Least Privilege - PoLP)**:

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                        TICKETBOOKING DOCKER NETWORK                         │
│                                                                             │
│   ┌────────────────┐   ┌────────────────┐   ┌────────────────┐              │
│   │ postgres-auth  │   │ postgres-user  │   │postgres-catalog│              │
│   │  (Port 5433)   │   │  (Port 5434)   │   │  (Port 5435)   │              │
│   └────────┬───────┘   └────────┬───────┘   └────────┬───────┘              │
│            │                    │                    │                      │
│       Auth Service         User Service       Catalog Service               │
│                                                                             │
│   ┌────────────────┐   ┌────────────────┐                                   │
│   │postgres-booking│   │postgres-payment│   + Redis, Kafka, MongoDB         │
│   │  (Port 5436)   │   │  (Port 5437)   │                                   │
│   └────────┬───────┘   └────────┬───────┘                                   │
│            │                    │                                           │
│      Booking Service      Payment Service                                   │
└─────────────────────────────────────────────────────────────────────────────┘
```

* **Cách ly phần cứng tuyệt đối (True Physical Isolation):** Mỗi service sở hữu riêng 1 container PostgreSQL hoàn toàn độc lập (`postgres-auth`, `postgres-user`, `postgres-catalog`, `postgres-booking`, `postgres-payment`). Nếu 1 container gặp sự cố, các service khác vẫn hoạt động bình thường.
* **Cấu hình động 100% qua file [`.env`](.env.example):** Tất cả database name, user, password, port và host đều được nạp trực tiếp từ `.env`. Khi cần chuyển từ Localhost lên VPS hoặc Cloud RDS, **chỉ cần sửa `.env`, không cần sửa một dòng code nào**.
* **Redis Protected Mode:** Redis được bảo vệ bằng mật khẩu (`requirepass`) và kích hoạt Keyspace Notification (`Ex`) phục vụ tự động nhả vé.
* **MongoDB Auth:** Quản lý truy cập phân quyền giữa root admin và application user (`notification_user`).

---

## ✨ Tính năng chính

### 👤 Customer (Khách hàng)
| Tính năng | Mô tả |
|-----------|--------|
| 🔍 Tìm kiếm & Lọc | Tìm sự kiện theo danh mục (Âm nhạc, Thể thao...), địa điểm, thời gian |
| 📊 Tình trạng vé Real-time | Hiển thị số lượng vé còn trống chính xác theo thời gian thực |
| 🚦 Xếp hàng phòng chờ ảo | Khi sự kiện quá tải, tự động vào phòng chờ với số thứ tự và thời gian ước tính |
| 🔒 Giữ chỗ (Seat Hold) | Khóa số lượng vé mong muốn trong **10 phút** để thanh toán |
| 💳 Thanh toán Trực tuyến | Thanh toán qua cổng VNPay Sandbox (hỗ trợ thẻ ATM, QR Pay) |
| 🎟 E-Ticket (QR Code) | Nhận vé điện tử có mã QR độc nhất qua email ngay sau khi thanh toán |

### 🏢 Organizer (Ban tổ chức)
| Tính năng | Mô tả |
|-----------|--------|
| 📝 Quản lý sự kiện | Đăng tải thông tin, banner, cấu hình thời gian mở/đóng bán |
| 🎫 Quản lý hạng vé | Phân loại VVIP, VIP, GA với giá bán và số lượng phát hành |
| 📱 Check-in QR Code | Quét mã QR xác minh vé vào cổng, chặn quét trùng lặp |
| 📈 Báo cáo doanh thu | Thống kê vé bán ra, tỷ lệ lấp đầy và doanh thu thực tế |

### ⚙️ System Background & Resilience
| Tính năng | Mô tả |
|-----------|--------|
| ⏰ Auto-Release Seat | Tự động hủy đơn và nhả vé về kho nếu không thanh toán sau 10 phút |
| 🔌 Disconnect Detection | Cơ chế Heartbeat WebSocket (15s): mất mạng/đóng tab là tự động loại khỏi hàng chờ |
| 🔄 Saga Rollback | Tự động hoàn tiền nếu hệ thống gặp sự cố sinh vé sau khi đã trừ tiền |

---

## 🛠 Tech Stack

| Thành phần | Công nghệ | Chi tiết |
|------------|-----------|----------|
| **Backend Framework** | Java 21+ / Spring Boot 4.1.1+ | Virtual Threads (Project Loom), Spring Cloud Gateway, Spring Security |
| **AI Recommendation** | Python 3.11 / FastAPI | AI Event Recommendation Engine (Gợi ý sự kiện thông minh) |
| **Relational Database** | PostgreSQL 16 | 5 Cụm Container độc lập, UUID v4, JSONB metadata, Flyway |
| **Document Database** | MongoDB 7 | Lưu trữ unstructured notification logs |
| **Cache & Distributed Lock**| Redis 7 | Atomic `INCRBY`, Keyspace Events, Sorted Set |
| **Message Broker** | Apache Kafka 3.7+ | Chế độ **KRaft Mode** (không cần Zookeeper, tối ưu RAM) |
| **Kafka Management** | Kafka UI | Giao diện web trực quan quản trị Topics & Consumers (Port 8090) |
| **Service Discovery** | Spring Cloud Eureka | Quản lý định tuyến và phát hiện dịch vụ động (Port 8761) |
| **Config Management** | Spring Cloud Config | Quản lý tập trung toàn bộ cấu hình hệ thống (Port 8888) |
| **Observability** | Prometheus + Grafana + Jaeger | Metrics, Alerting, Dashboard và Distributed Tracing |
| **Bảo mật** | JWT + RBAC | Access Token (ngắn hạn) + Refresh Token |
| **DevOps & Containers** | Docker & Docker Compose | Đóng gói toàn bộ infrastructure và services |

---

## 📁 Cấu trúc dự án

```
TicketBooking/
├── docs/                           # 📄 Toàn bộ tài liệu thiết kế hệ thống
│   ├── system-design.md            #    Thiết kế hệ thống tổng quan
│   ├── database-schema.md          #    Thiết kế CSDL chi tiết & SQL DDL
│   ├── api-design.md               #    Đặc tả RESTful API endpoints
│   ├── technical-flows.md          #    Luồng kỹ thuật (Seat hold, Payment, Saga)
│   ├── architecture-diagrams.md    #    Sơ đồ kiến trúc Mermaid
│   └── virtual-waiting-room.md     #    Thiết kế chi tiết phòng chờ ảo
├── services/                       # 🔧 Mã nguồn các Microservices (theo plan)
│   ├── api-gateway/                #    API Gateway (Port 8080)
│   ├── auth-service/               #    Xác thực & Phân quyền (Port 8081)
│   ├── user-service/               #    Hồ sơ người dùng (Port 8082)
│   ├── catalog-service/            #    Danh mục & Sự kiện (Port 8083)
│   ├── booking-service/            #    Đặt vé & Giữ chỗ Core (Port 8084)
│   ├── payment-service/            #    Thanh toán VNPay (Port 8085)
│   ├── notification-service/       #    Thông báo & Email QR (Port 8086)
│   ├── queue-service/              #    Phòng chờ ảo WebSocket (Port 8087)
│   └── recommend-service/          #    AI Gợi ý sự kiện - Python/FastAPI (Port 8088)
│
├── docker-compose.yml              # 🚀 File docker-compose khởi chạy toàn bộ hạ tầng
├── .env.example                    # 📋 Template biến môi trường mẫu
├── .env                            # 🔒 File biến môi trường thực tế (Git ignore)
├── .gitignore
├── LICENSE
└── README.md
```

---

## 🚀 Cài đặt & Khởi chạy

### Yêu cầu tiên quyết
- **Docker Desktop** (hỗ trợ Docker Compose v2)
- **Java 21+** & **Maven 3.9+** (khi chạy mã nguồn backend)

### Bước 1: Thiết lập biến môi trường
Tạo file `.env` từ mẫu `.env.example`:
```bash
cp .env.example .env
```
*(Bạn có thể mở file `.env` để tùy chỉnh mật khẩu Database, Redis, Kafka theo ý muốn).*

### Bước 2: Khởi chạy toàn bộ hạ tầng (Infrastructure)
```bash
docker compose up -d
```

Kiểm tra trạng thái các container:
```bash
docker compose ps
```

### Bước 3: Truy cập các cổng dịch vụ hạ tầng

| Dịch vụ | Địa chỉ Host | Database / User / Password (trong `.env`) |
| :--- | :--- | :--- |
| **PostgreSQL - Auth** | `localhost:5433` | DB: `auth_db` \| User: `auth_user` \| Pass: `auth_pass_2026` |
| **PostgreSQL - User** | `localhost:5434` | DB: `user_db` \| User: `user_svc_user` \| Pass: `user_svc_pass_2026` |
| **PostgreSQL - Catalog** | `localhost:5435` | DB: `catalog_db` \| User: `catalog_user` \| Pass: `catalog_pass_2026` |
| **PostgreSQL - Booking** | `localhost:5436` | DB: `booking_db` \| User: `booking_user` \| Pass: `booking_pass_2026` |
| **PostgreSQL - Payment** | `localhost:5437` | DB: `payment_db` \| User: `payment_user` \| Pass: `payment_pass_2026` |
| **Redis** | `localhost:6379` | Mật khẩu: `redis_secure_pass_2026` |
| **Kafka Broker** | `localhost:9092` | PLAINTEXT |
| **Kafka UI Dashboard** | [http://localhost:8090](http://localhost:8090) | `admin` / `admin_kafka_2026` |
| **MongoDB** | `localhost:27017` | `mongo_admin` / `mongo_secure_pass_2026` |

Dừng toàn bộ hạ tầng:
```bash
docker compose down
```

---

## 📚 Tài liệu chi tiết

| Tài liệu | Nội dung |
|:---|:---|
| [📐 System Design](docs/system-design.md) | Tổng quan hệ thống, Actors, Use Cases, Microservices, RBAC Matrix |
| [🗄 Database Schema](docs/database-schema.md) | Thiết kế chi tiết từng bảng, kiểu dữ liệu, Indexes, Redis keys |
| [🔌 API Design](docs/api-design.md) | Đặc tả Request/Response, Error codes, HTTP Status cho tất cả APIs |
| [⚡ Technical Flows](docs/technical-flows.md) | Luồng Atomic Seat Hold, Kafka Event Choreography, Saga Rollback |
| [📊 Architecture Diagrams](docs/architecture-diagrams.md) | 7 sơ đồ Mermaid (System, Sequence, ER, State Machine, Deployment) |
| [🚦 Virtual Waiting Room](docs/virtual-waiting-room.md) | Cơ chế phòng chờ ảo, WebSocket STOMP, Heartbeat, Auto-enable |

---

## 📄 License

Dự án được phân phối theo giấy phép [MIT License](LICENSE).

---

<p align="center">
  <b>TicketBooking</b> — Built with ❤️ for High-Performance Distributed Event Ticketing
</p>
