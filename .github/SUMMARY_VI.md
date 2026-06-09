# 📋 Tóm Tắt Dự Án - Ticket System

## 🎯 Tổng Quan Nhanh

**Ticket System** là hệ thống quản lý bán vé xe bus sử dụng kiến trúc microservices với Spring Boot, Kafka, Redis Cluster, và MySQL.

---

## 🏗️ Kiến Trúc Tổng Quan

### Microservices

#### 1️⃣ **Booking Service** (Port 8081)
- **Chức năng**: Xử lý đặt vé
- **Công nghệ**: Spring Boot, Kafka Producer, OpenFeign
- **API chính**: `POST /api/booking`
- **Luồng hoạt động**: 
  ```
  Client → Booking API → Kafka Producer → order-events topic
  ```

#### 2️⃣ **Manage Revenue Service** (Port 8082)
- **Chức năng**: Quản lý toàn bộ hệ thống
- **Công nghệ**: Spring Boot, Kafka Consumer, JPA, Redis
- **Modules**:
  - 🔐 Authentication & Authorization (JWT)
  - 👥 User Management
  - 🚌 Bus Management
  - 🛣️ Route Management
  - 🚗 Trip Management
  - 🎫 Ticket Management
  - 💰 Revenue Management
  - ⭐ Loyalty Points System
  - 💵 Salary Management
  - 📧 Email Service

#### 3️⃣ **Common Library**
- **Chức năng**: Thư viện chia sẻ giữa các services
- **Bao gồm**:
  - Security Configuration
  - JWT Utilities
  - Kafka Configuration
  - Redis Configuration
  - Exception Handling
  - Common DTOs
  - Annotations (@PublicApi, @RoleRequired)

---

## 🗄️ Infrastructure

### 1. MySQL Database
- **Version**: 8.0
- **Database**: `quan-ly-ban-hang`
- **Port**: 3306
- **Docker**: Yes

### 2. Kafka Cluster
- **Version**: 7.5.0 (KRaft mode)
- **Brokers**: 3 nodes
  - broker1: 9092
  - broker2: 9093
  - broker3: 9094
- **Kafka UI**: Port 9000
- **Topics**: `order-events`, `payment-events`, `notification-events`
- **Replication Factor**: 3

### 3. Redis Cluster
- **Version**: 7 Alpine
- **Nodes**: 6 nodes (3 masters + 3 replicas)
  - Master: 7001, 7002, 7003
  - Replica: 7004, 7005, 7006
- **Network**: Custom subnet 173.20.0.0/16
- **Use cases**: Session management, Caching

---

## 📊 Database Schema

### Core Tables

#### Users
- **Columns**: id, email, password, role, is_active, driver_status, user_status
- **Roles**: ADMIN, DRIVER, COLLECTOR, CUSTOMER
- **Relations**: 1-N với Ticket (as customer/seller), Trip (as driver), Salary, LoyaltyPoint

#### Routes
- **Columns**: id, route_name, start_point, end_point, distance_km, status
- **Status**: ACTIVE, INACTIVE
- **Relations**: 1-N với Trip

#### Buses
- **Columns**: id, bus_number, license_plate, capacity, status
- **Status**: AVAILABLE, IN_USE, MAINTENANCE
- **Relations**: 1-N với Trip

#### Trips
- **Columns**: id, route_id, bus_id, driver_id, departure_time, arrival_time, status, revenue
- **Status**: SCHEDULED, ONGOING, COMPLETED, CANCELLED
- **Relations**: N-1 với Route, Bus, User; 1-N với Ticket, Revenue

#### Tickets
- **Columns**: id, trip_id, customer_id, seller_id, seat_number, price, status_ticket, user_status, issued_at
- **Status**: NOT_BOOKED, BOOKED, PAID, CANCELLED
- **Relations**: N-1 với Trip, User (customer), User (seller)

#### Revenues
- **Columns**: id, trip_id, total_amount, report_date
- **Relations**: N-1 với Trip

#### LoyaltyPoints
- **Columns**: id, user_id, points, transaction_type, description
- **Transaction Types**: EARN, REDEEM, EXPIRE
- **Relations**: N-1 với User

#### Salaries
- **Columns**: id, user_id, base_salary, bonus, deduction, total_salary, month, year
- **Relations**: N-1 với User

---

## 🔐 Security & Authentication

### JWT Authentication
- **Access Token**: Expiration 24 giờ
- **Refresh Token**: Dùng để làm mới access token
- **Secret Key**: Base64 encoded (cấu hình trong application.properties)
- **Header**: `Authorization: Bearer <token>`

