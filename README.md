# 🎫 TicketBooking

> **Hệ thống Đặt vé Sự kiện phân tán — Distributed Event Ticketing System**

[![Java](https://img.shields.io/badge/Java-17+-orange?logo=openjdk)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.x-brightgreen?logo=spring-boot)](https://spring.io/projects/spring-boot)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-15+-blue?logo=postgresql)](https://www.postgresql.org/)
[![Redis](https://img.shields.io/badge/Redis-7+-red?logo=redis)](https://redis.io/)
[![Kafka](https://img.shields.io/badge/Apache%20Kafka-3.x-black?logo=apache-kafka)](https://kafka.apache.org/)
[![Docker](https://img.shields.io/badge/Docker-Compose-blue?logo=docker)](https://docs.docker.com/compose/)
[![License](https://img.shields.io/badge/License-MIT-yellow)](LICENSE)

---

## 📖 Mục lục

- [Tổng quan](#-tổng-quan)
- [Kiến trúc hệ thống](#-kiến-trúc-hệ-thống)
- [Tính năng chính](#-tính-năng-chính)
- [Tech Stack](#-tech-stack)
- [Cấu trúc dự án](#-cấu-trúc-dự-án)
- [Cài đặt & Chạy](#-cài-đặt--chạy)
- [Tài liệu chi tiết](#-tài-liệu-chi-tiết)
- [Đóng góp](#-đóng-góp)
- [License](#-license)

---

## 🎯 Tổng quan

**TicketBooking** là nền tảng thương mại điện tử chuyên biệt cho việc phân phối vé sự kiện. Dự án được xây dựng theo kiến trúc **Microservices** kết hợp **Event-Driven Architecture (EDA)** nhằm giải quyết các thách thức:

- 🔥 **Burst Traffic** — Hàng ngàn người cùng mua vé khi sự kiện "hot" mở bán
- 🔒 **Anti-Overbooking** — Đảm bảo không bán vượt quá số lượng vé phát hành
- ⚡ **Real-time** — Hiển thị tình trạng vé theo thời gian thực
- 🔄 **Eventual Consistency** — Đảm bảo nhất quán dữ liệu qua Kafka event

### Mô hình kinh doanh

```
B2B2C (Mô hình đóng)
├── Admin         → Kiểm duyệt & cấp tài khoản cho Organizer
├── Organizer     → Đăng tải sự kiện, quản lý vé, check-in
└── Customer      → Đăng ký tự do, tìm kiếm & mua vé
```

---

## 🏗 Kiến trúc hệ thống

```
                          ┌─────────────────┐
                          │   API Gateway   │
                          │ (Spring Cloud)  │
                          └────────┬────────┘
                                   │
          ┌────────────────────────┼────────────────────────┐
          │                        │                        │
   ┌──────▼──────┐         ┌──────▼──────┐         ┌──────▼──────┐
   │ Auth Service│         │Catalog Svc  │         │Booking Svc  │
   │  (JWT/RBAC) │         │(Events/Tix) │         │  (Orders)   │
   │ PostgreSQL  │         │ PostgreSQL  │         │ PostgreSQL  │
   └─────────────┘         └─────────────┘         │   + Redis   │
                                                   └───────┬─────┘
                                                           │
                                   ┌───────────────────────┤
                                   │                       │
                            ┌──────▼──────┐         ┌──────▼──────┐
                            │Payment Svc  │         │  User Svc   │
                            │  (VNPay)    │         │ (Profiles)  │
                            │ PostgreSQL  │         │ PostgreSQL  │
                            └──────┬──────┘         └─────────────┘
                                   │
                            ┌──────▼──────┐
                            │   Apache    │
                            │   Kafka     │
                            └──────┬──────┘
                                   │
                            ┌──────▼──────┐
                            │Notification │
                            │  Service    │
                            │  MongoDB    │
                            └─────────────┘
```

> 📄 Xem chi tiết tại [System Design](docs/system-design.md)

---

## ✨ Tính năng chính

### 👤 Customer
| Tính năng | Mô tả |
|-----------|--------|
| 🔍 Tìm kiếm & Lọc | Tìm sự kiện theo danh mục, thời gian, địa điểm |
| 📊 Xem vé Real-time | Số lượng vé còn trống cập nhật theo thời gian thực |
| 🔒 Giữ chỗ (Seat Hold) | Khóa vé trong 10 phút chờ thanh toán |
| 💳 Thanh toán Online | Tích hợp VNPay/Stripe |
| 🎟 E-Ticket (QR Code) | Nhận vé điện tử qua email sau thanh toán |

### 🏢 Organizer
| Tính năng | Mô tả |
|-----------|--------|
| 📝 Quản lý sự kiện | Tạo, chỉnh sửa, cấu hình thời gian bán vé |
| 🎫 Quản lý hạng vé | Định nghĩa VIP, GA, giá tiền, số lượng |
| 📱 Check-in QR | Quét mã QR xác minh vé tại cổng sự kiện |
| 📈 Báo cáo doanh thu | Thống kê vé bán ra và doanh thu |

### ⚙️ System Background
| Tính năng | Mô tả |
|-----------|--------|
| 🚦 Virtual Waiting Room | Tự động bật hàng chờ khi quá tải, xếp hàng real-time qua WebSocket |
| ⏰ Auto-Release | Tự động nhả vé nếu không thanh toán sau 10 phút |
| 📧 Email tự động | Gửi QR code ngay khi thanh toán thành công |
| 🔄 Saga Rollback | Hoàn tiền tự động nếu hệ thống gặp lỗi |

---

## 🛠 Tech Stack

| Thành phần | Công nghệ |
|------------|-----------|
| **Backend Framework** | Java 17+ / Spring Boot 3.x |
| **API Gateway** | Spring Cloud Gateway |
| **Database (chính)** | PostgreSQL 15+ |
| **Database (log)** | MongoDB 7+ |
| **Cache & Lock** | Redis 7+ |
| **Message Broker** | Apache Kafka 3.x |
| **Authentication** | JWT (JSON Web Token) + Spring Security |
| **Containerization** | Docker & Docker Compose |
| **CI/CD** | Jenkins / GitHub Actions |
| **Documentation** | Swagger / OpenAPI 3.0 |

---

## 📁 Cấu trúc dự án

```
TicketBooking/
├── docs/                           #    Tài liệu thiết kế
│   ├── system-design.md            #    Thiết kế hệ thống tổng quan
│   ├── database-schema.md          #    Thiết kế CSDL chi tiết
│   ├── api-design.md               #    Thiết kế RESTful API
│   ├── technical-flows.md          #    Luồng xử lý kỹ thuật
│   └── architecture-diagrams.md    #    Sơ đồ kiến trúc (Mermaid)
│
├── services/                       #    Microservices
│   ├── api-gateway/                #    API Gateway
│   ├── auth-service/               #    Xác thực & Phân quyền
│   ├── user-service/               #    Hồ sơ người dùng
│   ├── catalog-service/            #    Danh mục & Sự kiện
│   ├── booking-service/            #    Đặt vé (Core)
│   ├── payment-service/            #    Thanh toán
│   └── notification-service/       #    Thông báo
│
├── docker-compose.yml              #    Docker Compose
├── .gitignore
├── LICENSE
└── README.md
```

---

## 🚀 Cài đặt & Chạy

### Yêu cầu

- Java 17+
- Docker & Docker Compose
- Maven 3.8+

### Khởi chạy Infrastructure

```bash
# Clone dự án
git clone https://github.com/dangngockhieu/TicketBooking.git
cd TicketBooking

# Khởi chạy PostgreSQL, Redis, Kafka, MongoDB
docker-compose up -d

# Build tất cả services
mvn clean package -DskipTests

# Chạy từng service (mở terminal riêng cho mỗi service)
cd services/auth-service && mvn spring-boot:run
cd services/user-service && mvn spring-boot:run
cd services/catalog-service && mvn spring-boot:run
cd services/booking-service && mvn spring-boot:run
cd services/payment-service && mvn spring-boot:run
cd services/notification-service && mvn spring-boot:run
```

### Truy cập

| Service | URL |
|---------|-----|
| API Gateway | `http://localhost:8080` |
| Auth Service | `http://localhost:8081` |
| User Service | `http://localhost:8082` |
| Catalog Service | `http://localhost:8083` |
| Booking Service | `http://localhost:8084` |
| Payment Service | `http://localhost:8085` |
| Notification Service | `http://localhost:8086` |

---

## 📚 Tài liệu chi tiết

| Tài liệu | Nội dung |
|-----------|----------|
| [📐 System Design](docs/system-design.md) | Thiết kế hệ thống tổng quan, actors, use cases |
| [🗄 Database Schema](docs/database-schema.md) | Chi tiết bảng, quan hệ, indexes |
| [🔌 API Design](docs/api-design.md) | RESTful API endpoints, request/response |
| [⚡ Technical Flows](docs/technical-flows.md) | Luồng Seat Hold, Payment, Saga |
| [📊 Architecture Diagrams](docs/architecture-diagrams.md) | Sơ đồ kiến trúc, sequence diagrams |
| [🚦 Virtual Waiting Room](docs/virtual-waiting-room.md) | Thiết kế phòng chờ ảo, WebSocket, heartbeat |

---

## 📄 License

Dự án được phân phối theo giấy phép [MIT License](LICENSE).

---

<p align="center">
  <b>TicketBooking</b> — Built with ❤️ for High-Performance Event Ticketing
</p>
