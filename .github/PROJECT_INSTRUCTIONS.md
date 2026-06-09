# Ticket System - Hệ thống Quản lý Bán Vé Xe Bus

## 📋 Tổng Quan Dự Án

**Ticket System** là một hệ thống microservices được xây dựng bằng Spring Boot để quản lý bán vé xe bus, doanh thu, và các chức năng liên quan. Dự án sử dụng kiến trúc phân tán với Kafka làm message broker, Redis Cluster để caching/session, và MySQL làm database chính.

### 🎯 Mục Đích
- Quản lý đặt và bán vé xe bus
- Quản lý doanh thu và báo cáo
- Quản lý tuyến đường, chuyến xe, xe bus
- Hệ thống điểm thưởng khách hàng (Loyalty Points)
- Quản lý nhân viên và lương
- Xác thực và phân quyền người dùng

---

## 🏗️ Kiến Trúc Hệ Thống

```
                         Client
                           │
                           │
                     API Gateway
                    (Kong / Nginx)
                           │
                     Load Balancer
                           │
        ┌──────────────────┼──────────────────┐
        │                  │                  │
Booking Service   Manage Revenue    Other Services
    (8081)         Service (8082)
        │                  │                  │
        │                  │                  │
        └────────────── Event Producer ──────┘
                            │
                            │
                      Kafka Cluster
                  ┌────────┼────────┐
                  │        │        │
        Order Topic  Payment Topic  Notification Topic
                  │        │        │
        ┌─────────┘        │        └─────────┐
        │                  │                  │
Notification Service  Analytics Service  Email Service
                  │
                  │
          Redis Cluster
        (Cache / Session)
                  │
                  │
               Database
          (MySQL Cluster)
```

---

## 📦 Cấu Trúc Dự Án

### 1. **common-library**
Thư viện chia sẻ giữa các microservices, bao gồm:

#### Core Components:
- **Security & Authentication**
  - `SecurityConfig.java` - Cấu hình Spring Security
  - `JwtAuthFilter.java` - Filter xác thực JWT
  - `JwtUtil.java` - Tiện ích tạo và xác minh JWT token
  
- **Exception Handling**
  - `GlobalExceptionHandler.java` - Xử lý exception toàn cục
  - `CustomAuthEntryPoint.java` - Xử lý lỗi 401
  - `CustomAccessDeniedHandler.java` - Xử lý lỗi 403
  - `ResourceNotFoundException.java` - Custom exception
  - `UnauthorizedRoleException.java` - Custom exception

- **Configuration**
  - `KafkaProducerConfig.java` - Cấu hình Kafka Producer
  - `KafkaConsumerConfig.java` - Cấu hình Kafka Consumer
  - `RedisConfig.java` - Cấu hình Redis Cluster
  - `SessionConfig.java` - Cấu hình Session Management

- **Annotations**
  - `@PublicApi` - Đánh dấu API công khai (không cần xác thực)
  - `@RoleRequired` - Kiểm tra quyền truy cập

- **Enums**
  - `UserRole` - ADMIN, DRIVER, COLLECTOR, CUSTOMER
  - `CustomerStatus` - Trạng thái khách hàng

#### Dependencies:
- Spring Boot Starter Web
- Spring Security + JWT (jjwt 0.11.5)
- Spring Data JPA
- Spring Data Redis + Session
- Spring Kafka
- Spring Cloud OpenFeign
- Apache POI (Excel export)
- MySQL Connector
- Lombok
- Validation

---

### 2. **booking_ticket** (Port 8081)
Microservice xử lý đặt vé

#### Chức năng chính:
- **Đặt vé qua API**
  - Endpoint: `POST /api/booking`
  - Gửi yêu cầu đặt vé vào Kafka topic `order-events`

#### Components:
- `BookingTicketApplication.java` - Main application
- `BookingController.java` - REST Controller
- `BookingProducer.java` - Kafka Producer gửi booking events
- `TicketService.java` - Business logic
- `RevenueClient.java` - Feign Client gọi Manage Revenue Service

#### Entities:
- Ticket, Trip, Route, User, LoyaltyPoint, BaseSalary

#### Configuration:
- **Database**: MySQL `quan-ly-ban-hang`
- **Kafka**: 3 brokers (9092, 9093, 9094)
- **Redis Cluster**: 6 nodes (7001-7006)
- **Email**: Gmail SMTP

#### Docker:
- Dockerfile sử dụng `eclipse-temurin:21-jdk`
- Docker Compose expose port 8081

---

