# Ticket System - Hệ thống Quản lý Bán Vé Xe Bus 🚌

[![Java](https://img.shields.io/badge/Java-21-orange.svg)](https://openjdk.java.net/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3.6-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Kafka](https://img.shields.io/badge/Apache%20Kafka-7.5.0-black.svg)](https://kafka.apache.org/)
[![Redis](https://img.shields.io/badge/Redis-7-red.svg)](https://redis.io/)
[![MySQL](https://img.shields.io/badge/MySQL-8.0-blue.svg)](https://www.mysql.com/)

## 🎯 Giới Thiệu

**Ticket System** là hệ thống microservices quản lý bán vé xe bus với kiến trúc phân tán, sử dụng Kafka, Redis Cluster, và MySQL.

### ✨ Tính Năng Chính
- 🎫 Đặt và quản lý vé xe bus
- 💰 Quản lý doanh thu và báo cáo
- 🚌 Quản lý tuyến đường, chuyến xe, xe bus
- 👥 Quản lý người dùng và phân quyền
- ⭐ Hệ thống điểm thưởng khách hàng
- 💵 Quản lý lương nhân viên
- 📧 Gửi email xác nhận
- 🔐 JWT Authentication & Authorization

## 🏗️ Kiến Trúc

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
Booking Service    Manage Revenue    Other Services
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
Notification Service   Analytics Service   Email Service
                  │
                  │
          Redis Cluster
        (Cache / Session)
                  │
                  │
               Database
          (MySQL Cluster)
```

## 📦 Cấu Trúc Dự Án

```
ticket-system/
├── .github/
│   └── PROJECT_INSTRUCTIONS.md       # 📚 Tài liệu chi tiết
├── common-library/                   # 📚 Thư viện chia sẻ
│   ├── config/                       # Cấu hình Redis, Kafka, Security
│   ├── security/                     # JWT, Spring Security
│   ├── exception/                    # Exception handling
│   └── util/                         # Utilities
├── booking_ticket/                   # 🎫 Service đặt vé (Port 8081)
│   ├── controller/                   # REST Controllers
│   ├── service/                      # Business logic
│   └── Dockerfile
├── manage-revenue-ticket/            # 💰 Service quản lý (Port 8082)
│   ├── controller/                   # REST Controllers
│   ├── service/                      # Business logic
│   ├── entity/                       # JPA Entities
│   ├── repository/                   # Data access
│   └── Dockerfile
└── Infrastructure/                   # 🐳 Docker Infrastructure
    ├── kafka/                        # Kafka 3-broker cluster
    ├── mysql/                        # MySQL database
    └── redis-cluster/                # Redis 6-node cluster
```

## 🚀 Cài Đặt Nhanh

### 1. Yêu Cầu
- Java 21
- Maven 3.8+
- Docker & Docker Compose
- 16GB RAM (recommended)

### 2. Clone & Setup
```bash
# Clone repository
git clone <repository-url>
cd ticket-system

# Tạo Docker networks
docker network create ticket-system-network
docker network create redis-cluster-net --subnet=173.20.0.0/16
```

### 3. Khởi động Infrastructure

```bash
# MySQL
cd Infrastructure/mysql && docker-compose up -d && cd ../..

# Kafka Cluster
cd Infrastructure/kafka && docker-compose up -d && cd ../..

# Redis Cluster
cd Infrastructure/redis-cluster && docker-compose up -d

# Init Redis Cluster (lần đầu)
docker exec -it redis-1 redis-cli --cluster create \
  173.20.0.11:7001 173.20.0.12:7002 173.20.0.13:7003 \
  173.20.0.14:7004 173.20.0.15:7005 173.20.0.16:7006 \
  --cluster-replicas 1

cd ../..
```

### 4. Build & Run Services

```bash
# Build project
mvn clean install

# Run Manage Revenue Service (Terminal 1)
cd manage-revenue-ticket
mvn spring-boot:run

# Run Booking Service (Terminal 2)
cd booking_ticket
mvn spring-boot:run
```

### 5. Verify
```bash
# Check services
curl http://localhost:8081/actuator/health  # Booking Service
curl http://localhost:8082/actuator/health  # Manage Revenue Service

# Kafka UI
open http://localhost:9000

# Check infrastructure
docker ps
```

## 📡 API Examples

### Đăng ký
```bash
curl -X POST http://localhost:8082/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "email": "user@example.com",
    "password": "password123",
    "role": "CUSTOMER"
  }'
```

### Đăng nhập
```bash
curl -X POST http://localhost:8082/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "email": "user@example.com",
    "password": "password123"
  }'
```

### Đặt vé
```bash
curl -X POST http://localhost:8081/api/booking \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <token>" \
  -d '{
    "tripId": 1,
    "customerEmail": "user@example.com",
    "seatNumber": 15,
    "price": 150000,
    "status": "BOOKED"
  }'
```

## 🔧 Configuration

### Services
| Service | Port | Description |
|---------|------|-------------|
| Booking Service | 8081 | Đặt vé |
| Manage Revenue | 8082 | Quản lý hệ thống |

### Infrastructure
| Component | Ports | Info |
|-----------|-------|------|
| MySQL | 3306 | Database: quan-ly-ban-hang |
| Kafka Broker 1 | 9092 | KRaft mode |
| Kafka Broker 2 | 9093 | KRaft mode |
| Kafka Broker 3 | 9094 | KRaft mode |
| Kafka UI | 9000 | Web Interface |
| Redis Node 1-6 | 7001-7006 | Cluster mode |

## 📚 Tài Liệu Chi Tiết

Xem tài liệu đầy đủ tại: [📄 PROJECT_INSTRUCTIONS.md](.github/PROJECT_INSTRUCTIONS.md)

Bao gồm:
- ✅ Kiến trúc hệ thống chi tiết
- ✅ Danh sách API đầy đủ
- ✅ Database schema
- ✅ Cấu hình môi trường
- ✅ Troubleshooting guide
- ✅ Deployment instructions

## 🛠️ Tech Stack

| Technology | Version | Purpose |
|------------|---------|---------|
| Java | 21 | Language |
| Spring Boot | 3.3.6 | Framework |
| Spring Security | 3.3.6 | Security |
| JWT | 0.11.5 | Authentication |
| MySQL | 8.0 | Database |
| Redis | 7 | Cache/Session |
| Kafka | 7.5.0 | Message Queue |
| Docker | Latest | Container |
| OpenFeign | 2023.0.1 | HTTP Client |

## 🐛 Troubleshooting

### Service không khởi động được
```bash
# Check logs
docker logs <container-name>

# Restart infrastructure
cd Infrastructure/<service>
docker-compose restart
```

### Port đã được sử dụng
```bash
# Check port usage
lsof -i :8081
lsof -i :8082

# Kill process
kill -9 <PID>
```

### Redis cluster lỗi
```bash
# Check cluster status
docker exec -it redis-1 redis-cli -c -h 173.20.0.11 -p 7001 cluster info

# Re-init cluster
docker exec -it redis-1 redis-cli --cluster create \
  173.20.0.11:7001 173.20.0.12:7002 173.20.0.13:7003 \
  173.20.0.14:7004 173.20.0.15:7005 173.20.0.16:7006 \
  --cluster-replicas 1
```

## 📞 Liên Hệ

- **Email**: namsongc98@gmail.com
- **Issues**: [GitHub Issues](https://github.com/your-repo/issues)

## 📄 License

MIT License

---

**Made with ❤️ by Ticket System Team**
