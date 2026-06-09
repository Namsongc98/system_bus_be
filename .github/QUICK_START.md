 năm# 🚀 Quick Start Guide

Hướng dẫn khởi động nhanh Ticket System trong 5 phút.

## ⚡ Khởi Động Nhanh (5 phút)

### Bước 1: Prerequisites (30s)
```bash
# Kiểm tra Java version
java -version  # Cần Java 21

# Kiểm tra Maven
mvn -version   # Cần Maven 3.8+

# Kiểm tra Docker
docker --version
docker-compose --version
```

### Bước 2: Setup Networks (30s)
```bash
docker network create ticket-system-network
docker network create redis-cluster-net --subnet=173.20.0.0/16
```

### Bước 3: Start Infrastructure (3 phút)

#### MySQL (30s)
```bash
cd Infrastructure/mysql
docker-compose up -d
cd ../..
```

#### Kafka (1 phút)
```bash
cd Infrastructure/kafka
docker-compose up -d
cd ../..
```

#### Redis Cluster (1 phút)
```bash
cd Infrastructure/redis-cluster
docker-compose up -d

# Init cluster (chỉ lần đầu)
docker exec -it redis-1 redis-cli --cluster create \
  173.20.0.11:7001 173.20.0.12:7002 173.20.0.13:7003 \
  173.20.0.14:7004 173.20.0.15:7005 173.20.0.16:7006 \
  --cluster-replicas 1 --cluster-yes

cd ../..
```

### Bước 4: Build Project (1 phút)
```bash
mvn clean install -DskipTests
```

### Bước 5: Run Services (30s)

**Terminal 1: Manage Revenue Service**
```bash
cd manage-revenue-ticket
mvn spring-boot:run
```

**Terminal 2: Booking Service**
```bash
cd booking_ticket
mvn spring-boot:run
```

---

## ✅ Verify Installation

### 1. Check Infrastructure
```bash
# Check Docker containers
docker ps

# Should see:
# - mysql-ticket-system
# - broker1, broker2, broker3
# - kafka-ui
# - redis-1 to redis-6
```

### 2. Check Services
```bash
# Booking Service (8081)
curl http://localhost:8081/actuator/health

# Manage Revenue Service (8082)
curl http://localhost:8082/actuator/health

# Expected: {"status":"UP"}
```

### 3. Check Kafka UI
```bash
open http://localhost:9000
# hoặc
curl http://localhost:9000
```

### 4. Check Redis Cluster
```bash
docker exec -it redis-1 redis-cli -c -h 173.20.0.11 -p 7001 ping
# Expected: PONG

docker exec -it redis-1 redis-cli -c -h 173.20.0.11 -p 7001 cluster info
# Expected: cluster_state:ok
```

---

## 🧪 Test APIs

### 1. Đăng ký tài khoản
```bash
curl -X POST http://localhost:8082/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "email": "test@example.com",
    "password": "password123",
    "role": "CUSTOMER"
  }'
```

**Expected Response:**
```json
{
  "code": 201,
  "message": "Register successfully",
  "data": {
    "accessToken": "eyJhbGciOiJIUzI1NiIs...",
    "refreshToken": "eyJhbGciOiJIUzI1NiIs..."
  }
}
```

### 2. Đăng nhập
```bash
curl -X POST http://localhost:8082/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "email": "test@example.com",
    "password": "password123"
  }'
```

**Save the token:**
```bash
export TOKEN="<access-token-from-response>"
```

### 3. Đặt vé (cần tạo dữ liệu trước)
```bash
curl -X POST http://localhost:8081/api/booking \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{
    "tripId": 1,
    "customerEmail": "test@example.com",
    "seatNumber": 15,
    "price": 150000,
    "status": "BOOKED"
  }'
```

---

## 🎯 Next Steps

### 1. Tạo dữ liệu mẫu

**Tạo Route:**
```bash
curl -X POST http://localhost:8082/api/routes \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{
    "routeName": "Hà Nội - Hải Phòng",
    "startPoint": "Hà Nội",
    "endPoint": "Hải Phòng",
    "distanceKm": 120,
    "status": "ACTIVE"
  }'
```

**Tạo Bus:**
```bash
curl -X POST http://localhost:8082/api/buses \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{
    "busNumber": "BUS-001",
    "licensePlate": "29A-12345",
    "capacity": 40,
    "status": "AVAILABLE"
  }'
```

**Tạo Trip:**
```bash
curl -X POST http://localhost:8082/api/trips \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{
    "routeId": 1,
    "busId": 1,
    "driverId": 1,
    "departureTime": "2026-04-10T08:00:00",
    "arrivalTime": "2026-04-10T10:30:00",
    "status": "SCHEDULED"
  }'
```

### 2. Xem Kafka Messages
- Truy cập: http://localhost:9000
- Topics → order-events
- Messages

### 3. Xem Redis Data
```bash
docker exec -it redis-1 redis-cli -c -h 173.20.0.11 -p 7001

# List all keys
> KEYS *

# Get session data
> GET session:1
```

### 4. Xem Database
```bash
docker exec -it mysql-ticket-system mysql -uroot -p123456789 quan-ly-ban-hang

mysql> SHOW TABLES;
mysql> SELECT * FROM users;
mysql> SELECT * FROM routes;
mysql> SELECT * FROM trips;
```

---

## 🐛 Common Issues

### Port already in use
```bash
# Find process
lsof -i :8081
lsof -i :8082

# Kill process
kill -9 <PID>
```

### Cannot connect to MySQL
```bash
# Check container
docker ps | grep mysql

# Restart
docker restart mysql-ticket-system

# Check logs
docker logs mysql-ticket-system
```

### Redis cluster not initialized
```bash
# Init cluster
docker exec -it redis-1 redis-cli --cluster create \
  173.20.0.11:7001 173.20.0.12:7002 173.20.0.13:7003 \
  173.20.0.14:7004 173.20.0.15:7005 173.20.0.16:7006 \
  --cluster-replicas 1 --cluster-yes
```

### Kafka connection failed
```bash
# Check brokers
docker ps | grep broker

# Restart Kafka
cd Infrastructure/kafka
docker-compose restart
```

---

## 📚 More Documentation

- [📄 Full Documentation](PROJECT_INSTRUCTIONS.md)
- [🏠 README](../README.md)

---

## 💡 Tips

### 1. Development Mode
```bash
# Skip tests
mvn clean install -DskipTests

# Run with debug
mvn spring-boot:run -Dspring-boot.run.jvmArguments="-Xdebug -Xrunjdwp:transport=dt_socket,server=y,suspend=n,address=5005"
```

### 2. Stop All Services
```bash
# Stop Spring Boot services (Ctrl+C in terminals)

# Stop infrastructure
cd Infrastructure/mysql && docker-compose down && cd ../..
cd Infrastructure/kafka && docker-compose down && cd ../..
cd Infrastructure/redis-cluster && docker-compose down && cd ../..
```

### 3. Clean Up
```bash
# Remove containers and volumes
docker-compose down -v

# Remove networks
docker network rm ticket-system-network
docker network rm redis-cluster-net

# Clean Maven
mvn clean
```

---

**Happy Coding! 🎉**