### 3. **manage-revenue-ticket** (Port 8082)
Microservice chính quản lý doanh thu và hệ thống

#### Chức năng chính:

##### Authentication & Authorization
- `POST /api/auth/register` - Đăng ký tài khoản
- `POST /api/auth/login` - Đăng nhập (lưu session vào Redis)
- `PUT /api/auth/update-password` - Đổi mật khẩu

##### Quản lý Người dùng (Users)
- CRUD operations cho users
- Phân quyền: ADMIN, DRIVER, COLLECTOR, CUSTOMER
- Quản lý profile người dùng

##### Quản lý Tuyến đường (Routes)
- CRUD operations
- Trạng thái: ACTIVE, INACTIVE
- Thông tin: startPoint, endPoint, distanceKm

##### Quản lý Xe Bus (Buses)
- CRUD operations
- Theo dõi trạng thái xe
- Quản lý capacity (số chỗ ngồi)

##### Quản lý Chuyến Xe (Trips)
- CRUD operations
- Liên kết với Route, Bus, Driver
- Trạng thái: SCHEDULED, ONGOING, COMPLETED, CANCELLED
- Thời gian: departureTime, arrivalTime
- Theo dõi doanh thu từng chuyến

##### Quản lý Vé (Tickets)
- CRUD operations
- Trạng thái: NOT_BOOKED, BOOKED, PAID, CANCELLED
- Liên kết customer, seller, trip
- Số ghế, giá vé

##### Quản lý Doanh Thu (Revenue)
- Báo cáo doanh thu theo trip
- Báo cáo doanh thu theo route
- Thống kê doanh thu theo ngày/tháng
- Top customers

##### Hệ thống Điểm Thưởng (Loyalty)
- `BaseLoyaltyPoints` - Quy tắc tích điểm
- `LoyaltyPoint` - Điểm thưởng của khách hàng
- `LoyaltyReward` - Phần thưởng đổi điểm

##### Quản lý Lương (Salary)
- `BaseSalary` - Mức lương cơ bản theo role
- `Salary` - Chi tiết lương nhân viên
- Tính lương theo tháng

##### Kafka Consumer
- `RevenueConsumer` - Lắng nghe topic `order-events`
- `EmailConfirmTicketConsumer` - Gửi email xác nhận

##### Services khác
- `EmailService` - Gửi email qua Gmail SMTP
- `FileService` - Upload/download files
- `AuditLogService` - Ghi log hành động

#### Entities & Relationships:

**Core Entities:**
```
User (1) ----< (N) Ticket [as customer]
User (1) ----< (N) Ticket [as seller]
User (1) ----< (N) Trip [as driver]
Route (1) ----< (N) Trip
Buses (1) ----< (N) Trip
Trip (1) ----< (N) Ticket
Trip (1) ----< (N) Revenue
User (1) ----< (N) LoyaltyPoint
User (1) ----< (N) Salary
```

**Enums:**
- `UserRole`: ADMIN, DRIVER, COLLECTOR, CUSTOMER
- `CustomerStatus`: ACTIVE, INACTIVE, BLOCKED
- `DriverStatus`: AVAILABLE, ON_TRIP, OFF_DUTY
- `BusStatus`: AVAILABLE, IN_USE, MAINTENANCE
- `RouteStatus`: ACTIVE, INACTIVE
- `TripStatus`: SCHEDULED, ONGOING, COMPLETED, CANCELLED
- `TicketStatus`: NOT_BOOKED, BOOKED, PAID, CANCELLED
- `TransactionType`: EARN, REDEEM, EXPIRE
- `BaseLoyaltyPointStatus`: ACTIVE, INACTIVE

#### Configuration:
- **Database**: MySQL `quan-ly-ban-hang`
- **Kafka**: Consumer group `booking-group`
- **Redis Cluster**: Session + Cache
- **Email**: Gmail SMTP
- **File Storage**: MinIO (optional)

---

## 🚀 Infrastructure

### 1. MySQL (Port 3306)
```yaml
Database: quan-ly-ban-hang
Username: root
Password: 123456789
```

**Docker Compose:**
```bash
cd Infrastructure/mysql
docker-compose up -d
```

**Network:** `ticket-system-network`

---

### 2. Kafka Cluster (3 Brokers)

**Architecture:**
- **Broker 1**: localhost:9092
- **Broker 2**: localhost:9093
- **Broker 3**: localhost:9094
- **Kafka UI**: localhost:9000