### Authorization
- **@PublicApi**: API không cần authentication
- **@RoleRequired**: Kiểm tra quyền theo role
- **Spring Security**: Filter chain với JwtAuthFilter

### Session Management
- **Storage**: Redis Cluster
- **Timeout**: 30 phút
- **Key Pattern**: `session:{userId}`
- **Data**: userId, email, role, loginTime

---

## 📡 API Endpoints Summary

### Authentication (8082)
```
POST /api/auth/register      # Đăng ký
POST /api/auth/login         # Đăng nhập
PUT  /api/auth/update-password # Đổi mật khẩu
```

### Booking (8081)
```
POST /api/booking            # Đặt vé
```

### Users (8082)
```
GET    /api/users            # Danh sách users
GET    /api/users/{id}       # Chi tiết user
POST   /api/users            # Tạo user
PUT    /api/users/{id}       # Cập nhật user
DELETE /api/users/{id}       # Xóa user
```

### Routes (8082)
```
GET    /api/routes           # Danh sách tuyến
POST   /api/routes           # Tạo tuyến
PUT    /api/routes/{id}      # Cập nhật tuyến
DELETE /api/routes/{id}      # Xóa tuyến
```

### Buses (8082)
```
GET    /api/buses            # Danh sách xe
POST   /api/buses            # Thêm xe
PUT    /api/buses/{id}       # Cập nhật xe
DELETE /api/buses/{id}       # Xóa xe
```

### Trips (8082)
```
GET    /api/trips            # Danh sách chuyến
POST   /api/trips            # Tạo chuyến
PUT    /api/trips/{id}       # Cập nhật chuyến
DELETE /api/trips/{id}       # Xóa chuyến
```

### Tickets (8082)
```
GET    /api/tickets          # Danh sách vé
POST   /api/tickets          # Tạo vé
PUT    /api/tickets/{id}     # Cập nhật vé
DELETE /api/tickets/{id}     # Xóa vé
```

### Revenue (8082)
```
GET /api/revenue/report      # Báo cáo doanh thu
GET /api/revenue/by-route    # Doanh thu theo tuyến
GET /api/revenue/by-date     # Doanh thu theo ngày
GET /api/revenue/top-customers # Top khách hàng
```

### Loyalty Points (8082)
```
GET  /api/loyalty-points     # Điểm của user
POST /api/loyalty-points/earn # Tích điểm
POST /api/loyalty-points/redeem # Đổi điểm
```

### Salary (8082)
```
GET /api/salary              # Lương nhân viên
GET /api/salary/calculate    # Tính lương
GET /api/base-salary         # Lương cơ bản
```

---

## 🔄 Workflow Chính

### 1. Đặt Vé (Booking Flow)
```
1. Client gửi POST /api/booking (Booking Service)
   ↓
2. BookingProducer gửi message tới Kafka topic "order-events"
   ↓
3. RevenueConsumer (Manage Revenue Service) nhận message
   ↓
4. Xử lý logic đặt vé, lưu DB
   ↓
5. EmailService gửi email xác nhận
   ↓
6. Return response
```

### 2. Authentication Flow
```
1. Client gửi POST /api/auth/login
   ↓
2. AuthService validate credentials
   ↓
3. JwtUtil generate access token + refresh token
   ↓
4. Lưu session vào Redis
   ↓
5. Return tokens
```

### 3. API Request Flow (với JWT)
```
1. Client gửi request + JWT token
   ↓
2. JwtAuthFilter intercept request
   ↓
3. Validate token, extract user info
   ↓
4. Set SecurityContext
   ↓
5. Controller → Service → Repository → Database
   ↓
6. Return response
```

---

## 🚀 Tech Stack

### Backend
- **Language**: Java 21
- **Framework**: Spring Boot 3.3.6
- **Security**: Spring Security + JWT (jjwt 0.11.5)
- **Data Access**: Spring Data JPA + Hibernate
- **Database**: MySQL 8.0
- **Cache/Session**: Spring Data Redis + Redis Cluster
- **Messaging**: Spring Kafka
- **HTTP Client**: Spring Cloud OpenFeign
- **Email**: Spring Mail (Gmail SMTP)
- **Excel Export**: Apache POI 5.3.0
- **Utils**: Lombok, Jackson

### Infrastructure
- **Container**: Docker, Docker Compose
- **Message Broker**: Apache Kafka 7.5.0 (KRaft)
- **Cache**: Redis 7 Alpine (Cluster mode)
- **Database**: MySQL 8.0
- **Build Tool**: Maven 3.8+