**Configuration:**
- KRaft mode (không cần Zookeeper)
- Replication Factor: 3
- Min In-Sync Replicas: 2
- Idempotent Producer: enabled
- Transactions: enabled

**Topics:**
- `order-events` - Đặt vé
- `payment-events` - Thanh toán (future)
- `notification-events` - Thông báo (future)

**Start Kafka:**
```bash
cd Infrastructure/kafka
docker-compose up -d
```

**Access Kafka UI:**
```
http://localhost:9000
```

---

### 3. Redis Cluster (6 Nodes)

**Architecture:**
- 3 Master nodes: 7001, 7002, 7003
- 3 Replica nodes: 7004, 7005, 7006
- Network: `redis-cluster-net` (173.20.0.0/16)

**Static IPs:**
```
redis-node-1: 173.20.0.11:7001
redis-node-2: 173.20.0.12:7002
redis-node-3: 173.20.0.13:7003
redis-node-4: 173.20.0.14:7004
redis-node-5: 173.20.0.15:7005
redis-node-6: 173.20.0.16:7006
```

**Start Redis Cluster:**
```bash
cd Infrastructure/redis-cluster
docker-compose up -d
```

**Init Cluster (first time only):**
```bash
docker exec -it redis-1 redis-cli --cluster create \
  173.20.0.11:7001 \
  173.20.0.12:7002 \
  173.20.0.13:7003 \
  173.20.0.14:7004 \
  173.20.0.15:7005 \
  173.20.0.16:7006 \
  --cluster-replicas 1
```

**Test Connection:**
```bash
docker exec -it redis-1 redis-cli -c -h 173.20.0.11 -p 7001
> cluster info
> cluster nodes
```

---

## 🛠️ Cài Đặt và Chạy

### Prerequisites
- Java 21
- Maven 3.8+
- Docker & Docker Compose
- Git

### Bước 1: Clone Repository
```bash
git clone <repository-url>
cd ticket-system
```

### Bước 2: Tạo Network cho Docker
```bash
docker network create ticket-system-network
docker network create redis-cluster-net --subnet=173.20.0.0/16
```

### Bước 3: Khởi động Infrastructure

#### Start MySQL
```bash
cd Infrastructure/mysql
docker-compose up -d
cd ../..
```

#### Start Kafka Cluster
```bash
cd Infrastructure/kafka
docker-compose up -d
cd ../..
```

#### Start Redis Cluster
```bash
cd Infrastructure/redis-cluster
docker-compose up -d

# Init cluster (lần đầu tiên)
docker exec -it redis-1 redis-cli --cluster create \
  173.20.0.11:7001 173.20.0.12:7002 173.20.0.13:7003 \
  173.20.0.14:7004 173.20.0.15:7005 173.20.0.16:7006 \
  --cluster-replicas 1

cd ../..
```

### Bước 4: Build Project
```bash
# Build toàn bộ project
mvn clean install

# Hoặc build từng module
mvn clean install -pl common-library
mvn clean install -pl booking_ticket
mvn clean install -pl manage-revenue-ticket
```

### Bước 5: Chạy Services

#### Option 1: Chạy bằng Maven
```bash
# Terminal 1 - Manage Revenue Service
cd manage-revenue-ticket
mvn spring-boot:run

# Terminal 2 - Booking Service
cd booking_ticket
mvn spring-boot:run
```

#### Option 2: Chạy bằng JAR
```bash
# Manage Revenue Service
java -jar manage-revenue-ticket/target/manage-revenue-ticket-1.0.0.jar

# Booking Service
java -jar booking_ticket/target/booking_ticket-1.0.0.jar
```

#### Option 3: Chạy bằng Docker

**Build Docker Images:**
```bash
# Booking Service
cd booking_ticket
docker build -t booking-ticket:1.0.0 .

# Manage Revenue Service
cd ../manage-revenue-ticket
docker build -t manage-revenue-ticket:1.0.0 .
```

**Run with Docker Compose:**
```bash
cd booking_ticket
docker-compose up -d

cd ../manage-revenue-ticket
docker-compose up -d
```

### Bước 6: Verify Services

**Check Services Status:**
```bash
# MySQL
docker ps | grep mysql

# Kafka
docker ps | grep broker
curl http://localhost:9000

# Redis
docker exec -it redis-1 redis-cli -c -h 173.20.0.11 -p 7001 ping

# Spring Boot Services
curl http://localhost:8081/actuator/health
curl http://localhost:8082/actuator/health
```

---

## 📡 API Endpoints

### Authentication (Port 8082)