### DevOps
- **Java Version**: 21 (Eclipse Temurin)
- **Build**: Maven multi-module project
- **Deployment**: Docker containers
- **Networking**: Docker networks (custom subnets)

---

## 📈 Performance & Scalability

### Caching Strategy
- Redis Cluster với 3 master nodes
- Cache: User profiles, Routes, Buses
- Session storage: Distributed sessions
- TTL: 30 phút cho session

### Message Queue
- Kafka với 3 brokers (high availability)
- Replication factor 3
- Min in-sync replicas: 2
- Idempotent producer
- Consumer group: booking-group
- Concurrency: 3

### Database
- HikariCP connection pool
- JPA lazy loading
- Indexed columns (PKs, FKs)
- Transactions với @Transactional

---

## 🎯 Features Roadmap

### ✅ Completed (Phase 1)
- JWT Authentication & Authorization
- User Management (CRUD)
- Route Management
- Bus Management
- Trip Management
- Ticket Management
- Revenue Tracking & Reports
- Loyalty Points System
- Salary Management
- Email Notifications
- Kafka Integration
- Redis Cluster (Cache/Session)
- Docker Infrastructure

### 🚧 In Progress (Phase 2)
- Payment Gateway Integration
- Real-time Seat Availability
- Mobile App API
- Admin Dashboard
- Advanced Analytics

### 📅 Planned (Phase 3)
- API Gateway (Kong/Nginx)
- Service Mesh (Istio)
- Kubernetes Deployment
- Monitoring (Prometheus/Grafana)
- CI/CD Pipeline (GitHub Actions)
- Elasticsearch for Logging
- MinIO for File Storage

---

## 📝 Project Metrics

### Code Structure
- **Modules**: 3 (common-library, booking_ticket, manage-revenue-ticket)
- **Entities**: 14+ (User, Route, Bus, Trip, Ticket, Revenue, etc.)
- **Services**: 20+ business services
- **Controllers**: 14+ REST controllers
- **Repositories**: 15+ JPA repositories

### Infrastructure
- **Docker Containers**: 11 (MySQL + 3 Kafka brokers + Kafka UI + 6 Redis nodes)
- **Networks**: 3 (kafka-net, redis-cluster-net, ticket-system-network)
- **Volumes**: Multiple persistent volumes for data

### Dependencies
- **Total Dependencies**: 20+ major libraries
- **Spring Boot Starters**: 10+
- **Third-party**: Kafka, Redis, MySQL, POI, JWT, OpenFeign

---

## 🔧 Configuration Files

### application.properties (Booking Service)
```properties
server.port=8081
spring.datasource.url=jdbc:mysql://localhost:3306/quan-ly-ban-hang
spring.kafka.bootstrap-servers=localhost:9092,localhost:9093,localhost:9094
spring.redis.cluster.nodes=173.20.0.11:7001,...,173.20.0.16:7006
```

### application.properties (Manage Revenue Service)
```properties
server.port=8082
spring.kafka.consumer.group-id=booking-group
spring.kafka.consumer.auto-offset-reset=earliest
spring.mail.host=smtp.gmail.com
spring.mail.port=587
```

---

## 📚 Documentation Links

- [📖 Hướng dẫn đầy đủ](PROJECT_INSTRUCTIONS.md) - Chi tiết về cài đặt, API, troubleshooting
- [🚀 Quick Start](QUICK_START.md) - Khởi động nhanh trong 5 phút
- [🏠 README](../README.md) - Tổng quan dự án

---

## 📞 Support & Contact

- **Email**: namsongc98@gmail.com
- **GitHub Issues**: Tạo issue trên repository
- **Documentation**: Xem các file .md trong thư mục .github/

---

## 🏆 Best Practices

### Code Quality
- ✅ Lombok annotations để giảm boilerplate code
- ✅ Builder pattern cho entities
- ✅ DTOs cho request/response
- ✅ Global exception handling
- ✅ Custom annotations (@PublicApi, @RoleRequired)
- ✅ Service layer separation
- ✅ Repository pattern

### Security
- ✅ JWT token authentication
- ✅ Password encryption (BCrypt)
- ✅ Role-based authorization
- ✅ Session management với Redis
- ✅ CORS configuration
- ✅ Custom security filters

### Microservices
- ✅ Shared common library
- ✅ Event-driven architecture (Kafka)
- ✅ Service-to-service communication (OpenFeign)
- ✅ Distributed caching (Redis Cluster)
- ✅ Independent deployment
- ✅ Docker containerization

---

**Last Updated**: April 9, 2026  
**Version**: 1.0.0  
**Status**: Production Ready ✅