#### Register
```http
POST /api/auth/register
Content-Type: application/json

{
  "email": "user@example.com",
  "password": "password123",
  "role": "CUSTOMER"
}
```

#### Login
```http
POST /api/auth/login
Content-Type: application/json

{
  "email": "user@example.com",
  "password": "password123"
}
```

**Response:**
```json
{
  "code": 200,
  "message": "Login successfully",
  "data": {
    "accessToken": "eyJhbGciOiJIUzI1NiIs...",
    "refreshToken": "eyJhbGciOiJIUzI1NiIs..."
  }
}
```

### Booking Service (Port 8081)

#### Create Booking
```http
POST /api/booking
Content-Type: application/json
Authorization: Bearer <token>

{
  "tripId": 1,
  "customerEmail": "customer@example.com",
  "seatNumber": 15,
  "price": 150000,
  "status": "BOOKED"
}
```

### Manage Revenue Service (Port 8082)

#### Users
```http
GET    /api/users              # Lấy danh sách users
GET    /api/users/{id}         # Lấy user theo ID
POST   /api/users              # Tạo user mới
PUT    /api/users/{id}         # Cập nhật user
DELETE /api/users/{id}         # Xóa user
```

#### Routes
```http
GET    /api/routes             # Lấy danh sách routes
GET    /api/routes/{id}        # Lấy route theo ID
POST   /api/routes             # Tạo route mới
PUT    /api/routes/{id}        # Cập nhật route
DELETE /api/routes/{id}        # Xóa route
```

#### Trips
```http
GET    /api/trips              # Lấy danh sách trips
GET    /api/trips/{id}         # Lấy trip theo ID
POST   /api/trips              # Tạo trip mới
PUT    /api/trips/{id}         # Cập nhật trip
DELETE /api/trips/{id}         # Xóa trip
```

#### Tickets
```http
GET    /api/tickets            # Lấy danh sách tickets
GET    /api/tickets/{id}       # Lấy ticket theo ID
POST   /api/tickets            # Tạo ticket mới
PUT    /api/tickets/{id}       # Cập nhật ticket
DELETE /api/tickets/{id}       # Xóa ticket
```

#### Revenue
```http
GET    /api/revenue/report     # Báo cáo doanh thu
GET    /api/revenue/by-route   # Doanh thu theo route
GET    /api/revenue/by-date    # Doanh thu theo ngày
GET    /api/revenue/top-customers # Top khách hàng
```

#### Buses
```http
GET    /api/buses              # Lấy danh sách buses
GET    /api/buses/{id}         # Lấy bus theo ID
POST   /api/buses              # Tạo bus mới
PUT    /api/buses/{id}         # Cập nhật bus
DELETE /api/buses/{id}         # Xóa bus
```

#### Loyalty Points
```http
GET    /api/loyalty-points           # Điểm thưởng của user
POST   /api/loyalty-points/earn      # Tích điểm
POST   /api/loyalty-points/redeem    # Đổi điểm
GET    /api/base-loyalty-points      # Quy tắc tích điểm
```

#### Salary
```http
GET    /api/salary                   # Lương nhân viên
GET    /api/salary/calculate         # Tính lương
GET    /api/base-salary              # Mức lương cơ bản
```

---

## 🔐 Security

### JWT Authentication
- **Access Token**: Có hiệu lực 24 giờ
- **Refresh Token**: Có hiệu lực lâu hơn (để refresh access token)
- **Secret Key**: Cấu hình trong `application.properties`

### Authorization
- **@PublicApi**: Endpoint công khai (không cần token)
- **@RoleRequired**: Kiểm tra quyền theo role
- **Roles**: ADMIN, DRIVER, COLLECTOR, CUSTOMER

### Session Management
- Sử dụng Redis để lưu session
- Timeout: 30 phút
- Key pattern: `session:{userId}`

---

## 📊 Database Schema

### Core Tables

#### users
```sql
id, email, password, role, is_active, 
driver_status, user_status, 
created_at, updated_at
```

#### routes
```sql
id, route_name, start_point, end_point, 
distance_km, status, 
created_at, updated_at
```

#### buses
```sql
id, bus_number, license_plate, capacity, 
status, created_at, updated_at
```

#### trips
```sql
id, route_id, bus_id, driver_id,
departure_time, arrival_time, status, revenue,
created_at, updated_at
```

#### tickets
```sql
id, trip_id, customer_id, seller_id,
seat_number, price, status_ticket, user_status,
issued_at, created_at, updated_at
```

#### revenues
```sql
id, trip_id, total_amount, report_date,
created_at, updated_at
```

#### loyalty_points
```sql
id, user_id, points, transaction_type,
description, created_at
```

#### salaries
```sql
id, user_id, base_salary, bonus, deduction,
total_salary, month, year, created_at
```

---

## 🧪 Testing

### Unit Tests
```bash
mvn test
```

### Integration Tests
```bash
mvn verify
```

### Manual Testing với cURL

**Register:**
```bash
curl -X POST http://localhost:8082/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "email": "test@example.com",
    "password": "password123",
    "role": "CUSTOMER"
  }'
```

**Login:**
```bash
curl -X POST http://localhost:8082/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "email": "test@example.com",
    "password": "password123"
  }'
```

**Booking Ticket:**
```bash
curl -X POST http://localhost:8081/api/booking \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <your-token>" \
  -d '{
    "tripId": 1,
    "customerEmail": "test@example.com",
    "seatNumber": 15,
    "price": 150000,
    "status": "BOOKED"
  }'
```

---

## 🐛 Troubleshooting

### Issue 1: Cannot connect to MySQL
```bash
# Check MySQL container
docker ps | grep mysql

# Check logs
docker logs mysql-ticket-system

# Restart MySQL
cd Infrastructure/mysql
docker-compose restart
```

### Issue 2: Kafka connection failed
```bash
# Check Kafka brokers
docker ps | grep broker

# Check logs
docker logs broker1
docker logs broker2
docker logs broker3

# Restart Kafka
cd Infrastructure/kafka
docker-compose restart
```

### Issue 3: Redis cluster not working
```bash
# Check Redis nodes
docker ps | grep redis

# Check cluster status
docker exec -it redis-1 redis-cli -c -h 173.20.0.11 -p 7001 cluster info

# Re-initialize cluster
docker exec -it redis-1 redis-cli --cluster create \
  173.20.0.11:7001 173.20.0.12:7002 173.20.0.13:7003 \
  173.20.0.14:7004 173.20.0.15:7005 173.20.0.16:7006 \
  --cluster-replicas 1
```

### Issue 4: Port already in use
```bash
# Check which process is using the port
lsof -i :8081
lsof -i :8082

# Kill the process
kill -9 <PID>
```

### Issue 5: Maven build failed
```bash
# Clean and rebuild
mvn clean install -U

# Skip tests
mvn clean install -DskipTests

# Build specific module
mvn clean install -pl common-library -am
```

---

## 📝 Configuration Files

### application.properties (booking_ticket)
```properties
spring.application.name=booking_ticket
server.port=8081

# Database
spring.datasource.url=jdbc:mysql://localhost:3306/quan-ly-ban-hang
spring.datasource.username=root
spring.datasource.password=123456789

# Kafka
spring.kafka.bootstrap-servers=localhost:9092,localhost:9093,localhost:9094
spring.kafka.producer.acks=all
spring.kafka.producer.enable-idempotence=true

# Redis Cluster
spring.redis.cluster.nodes=173.20.0.11:7001,...,173.20.0.16:7006
```

### application.properties (manage-revenue-ticket)
```properties
spring.application.name=manage-revenue-ticket
server.port=8082

# Database (same as booking_ticket)

# Kafka Consumer
spring.kafka.consumer.group-id=booking-group
spring.kafka.consumer.auto-offset-reset=earliest

# Redis Cluster (same as booking_ticket)

# Email
spring.mail.host=smtp.gmail.com
spring.mail.port=587
```

---

## 🔄 Workflow

### 1. Đặt vé (Booking Flow)
```
Client → POST /api/booking (Booking Service)
  ↓
Booking Service → Kafka Producer → order-events topic
  ↓
Kafka → Manage Revenue Service (Consumer)
  ↓
Process booking → Save to DB
  ↓
Send email confirmation
```

### 2. Authentication Flow
```
Client → POST /api/auth/login
  ↓
Validate credentials
  ↓
Generate JWT tokens (access + refresh)
  ↓
Save session to Redis
  ↓
Return tokens to client
```

### 3. API Request Flow (with JWT)
```
Client → API Request + JWT Token
  ↓
JwtAuthFilter → Validate token
  ↓
Extract user info → Set SecurityContext
  ↓
Controller → Service → Repository → Database
  ↓
Return response
```

---

## 📈 Performance & Scalability

### Caching Strategy
- **Redis Cluster**: 6 nodes (3 masters + 3 replicas)
- **Cache Types**:
  - Session data
  - Frequently accessed data (routes, buses)
  - User profiles

### Message Queue
- **Kafka Cluster**: 3 brokers
- **Replication Factor**: 3
- **Use Cases**:
  - Async booking processing
  - Email notifications
  - Analytics events

### Database
- **Connection Pool**: HikariCP (default)
- **JPA**: Hibernate with lazy loading
- **Indexes**: Primary keys, foreign keys

### Monitoring
- **Spring Actuator**: `/actuator/health`, `/actuator/metrics`
- **Kafka UI**: http://localhost:9000
- **Logs**: Console + File (if configured)

---

## 🚦 Environment Variables

### Production Deployment
```bash
# Database
export SPRING_DATASOURCE_URL=jdbc:mysql://prod-db:3306/quan-ly-ban-hang
export SPRING_DATASOURCE_USERNAME=prod_user
export SPRING_DATASOURCE_PASSWORD=prod_password

# Kafka
export SPRING_KAFKA_BOOTSTRAP_SERVERS=kafka-1:9092,kafka-2:9092,kafka-3:9092

# Redis
export SPRING_REDIS_CLUSTER_NODES=redis-1:7001,...,redis-6:7006

# JWT
export JWT_SECRET=<your-production-secret>
export JWT_EXPIRATION=86400000

# Email
export SPRING_MAIL_USERNAME=<your-email>
export SPRING_MAIL_PASSWORD=<your-app-password>
```

---

## 🤝 Contributing

### Git Workflow
```bash
# Create feature branch
git checkout -b feature/your-feature-name

# Make changes and commit
git add .
git commit -m "feat: add new feature"

# Push to remote
git push origin feature/your-feature-name

# Create Pull Request on GitHub
```

### Commit Message Convention
- `feat:` - New feature
- `fix:` - Bug fix
- `docs:` - Documentation changes
- `refactor:` - Code refactoring
- `test:` - Adding tests
- `chore:` - Maintenance tasks

---

## 📞 Support

### Contact
- **Email**: namsongc98@gmail.com
- **GitHub Issues**: Create an issue on GitHub repository

### Documentation
- Spring Boot: https://spring.io/projects/spring-boot
- Kafka: https://kafka.apache.org/documentation/
- Redis: https://redis.io/docs/

---

## 📄 License

This project is licensed under the MIT License.

---

## 🎯 Roadmap

### Phase 1 (Current)
- ✅ Basic authentication & authorization
- ✅ CRUD operations for all entities
- ✅ Kafka integration for booking
- ✅ Redis cluster for caching/session
- ✅ Email notifications

### Phase 2 (Planned)
- [ ] Payment gateway integration
- [ ] Real-time seat availability
- [ ] Mobile app support
- [ ] Admin dashboard
- [ ] Advanced analytics

### Phase 3 (Future)
- [ ] API Gateway (Kong/Nginx)
- [ ] Service mesh (Istio)
- [ ] Kubernetes deployment
- [ ] Monitoring & Alerting (Prometheus/Grafana)
- [ ] CI/CD pipeline

---

## 📚 Tech Stack Summary

| Technology | Version | Purpose |
|------------|---------|---------|
| Java | 21 | Programming Language |
| Spring Boot | 3.3.6 | Application Framework |
| Spring Security | 3.3.6 | Authentication & Authorization |
| JWT | 0.11.5 | Token-based Auth |
| MySQL | 8.0 | Relational Database |
| Redis | 7 Alpine | Caching & Session |
| Kafka | 7.5.0 | Message Broker |
| Docker | Latest | Containerization |
| Maven | 3.8+ | Build Tool |
| Lombok | Latest | Code Generation |
| OpenFeign | 2023.0.1 | HTTP Client |

---

## 🎨 Code Style

### Java Coding Standards
- Follow Google Java Style Guide
- Use Lombok annotations (@Getter, @Setter, @Builder, etc.)
- Use meaningful variable and method names
- Add comments for complex logic
- Use DTOs for API requests/responses

### Project Structure
```
src/main/java/com/ticket_system/
├── controller/     # REST Controllers
├── service/        # Business Logic
├── repository/     # Data Access Layer
├── entity/         # JPA Entities
├── Dto/           # Data Transfer Objects
├── Enum/          # Enumerations
├── config/        # Configurations
├── exception/     # Custom Exceptions
├── interceptor/   # Interceptors
└── projection/    # JPA Projections
```

---

**Last Updated**: April 2026  
**Version**: 1.0.0  
**Author**: Ticket System Development Team

