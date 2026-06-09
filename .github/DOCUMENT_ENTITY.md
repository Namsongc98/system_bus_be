Fields:
- tripId (chuyến xe)
- seatNumber (số ghế: 1-40)
- price (giá vé: VND)
- customerEmail
- status: NOT_BOOKED, BOOKED, PENDING, PAID, CANCELLED

Related Entities:
- Trip: route, bus, driver, departureTime, arrivalTime
- Route: routeName, startPoint, endPoint, distanceKm
- Bus: plateNumber, capacity (40 chỗ)
- User: email, role

APIs:
POST /api/booking → Create booking (async via Kafka)
GET /api/trips → List available trips
GET /api/tickets/my-tickets → User's tickets
PUT /api/tickets/{id}/cancel → Cancel ticketFields:
- tripId (chuyến xe)
- seatNumber (số ghế: 1-40)
- price (giá vé: VND)
- customerEmail
- status: NOT_BOOKED, BOOKED, PENDING, PAID, CANCELLED

Related Entities:
- Trip: route, bus, driver, departureTime, arrivalTime
- Route: routeName, startPoint, endPoint, distanceKm
- Bus: plateNumber, capacity (40 chỗ)
- User: email, role

APIs:
POST /api/booking → Create booking (async via Kafka)
GET /api/trips → List available trips
GET /api/tickets/my-tickets → User's tickets
PUT /api/tickets/{id}/cancel → Cancel ticket# 📊 ENTITY DOCUMENTATION - Ticket System

## 📋 Tổng Quan

Hệ thống Ticket System có **10 Entities chính** với các mối quan hệ phức tạp:

| Entity | Table | Description | Relations |
|--------|-------|-------------|-----------|
| User | users | Người dùng hệ thống | 1-N với Ticket, Trip, Salary, LoyaltyPoint, Profile |
| Profile | profiles | Thông tin cá nhân | 1-1 với User |
| Route | routes | Tuyến đường | 1-N với Trip |
| Buses | buses | Xe bus | 1-N với Trip |
| Trip | trips | Chuyến xe | N-1 với Route, Bus, Driver; 1-N với Ticket, Revenue |
| Ticket | tickets | Vé xe | N-1 với Trip, Customer, Seller |
| Revenue | revenues | Doanh thu | N-1 với Trip |
| LoyaltyPoint | loyalty_points | Điểm thưởng | N-1 với Customer |
| LoyaltyReward | loyalty_rewards | Phần thưởng | Standalone |
| BaseLoyaltyPoints | base_loyalty_points | Quy tắc tích điểm | Standalone |
| BaseSalary | base_salaries | Mức lương cơ bản | N-1 với User |
| Salary | salaries | Lương thực tế | N-1 với User |
| AuditLog | audit_logs | Log hệ thống | N-1 với User |

---

## 🗂️ ERD - Entity Relationship Diagram

```
┌──────────┐
│   User   │──────┐
└──────────┘      │
     │ 1          │ 1
     │            │
     │ N          │ 1
┌──────────┐  ┌─────────┐
│  Ticket  │  │ Profile │
└──────────┘  └─────────┘
     │ N
     │
     │ 1
┌──────────┐      ┌──────────┐
│   Trip   │──────│  Route   │
└──────────┘  N 1 └──────────┘
     │ N
     │ 1
┌──────────┐
│  Buses   │
└──────────┘

Trip (1) ────< (N) Revenue
User (1) ────< (N) LoyaltyPoint
User (1) ────< (N) Salary
```

---

## 1️⃣ USER ENTITY

### 📝 Mô Tả
Entity quản lý tất cả người dùng trong hệ thống bao gồm: Khách hàng (CUSTOMER), Tài xế (DRIVER), Nhân viên bán vé (COLLECTOR), và Quản trị viên (ADMIN).

### 📊 Table: `users`

### 🔑 Fields

| Field | Type | Constraint | Default | Description |
|-------|------|------------|---------|-------------|
| **id** | Long | PK, Auto Increment, NOT NULL | - | ID duy nhất |
| **email** | String(100) | NOT NULL, UNIQUE | - | Email đăng nhập |
| **password** | String(255) | NOT NULL | - | Mật khẩu đã mã hóa (BCrypt) |
| **role** | Enum(UserRole) | NOT NULL | CUSTOMER | Vai trò: ADMIN, DRIVER, COLLECTOR, CUSTOMER |
| **isActive** | Boolean | NOT NULL | true | Trạng thái hoạt động |
| **createdAt** | LocalDateTime | NOT NULL, Auto | CURRENT_TIMESTAMP | Ngày tạo |
| **updatedAt** | LocalDateTime | NOT NULL, Auto | CURRENT_TIMESTAMP | Ngày cập nhật |
| **driverStatus** | Enum(DriverStatus) | NULL | - | AVAILABLE, ACTIVE, INACTIVE (chỉ cho DRIVER) |
| **userStatus** | Enum(CustomerStatus) | NULL | - | ACTIVE, INACTIVE, BLOCKED, NOT_BOOKED, BOOKED |

### 🔗 Relationships

| Relation | Entity | Type | Description |
|----------|--------|------|-------------|
| tickets (customer) | Ticket | One-to-Many | Vé mà user đã mua |
| tickets (seller) | Ticket | One-to-Many | Vé mà user đã bán |
| trips (driver) | Trip | One-to-Many | Chuyến xe mà driver lái |
| profile | Profile | One-to-One | Thông tin cá nhân |
| loyaltyPoints | LoyaltyPoint | One-to-Many | Điểm thưởng |
| salaries | Salary | One-to-Many | Lương |
| baseSalaries | BaseSalary | One-to-Many | Mức lương cơ bản |
| auditLogs | AuditLog | One-to-Many | Log hành động |

### 🎯 Business Rules

- Email phải unique trong hệ thống
- Password được mã hóa bằng BCrypt (strength 12)
- Role mặc định là CUSTOMER khi đăng ký
- driverStatus chỉ áp dụng cho user có role = DRIVER
- userStatus áp dụng cho CUSTOMER
- Khi tạo user mới, createdAt và updatedAt tự động set
- Khi update user, updatedAt tự động cập nhật

### 📡 Related APIs

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/auth/register` | Đăng ký user mới |
| POST | `/api/auth/login` | Đăng nhập |
| PUT | `/api/auth/update-password` | Đổi mật khẩu |
| GET | `/api/users` | Lấy danh sách users |
| GET | `/api/users/{id}` | Lấy user theo ID |
| POST | `/api/users` | Tạo user mới |
| PUT | `/api/users/{id}` | Cập nhật user |
| DELETE | `/api/users/{id}` | Xóa user |

### 📌 Enums

#### UserRole
```java
ADMIN       // Quản trị viên
DRIVER      // Tài xế
COLLECTOR   // Nhân viên bán vé
CUSTOMER    // Khách hàng
```

#### DriverStatus
```java
AVAILABLE   // Sẵn sàng
ACTIVE      // Đang chạy chuyến
INACTIVE    // Không hoạt động
```

#### CustomerStatus
```java
ACTIVE      // Hoạt động
INACTIVE    // Không hoạt động
BLOCKED     // Bị khóa
NOT_BOOKED  // Chưa đặt vé
BOOKED      // Đã đặt vé
```

### 💡 Example JSON

```json
{
  "id": 1,
  "email": "driver@example.com",
  "role": "DRIVER",
  "isActive": true,
  "createdAt": "2026-04-01T10:00:00",
  "updatedAt": "2026-04-09T14:30:00",
  "driverStatus": "AVAILABLE",
  "userStatus": null
}
```

---

## 2️⃣ PROFILE ENTITY

### 📝 Mô Tả
Lưu trữ thông tin chi tiết của người dùng (họ tên, số điện thoại, địa chỉ, ngày sinh).

### 📊 Table: `profiles`

### 🔑 Fields

| Field | Type | Constraint | Default | Description |
|-------|------|------------|---------|-------------|
| **id** | Long | PK, Auto Increment | - | ID duy nhất |
| **userId** | Long | FK → users.id, NOT NULL, UNIQUE | - | User liên kết |
| **fullName** | String(150) | NOT NULL | - | Họ và tên |
| **phone** | String(20) | NULL | - | Số điện thoại |
| **email** | String(100) | NULL | - | Email liên hệ |
| **address** | String(255) | NULL | - | Địa chỉ |
| **dateOfBirth** | LocalDate | NULL | - | Ngày sinh |

### 🔗 Relationships

| Relation | Entity | Type | Description |
|----------|--------|------|-------------|
| user | User | One-to-One | User sở hữu profile |

### 📡 Related APIs

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/profiles/{userId}` | Lấy profile theo user ID |
| POST | `/api/profiles` | Tạo profile mới |
| PUT | `/api/profiles/{id}` | Cập nhật profile |

### 💡 Example JSON

```json
{
  "id": 1,
  "userId": 1,
  "fullName": "Nguyễn Văn A",
  "phone": "0123456789",
  "email": "contact@example.com",
  "address": "123 Đường ABC, Hà Nội",
  "dateOfBirth": "1990-01-15"
}
```

---

## 3️⃣ ROUTE ENTITY

### 📝 Mô Tả
Quản lý các tuyến đường xe bus (từ điểm A đến điểm B).

### 📊 Table: `routes`

### 🔑 Fields

| Field | Type | Constraint | Default | Description |
|-------|------|------------|---------|-------------|
| **id** | Long | PK, Auto Increment | - | ID duy nhất |
| **routeName** | String(100) | NOT NULL | - | Tên tuyến (VD: "Hà Nội - Hải Phòng") |
| **startPoint** | String(100) | NULL | - | Điểm đi |
| **endPoint** | String(100) | NULL | - | Điểm đến |
| **distanceKm** | BigDecimal(6,2) | NULL | - | Khoảng cách (km) |
| **status** | Enum(RouteStatus) | NOT NULL | ACTIVE | ACTIVE, INACTIVE |

### 🔗 Relationships

| Relation | Entity | Type | Description |
|----------|--------|------|-------------|
| trips | Trip | One-to-Many | Các chuyến xe trên tuyến này |

### 🎯 Business Rules

- routeName nên unique nhưng không enforce ở DB
- distanceKm dùng để tính giá vé
- Chỉ route có status = ACTIVE mới được tạo trip mới

### 📡 Related APIs

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/routes` | Lấy danh sách routes |
| GET | `/api/routes/{id}` | Lấy route theo ID |
| POST | `/api/routes` | Tạo route mới |
| PUT | `/api/routes/{id}` | Cập nhật route |
| DELETE | `/api/routes/{id}` | Xóa route |

### 📌 Enums

#### RouteStatus
```java
ACTIVE      // Đang hoạt động
INACTIVE    // Ngừng hoạt động
```

### 💡 Example JSON

```json
{
  "id": 1,
  "routeName": "Hà Nội - Hải Phòng",
  "startPoint": "Hà Nội",
  "endPoint": "Hải Phòng",
  "distanceKm": 120.5,
  "status": "ACTIVE"
}
```

---

## 4️⃣ BUSES ENTITY

### 📝 Mô Tả
Quản lý thông tin xe bus (biển số, sức chứa, trạng thái).

### 📊 Table: `buses`

### 🔑 Fields

| Field | Type | Constraint | Default | Description |
|-------|------|------------|---------|-------------|
| **id** | Long | PK, Auto Increment | - | ID duy nhất |
| **plateNumber** | String(20) | NOT NULL, UNIQUE | - | Biển số xe |
| **capacity** | Integer | NOT NULL | - | Số chỗ ngồi |
| **status** | Enum(BusStatus) | NOT NULL | PENDING | ACTIVE, INACTIVE, PENDING |

### 🔗 Relationships

| Relation | Entity | Type | Description |
|----------|--------|------|-------------|
| trips | Trip | One-to-Many | Các chuyến xe của bus này |

### 🎯 Business Rules

- plateNumber phải unique
- capacity phải > 0
- Status ACTIVE khi xe đang chạy chuyến
- Status INACTIVE khi xe bảo trì
- Status PENDING khi xe sẵn sàng

### 📡 Related APIs

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/buses` | Lấy danh sách buses |
| GET | `/api/buses/{id}` | Lấy bus theo ID |
| POST | `/api/buses` | Tạo bus mới |
| PUT | `/api/buses/{id}` | Cập nhật bus |
| DELETE | `/api/buses/{id}` | Xóa bus |

### 📌 Enums

#### BusStatus
```java
ACTIVE      // Đang chạy chuyến
INACTIVE    // Bảo trì/không hoạt động
PENDING     // Sẵn sàng
```

### 💡 Example JSON

```json
{
  "id": 1,
  "plateNumber": "29A-12345",
  "capacity": 40,
  "status": "PENDING"
}
```

---

## 5️⃣ TRIP ENTITY

### 📝 Mô Tả
Quản lý thông tin chuyến xe (kết hợp Route, Bus, Driver).

### 📊 Table: `trips`

### 🔑 Fields

| Field | Type | Constraint | Default | Description |
|-------|------|------------|---------|-------------|
| **id** | Long | PK, Auto Increment | - | ID duy nhất |
| **routeId** | Long | FK → routes.id, NOT NULL | - | Tuyến đường |
| **busId** | Long | FK → buses.id, NOT NULL | - | Xe bus |
| **driverId** | Long | FK → users.id, NOT NULL | - | Tài xế |
| **departureTime** | LocalDateTime | NOT NULL | - | Thời gian khởi hành |
| **arrivalTime** | LocalDateTime | NULL | - | Thời gian đến (dự kiến) |
| **status** | Enum(TripStatus) | NOT NULL | SCHEDULED | Trạng thái chuyến |
| **revenue** | BigDecimal(15,2) | NOT NULL | 0.00 | Doanh thu chuyến xe |

### 🔗 Relationships

| Relation | Entity | Type | Description |
|----------|--------|------|-------------|
| route | Route | Many-to-One | Tuyến đường |
| bus | Buses | Many-to-One | Xe bus |
| driver | User | Many-to-One | Tài xế |
| tickets | Ticket | One-to-Many | Các vé của chuyến |
| revenues | Revenue | One-to-Many | Bản ghi doanh thu |

### 🎯 Business Rules

- driver phải có role = DRIVER
- driver.driverStatus = AVAILABLE khi tạo trip
- bus.status = PENDING khi tạo trip
- departureTime phải < arrivalTime
- revenue tự động tính từ tổng giá vé đã bán
- Không thể tạo vé mới khi status = ONGOING
- Khi status = COMPLETED, tính tổng revenue

### 📡 Related APIs

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/trips` | Lấy danh sách trips |
| GET | `/api/trips/{id}` | Lấy trip theo ID |
| POST | `/api/trips` | Tạo trip mới |
| PUT | `/api/trips/{id}` | Cập nhật trip |
| DELETE | `/api/trips/{id}` | Xóa trip |
| PUT | `/api/trips/{id}/complete` | Hoàn thành chuyến |

### 📌 Enums

#### TripStatus
```java
SCHEDULED   // Đã lên lịch
ONGOING     // Đang chạy
COMPLETED   // Hoàn thành
CANCELLED   // Đã hủy
```

### 💡 Example JSON

```json
{
  "id": 1,
  "route": {
    "id": 1,
    "routeName": "Hà Nội - Hải Phòng"
  },
  "bus": {
    "id": 1,
    "plateNumber": "29A-12345"
  },
  "driver": {
    "id": 2,
    "email": "driver@example.com"
  },
  "departureTime": "2026-04-10T08:00:00",
  "arrivalTime": "2026-04-10T10:30:00",
  "status": "SCHEDULED",
  "revenue": 0.00
}
```

---

## 6️⃣ TICKET ENTITY

### 📝 Mô Tả
Quản lý vé xe bus (liên kết Trip, Customer, Seller).

### 📊 Table: `tickets`

### 🔑 Fields

| Field | Type | Constraint | Default | Description |
|-------|------|------------|---------|-------------|
| **id** | Long | PK, Auto Increment | - | ID duy nhất |
| **tripId** | Long | FK → trips.id, NOT NULL | - | Chuyến xe |
| **customerId** | Long | FK → users.id | - | Khách hàng mua vé |
| **sellerId** | Long | FK → users.id | - | Nhân viên bán vé |
| **seatNumber** | Integer | NULL | - | Số ghế |
| **price** | BigDecimal(10,2) | NOT NULL | - | Giá vé |
| **statusTicket** | Enum(TicketStatus) | NOT NULL | NOT_BOOKED | Trạng thái vé |
| **userStatus** | Enum(CustomerStatus) | NULL | - | Trạng thái khách hàng |
| **issuedAt** | LocalDateTime | NULL, Auto | CURRENT_TIMESTAMP | Thời gian phát hành |

### 🔗 Relationships

| Relation | Entity | Type | Description |
|----------|--------|------|-------------|
| trip | Trip | Many-to-One | Chuyến xe |
| customer | User | Many-to-One | Khách hàng |
| seller | User | Many-to-One | Nhân viên bán |

### 🎯 Business Rules

- Mỗi vé thuộc về 1 chuyến xe duy nhất
- seatNumber phải <= bus.capacity
- Không được trùng seatNumber trong cùng 1 trip
- price > 0
- customer có thể NULL (vé chưa bán)
- seller có role = COLLECTOR
- Khi customer đặt vé, customer.userStatus = BOOKED
- Khi hủy vé, statusTicket = CANCELLED và customer.userStatus = NOT_BOOKED

### 📡 Related APIs

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/tickets` | Lấy danh sách tickets |
| GET | `/api/tickets/{id}` | Lấy ticket theo ID |
| GET | `/api/tickets/my-tickets` | Lấy vé của user hiện tại |
| POST | `/api/tickets` | Tạo ticket mới |
| POST | `/api/booking` | Đặt vé (via Kafka) |
| PUT | `/api/tickets/{id}` | Cập nhật ticket |
| PUT | `/api/tickets/{id}/cancel` | Hủy vé |
| DELETE | `/api/tickets/{id}` | Xóa ticket |

### 📌 Enums

#### TicketStatus
```java
NOT_BOOKED  // Chưa đặt
BOOKED      // Đã đặt
PENDING     // Đang xử lý
PAID        // Đã thanh toán
CANCELLED   // Đã hủy
```

### 💡 Example JSON

```json
{
  "id": 1,
  "trip": {
    "id": 1,
    "departureTime": "2026-04-10T08:00:00"
  },
  "customer": {
    "id": 3,
    "email": "customer@example.com"
  },
  "seller": {
    "id": 4,
    "email": "seller@example.com"
  },
  "seatNumber": 15,
  "price": 150000.00,
  "statusTicket": "BOOKED",
  "issuedAt": "2026-04-09T14:30:00"
}
```

---

## 7️⃣ REVENUE ENTITY

### 📝 Mô Tả
Lưu trữ bản ghi doanh thu theo từng chuyến xe.

### 📊 Table: `revenues`

### 🔑 Fields

| Field | Type | Constraint | Default | Description |
|-------|------|------------|---------|-------------|
| **id** | Long | PK, Auto Increment | - | ID duy nhất |
| **tripId** | Long | FK → trips.id, NOT NULL | - | Chuyến xe |
| **totalAmount** | BigDecimal(12,2) | NOT NULL | - | Tổng doanh thu |
| **reportDate** | LocalDate | NOT NULL | - | Ngày báo cáo |

### 🔗 Relationships

| Relation | Entity | Type | Description |
|----------|--------|------|-------------|
| trip | Trip | Many-to-One | Chuyến xe |

### 🎯 Business Rules

- totalAmount = SUM(tickets.price) WHERE trip_id = ?
- reportDate thường là ngày chuyến xe hoàn thành
- Một trip có thể có nhiều revenue records (theo ngày)

### 📡 Related APIs

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/revenue/report` | Báo cáo doanh thu |
| GET | `/api/revenue/by-route` | Doanh thu theo tuyến |
| GET | `/api/revenue/by-date` | Doanh thu theo ngày |
| GET | `/api/revenue/top-customers` | Top khách hàng |

### 💡 Example JSON

```json
{
  "id": 1,
  "trip": {
    "id": 1,
    "route": "Hà Nội - Hải Phòng"
  },
  "totalAmount": 4500000.00,
  "reportDate": "2026-04-09"
}
```

---

## 8️⃣ LOYALTY POINT ENTITY

### 📝 Mô Tả
Quản lý điểm thưởng của khách hàng.

### 📊 Table: `loyalty_points`

### 🔑 Fields

| Field | Type | Constraint | Default | Description |
|-------|------|------------|---------|-------------|
| **id** | Long | PK, Auto Increment | - | ID duy nhất |
| **customerId** | Long | FK → users.id, NOT NULL | - | Khách hàng |
| **points** | Integer | NOT NULL | - | Số điểm |
| **status** | Enum | NULL | - | UNUSED, REDEEMED |
| **allocate** | Integer | NULL | - | Điểm được phân bổ |
| **requiredPoint** | Integer | NULL | - | Điểm yêu cầu |
| **transactionType** | Enum(TransactionType) | NOT NULL | - | EARN, REDEEM, EXPIRE |
| **description** | String(255) | NULL | - | Mô tả giao dịch |

### 🔗 Relationships

| Relation | Entity | Type | Description |
|----------|--------|------|-------------|
| customer | User | Many-to-One | Khách hàng sở hữu |

### 🎯 Business Rules

- points có thể âm (khi redeem)
- transactionType = EARN khi đặt vé, tích điểm
- transactionType = REDEEM khi đổi quà
- transactionType = EXPIRE khi hết hạn
- Tổng points hiện tại = SUM(points) WHERE customer_id = ?

### 📡 Related APIs

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/loyalty-points` | Điểm của user hiện tại |
| POST | `/api/loyalty-points/earn` | Tích điểm |
| POST | `/api/loyalty-points/redeem` | Đổi điểm |
| GET | `/api/loyalty-points/history` | Lịch sử giao dịch |

### 📌 Enums

#### TransactionType
```java
EARN        // Tích điểm
REDEEM      // Đổi điểm
EXPIRE      // Hết hạn
```

#### Status
```java
UNUSED      // Chưa dùng
REDEEMED    // Đã đổi
```

### 💡 Example JSON

```json
{
  "id": 1,
  "customer": {
    "id": 3,
    "email": "customer@example.com"
  },
  "points": 50,
  "status": "UNUSED",
  "transactionType": "EARN",
  "description": "Tích điểm khi đặt vé trip #1"
}
```

---

## 9️⃣ LOYALTY REWARD ENTITY

### 📝 Mô Tả
Định nghĩa các phần thưởng có thể đổi bằng điểm.

### 📊 Table: `loyalty_rewards`

### 🔑 Fields

| Field | Type | Constraint | Default | Description |
|-------|------|------------|---------|-------------|
| **id** | Long | PK, Auto Increment | - | ID duy nhất |
| **rewardName** | String | NULL | - | Tên phần thưởng |
| **rewardType** | Enum(RewardType) | NULL | - | Loại thưởng |
| **description** | String | NULL | - | Mô tả |
| **active** | Boolean | NULL | - | Còn hiệu lực không |
| **pointsRequired** | Integer | NULL | - | Số điểm cần đổi |
| **status** | Enum(Status) | NULL | - | ACTIVE, INACTIVE |

### 🎯 Business Rules

- pointsRequired > 0
- Chỉ reward có status = ACTIVE mới được đổi
- rewardType quyết định loại ưu đãi

### 📡 Related APIs

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/loyalty-rewards` | Danh sách phần thưởng |
| GET | `/api/loyalty-rewards/{id}` | Chi tiết phần thưởng |
| POST | `/api/loyalty-rewards` | Tạo phần thưởng mới |

### 📌 Enums

#### RewardType
```java
TICKET_DISCOUNT     // Giảm giá vé (50%)
VOUCHER            // Voucher tiền
FREE_TICKET        // Vé miễn phí
```

#### Status
```java
ACTIVE      // Đang hoạt động
INACTIVE    // Không hoạt động
```

### 💡 Example JSON

```json
{
  "id": 1,
  "rewardName": "Giảm 50% giá vé",
  "rewardType": "TICKET_DISCOUNT",
  "description": "Áp dụng cho vé tiếp theo",
  "active": true,
  "pointsRequired": 100,
  "status": "ACTIVE"
}
```

---

## 🔟 BASE LOYALTY POINTS ENTITY

### 📝 Mô Tả
Quy tắc tích điểm theo role (VD: 1 điểm cho 1 vé).

### 📊 Table: `base_loyalty_points`

### 🔑 Fields

| Field | Type | Constraint | Default | Description |
|-------|------|------------|---------|-------------|
| **id** | Long | PK, Auto Increment | - | ID duy nhất |
| **roleName** | String | NULL | - | Role áp dụng |
| **ticketsPerPoint** | Integer | NOT NULL | - | Số vé = 1 điểm |
| **pointValue** | BigDecimal | NULL | - | Giá trị 1 điểm |
| **maxPointsPerMonth** | Integer | NULL | - | Giới hạn điểm/tháng |
| **startDate** | LocalDate | NULL | - | Ngày bắt đầu |
| **endDate** | LocalDate | NULL | - | Ngày kết thúc |
| **status** | Enum | NULL | - | ACTIVE, INACTIVE |

### 📡 Related APIs

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/base-loyalty-points` | Quy tắc tích điểm |
| POST | `/api/base-loyalty-points` | Tạo quy tắc mới |
| PUT | `/api/base-loyalty-points/{id}` | Cập nhật quy tắc |

### 📌 Enums

#### BaseLoyaltyPointStatus
```java
ACTIVE      // Đang áp dụng
INACTIVE    // Không áp dụng
```

### 💡 Example JSON

```json
{
  "id": 1,
  "roleName": "CUSTOMER",
  "ticketsPerPoint": 1,
  "pointValue": 1000.00,
  "maxPointsPerMonth": 500,
  "startDate": "2026-01-01",
  "endDate": null,
  "status": "ACTIVE"
}
```

---

## 1️⃣1️⃣ BASE SALARY ENTITY

### 📝 Mô Tả
Mức lương cơ bản theo role hoặc theo user cụ thể.

### 📊 Table: `base_salaries`

### 🔑 Fields

| Field | Type | Constraint | Default | Description |
|-------|------|------------|---------|-------------|
| **id** | Long | PK, Auto Increment | - | ID duy nhất |
| **userId** | Long | FK → users.id, NULL | - | User cụ thể (NULL = áp dụng cho role) |
| **role** | Enum(UserRole) | NULL | - | Role áp dụng |
| **baseSalary** | BigDecimal(12,2) | NOT NULL | - | Lương cơ bản |
| **allowance** | BigDecimal(12,2) | NOT NULL | 0.00 | Trợ cấp |
| **commission** | BigDecimal(12,2) | NOT NULL | 0.00 | Hoa hồng/chuyến |
| **bonus** | BigDecimal(12,2) | NOT NULL | 0.00 | Tiền thưởng |
| **effectiveFrom** | LocalDate | NOT NULL | - | Ngày bắt đầu hiệu lực |
| **effectiveTo** | LocalDate | NULL | - | Ngày kết thúc |

### 🔗 Relationships

| Relation | Entity | Type | Description |
|----------|--------|------|-------------|
| user | User | Many-to-One | User cụ thể (optional) |

### 🎯 Business Rules

- Nếu userId = NULL → áp dụng cho tất cả users có role này
- Nếu userId != NULL → áp dụng riêng cho user đó
- effectiveFrom <= CURRENT_DATE <= effectiveTo (or null)
- baseSalary > 0

### 📡 Related APIs

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/base-salary` | Mức lương cơ bản |
| POST | `/api/base-salary` | Tạo mức lương mới |
| PUT | `/api/base-salary/{id}` | Cập nhật |

### 💡 Example JSON

```json
{
  "id": 1,
  "user": null,
  "role": "DRIVER",
  "baseSalary": 8000000.00,
  "allowance": 500000.00,
  "commission": 50000.00,
  "bonus": 0.00,
  "effectiveFrom": "2026-01-01",
  "effectiveTo": null
}
```

---

## 1️⃣2️⃣ SALARY ENTITY

### 📝 Mô Tả
Lương thực tế của nhân viên theo tháng.

### 📊 Table: `salaries`

### 🔑 Fields

| Field | Type | Constraint | Default | Description |
|-------|------|------------|---------|-------------|
| **id** | Long | PK, Auto Increment | - | ID duy nhất |
| **userId** | Long | FK → users.id, NOT NULL | - | Nhân viên |
| **salary** | BigDecimal(12,2) | NOT NULL | - | Tổng lương |
| **periodMonth** | Byte | NOT NULL | - | Tháng (1-12) |
| **periodYear** | Short | NOT NULL | - | Năm |

### 🔗 Relationships

| Relation | Entity | Type | Description |
|----------|--------|------|-------------|
| user | User | Many-to-One | Nhân viên |

### 🎯 Business Rules

- Tính lương = baseSalary + allowance + (commission × số chuyến) + bonus
- periodMonth: 1-12
- periodYear: năm hiện tại
- Mỗi user chỉ có 1 salary record cho 1 tháng

### 📡 Related APIs

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/salary` | Lương nhân viên |
| GET | `/api/salary/calculate` | Tính lương |
| POST | `/api/salary` | Tạo phiếu lương |

### 💡 Example JSON

```json
{
  "id": 1,
  "user": {
    "id": 2,
    "email": "driver@example.com",
    "role": "DRIVER"
  },
  "salary": 12500000.00,
  "periodMonth": 4,
  "periodYear": 2026
}
```

---

## 1️⃣3️⃣ AUDIT LOG ENTITY

### 📝 Mô Tả
Ghi lại các hành động quan trọng trong hệ thống.

### 📊 Table: `audit_logs`

### 🔑 Fields

| Field | Type | Constraint | Default | Description |
|-------|------|------------|---------|-------------|
| **id** | Long | PK, Auto Increment | - | ID duy nhất |
| **userId** | Long | FK → users.id | - | User thực hiện |
| **action** | String(255) | NULL | - | Mô tả hành động |

### 🔗 Relationships

| Relation | Entity | Type | Description |
|----------|--------|------|-------------|
| user | User | Many-to-One | User thực hiện |

### 🎯 Business Rules

- Ghi log cho các hành động: CREATE, UPDATE, DELETE
- Ghi log cho login/logout
- Ghi log cho các thay đổi quan trọng

### 💡 Example JSON

```json
{
  "id": 1,
  "user": {
    "id": 1,
    "email": "admin@example.com"
  },
  "action": "Created new route: Hà Nội - Đà Nẵng",
  "createdAt": "2026-04-09T14:30:00"
}
```

---

## 📐 ENTITY RELATIONSHIPS MATRIX

| Entity | User | Profile | Route | Bus | Trip | Ticket | Revenue | LoyaltyPoint | Salary |
|--------|------|---------|-------|-----|------|--------|---------|--------------|--------|
| **User** | - | 1:1 | - | - | 1:N (driver) | 1:N (customer/seller) | - | 1:N | 1:N |
| **Profile** | N:1 | - | - | - | - | - | - | - | - |
| **Route** | - | - | - | - | 1:N | - | - | - | - |
| **Bus** | - | - | - | - | 1:N | - | - | - | - |
| **Trip** | N:1 (driver) | - | N:1 | N:1 | - | 1:N | 1:N | - | - |
| **Ticket** | N:1 (customer/seller) | - | - | - | N:1 | - | - | - | - |
| **Revenue** | - | - | - | - | N:1 | - | - | - | - |
| **LoyaltyPoint** | N:1 | - | - | - | - | - | - | - | - |
| **Salary** | N:1 | - | - | - | - | - | - | - | - |

---

## 📊 DATABASE DIAGRAM

### Core Flow
```
                    ┌─────────┐
                    │  User   │
                    └────┬────┘
                         │
         ┌───────────────┼───────────────┐
         │               │               │
    ┌────▼───┐      ┌───▼────┐    ┌────▼────┐
    │Profile │      │ Ticket │    │ Salary  │
    └────────┘      └───┬────┘    └─────────┘
                        │
                        │ N
                        │
                        │ 1
                    ┌───▼────┐
                    │  Trip  │
                    └───┬────┘
                        │
         ┌──────────────┼──────────────┐
         │              │              │
    ┌────▼────┐    ┌───▼────┐    ┌───▼────┐
    │  Route  │    │  Bus   │    │Revenue │
    └─────────┘    └────────┘    └────────┘
```

---

## 📝 SQL SCHEMA QUICK REFERENCE

### Users Table
```sql
CREATE TABLE users (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    email VARCHAR(100) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    role VARCHAR(20) NOT NULL DEFAULT 'customer',
    is_active TINYINT(1) NOT NULL DEFAULT 1,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    driver_status ENUM('AVAILABLE', 'ACTIVE', 'INACTIVE'),
    user_status ENUM('ACTIVE', 'INACTIVE', 'BLOCKED', 'NOT_BOOKED', 'BOOKED'),
    INDEX idx_users_email (email),
    INDEX idx_users_role (role)
);
```

### Trips Table
```sql
CREATE TABLE trips (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    route_id BIGINT NOT NULL,
    bus_id BIGINT NOT NULL,
    driver_id BIGINT NOT NULL,
    departure_time DATETIME NOT NULL,
    arrival_time DATETIME,
    status ENUM('SCHEDULED','ONGOING','COMPLETED','CANCELLED') DEFAULT 'SCHEDULED',
    revenue DECIMAL(15,2) DEFAULT 0.00,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (route_id) REFERENCES routes(id),
    FOREIGN KEY (bus_id) REFERENCES buses(id),
    FOREIGN KEY (driver_id) REFERENCES users(id),
    INDEX idx_trips_route (route_id),
    INDEX idx_trips_bus (bus_id),
    INDEX idx_trips_driver (driver_id),
    INDEX idx_trips_departure (departure_time),
    INDEX idx_trips_status (status)
);
```

### Tickets Table
```sql
CREATE TABLE tickets (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    trip_id BIGINT NOT NULL,
    customer_id BIGINT,
    seller_id BIGINT,
    seat_number INT,
    price DECIMAL(10,2) NOT NULL,
    status_ticket ENUM('NOT_BOOKED','BOOKED','PENDING','PAID','CANCELLED') DEFAULT 'NOT_BOOKED',
    user_status ENUM('ACTIVE', 'INACTIVE', 'BLOCKED', 'NOT_BOOKED', 'BOOKED'),
    issued_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (trip_id) REFERENCES trips(id),
    FOREIGN KEY (customer_id) REFERENCES users(id),
    FOREIGN KEY (seller_id) REFERENCES users(id),
    INDEX idx_tickets_trip (trip_id),
    INDEX idx_tickets_customer (customer_id),
    INDEX idx_tickets_seller (seller_id),
    INDEX idx_tickets_issued (issued_at),
    UNIQUE KEY unique_seat_per_trip (trip_id, seat_number)
);
```

---

## 🔍 COMMON QUERIES

### 1. Lấy tất cả vé của customer
```sql
SELECT t.*, tr.departure_time, r.route_name
FROM tickets t
JOIN trips tr ON t.trip_id = tr.id
JOIN routes r ON tr.route_id = r.id
WHERE t.customer_id = ?
ORDER BY t.issued_at DESC;
```

### 2. Doanh thu theo chuyến
```sql
SELECT tr.id, r.route_name, SUM(t.price) as total_revenue
FROM trips tr
JOIN routes r ON tr.route_id = r.id
LEFT JOIN tickets t ON t.trip_id = tr.id
WHERE tr.status = 'COMPLETED'
GROUP BY tr.id, r.route_name;
```

### 3. Số vé đã bán của một trip
```sql
SELECT COUNT(*) 
FROM tickets 
WHERE trip_id = ? AND status_ticket IN ('BOOKED', 'PAID');
```

### 4. Top khách hàng theo doanh thu
```sql
SELECT u.id, u.email, SUM(t.price) as total_spent, COUNT(t.id) as total_tickets
FROM users u
JOIN tickets t ON u.id = t.customer_id
WHERE t.status_ticket IN ('BOOKED', 'PAID')
GROUP BY u.id, u.email
ORDER BY total_spent DESC
LIMIT 10;
```

### 5. Lương tài xế theo tháng
```sql
SELECT u.id, u.email, 
       bs.base_salary + bs.allowance + (COUNT(tr.id) * bs.commission) as total_salary
FROM users u
JOIN base_salaries bs ON u.role = bs.role
LEFT JOIN trips tr ON u.id = tr.driver_id 
    AND MONTH(tr.departure_time) = ?
    AND YEAR(tr.departure_time) = ?
WHERE u.role = 'DRIVER'
GROUP BY u.id, u.email, bs.base_salary, bs.allowance, bs.commission;
```

---

## 📋 FIELD CONVENTIONS

### Naming Convention
```
✅ camelCase in Java:    userId, fullName, startPoint
✅ snake_case in DB:     user_id, full_name, start_point
✅ Consistent:           Always use same pattern
```

### Data Types
```
ID fields:              Long
String fields:          String (with max length)
Numbers:                Integer, BigDecimal
Dates:                  LocalDate, LocalDateTime
Enums:                  EnumType.STRING
Boolean:                Boolean
```

### Constraints
```
Primary Keys:           @Id, Auto Increment
Foreign Keys:           @ManyToOne, @JoinColumn
Unique:                 @UniqueConstraint
Not Null:               nullable = false
Default Values:         columnDefinition = "DEFAULT ..."
```

---

## 🎯 VALIDATION RULES

### User
```java
@Email(message = "Invalid email format")
private String email;

@Size(min = 6, max = 100, message = "Password must be 6-100 characters")
private String password;

@NotNull(message = "Role is required")
private UserRole role;
```

### Ticket
```java
@NotNull(message = "Trip ID is required")
private Long tripId;

@NotNull(message = "Price is required")
@DecimalMin(value = "0.0", inclusive = false)
private BigDecimal price;

@Min(value = 1, message = "Seat number must be positive")
@Max(value = 100, message = "Seat number too large")
private Integer seatNumber;
```

### Trip
```java
@NotNull(message = "Departure time is required")
@Future(message = "Departure time must be in future")
private LocalDateTime departureTime;

@NotNull(message = "Route is required")
private Long routeId;

@NotNull(message = "Bus is required")
private Long busId;
```

---

## 🔐 SECURITY & ACCESS CONTROL

### Field-Level Security

```java
// Password field - never serialize
@Column(name = "password")
@JsonIgnore
private String password;

// Lazy-loaded relationships - prevent N+1
@ManyToOne(fetch = FetchType.LAZY)
@JsonIgnore
private Trip trip;
```

### Role-Based Access

| Entity | CREATE | READ | UPDATE | DELETE |
|--------|--------|------|--------|--------|
| User | ADMIN | ALL | ADMIN, SELF | ADMIN |
| Route | ADMIN | ALL | ADMIN | ADMIN |
| Bus | ADMIN | ALL | ADMIN | ADMIN |
| Trip | ADMIN | ALL | ADMIN | ADMIN |
| Ticket | COLLECTOR, CUSTOMER | ALL | COLLECTOR, CUSTOMER (own) | ADMIN |
| Revenue | ADMIN | ADMIN | ADMIN | ADMIN |
| Salary | ADMIN | ADMIN, SELF | ADMIN | ADMIN |

---

## 📈 INDEXING RECOMMENDATIONS

### Critical Indexes
```sql
-- Users
CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_users_role ON users(role);
CREATE INDEX idx_users_active ON users(is_active);

-- Trips
CREATE INDEX idx_trips_route_id ON trips(route_id);
CREATE INDEX idx_trips_bus_id ON trips(bus_id);
CREATE INDEX idx_trips_driver_id ON trips(driver_id);
CREATE INDEX idx_trips_departure ON trips(departure_time);
CREATE INDEX idx_trips_status ON trips(status);

-- Tickets
CREATE INDEX idx_tickets_trip_id ON tickets(trip_id);
CREATE INDEX idx_tickets_customer_id ON tickets(customer_id);
CREATE INDEX idx_tickets_seller_id ON tickets(seller_id);
CREATE INDEX idx_tickets_issued_at ON tickets(issued_at);
CREATE INDEX idx_tickets_status ON tickets(status_ticket);

-- Composite Indexes
CREATE INDEX idx_tickets_trip_status ON tickets(trip_id, status_ticket);
CREATE INDEX idx_trips_bus_status ON trips(bus_id, status);
CREATE INDEX idx_salary_user_period ON salaries(user_id, period_year, period_month);
```

---

## 🧪 ENTITY LIFECYCLE HOOKS

### @PrePersist - Before Insert
```java
@PrePersist
protected void onCreate() {
    this.createdAt = LocalDateTime.now();
    this.updatedAt = LocalDateTime.now();
    if (this.isActive == null) {
        this.isActive = true;
    }
}
```

### @PreUpdate - Before Update
```java
@PreUpdate
protected void onUpdate() {
    this.updatedAt = LocalDateTime.now();
}
```

### @PostLoad - After Load
```java
@PostLoad
protected void onLoad() {
    // Initialize transient fields
}
```

---

## 📚 DTO MAPPING

### Entity → DTO
```java
// UserResponseDto
public class UserResponseDto {
    private Long id;
    private String email;
    private String role;
    private Boolean isActive;
    // NO password field!
}

// Mapping
UserResponseDto dto = UserResponseDto.builder()
    .id(user.getId())
    .email(user.getEmail())
    .role(user.getRole().toString())
    .isActive(user.getIsActive())
    .build();
```

### DTO → Entity
```java
// UserRequestDto
public class UserRequestDto {
    @Email
    private String email;
    
    @Size(min = 6)
    private String password;
    
    @NotNull
    private UserRole role;
}

// Mapping
User user = User.builder()
    .email(dto.getEmail())
    .password(passwordEncoder.encode(dto.getPassword()))
    .role(dto.getRole())
    .build();
```

---

## 🎯 BEST PRACTICES

### 1. Always Use DTOs
```java
// ❌ DON'T: Return entity directly
@GetMapping("/{id}")
public User getUser(@PathVariable Long id) {
    return userRepository.findById(id).orElseThrow();
}

// ✅ DO: Return DTO
@GetMapping("/{id}")
public UserResponseDto getUser(@PathVariable Long id) {
    User user = userRepository.findById(id).orElseThrow();
    return mapToDto(user);
}
```

### 2. Use Fetch Joins for Relations
```java
// ❌ DON'T: Lazy loading triggers N+1
List<Ticket> tickets = ticketRepository.findAll();
tickets.forEach(t -> System.out.println(t.getTrip().getRoute()));

// ✅ DO: Fetch join
@Query("SELECT t FROM Ticket t " +
       "JOIN FETCH t.trip tr " +
       "JOIN FETCH tr.route")
List<Ticket> findAllWithTripAndRoute();
```

### 3. Validate Before Save
```java
// ✅ DO: Validate business rules
public Ticket createTicket(TicketRequestDto dto) {
    Trip trip = tripRepository.findById(dto.getTripId())
        .orElseThrow(() -> new TripNotFoundException(dto.getTripId()));
    
    // Validate trip status
    if (trip.getStatus() == TripStatus.ONGOING) {
        throw new TripOngoingException(trip.getId());
    }
    
    // Validate capacity
    int bookedSeats = ticketRepository.countByTripId(trip.getId());
    if (bookedSeats >= trip.getBus().getCapacity()) {
        throw new TripFullException(trip.getId());
    }
    
    // Create ticket
    return ticketRepository.save(ticket);
}
```

### 4. Use Projections for Reports
```java
// ✅ DO: Use projections to reduce data transfer
public interface TicketSummaryProjection {
    Long getId();
    Integer getSeatNumber();
    BigDecimal getPrice();
    String getCustomerEmail();
}

@Query("SELECT t.id as id, t.seatNumber as seatNumber, " +
       "t.price as price, c.email as customerEmail " +
       "FROM Ticket t JOIN t.customer c")
List<TicketSummaryProjection> findTicketSummaries();
```

---

## 🔗 CROSS-REFERENCE

### Entities by Module

#### common-library
- BaseEntity (abstract class - inherited by all)

#### manage-revenue-ticket
- User
- Profile
- Route
- Buses
- Trip
- Ticket
- Revenue
- LoyaltyPoint
- LoyaltyReward
- BaseLoyaltyPoints
- BaseSalary
- Salary
- AuditLog

#### booking_ticket
- (Reuses entities from manage-revenue-ticket)
- Trip (reference)
- Ticket (reference)
- User (reference)

---

## 📞 API ENDPOINT SUMMARY BY ENTITY

### User APIs (Port 8082)
```
GET    /api/users              → Page<User>
GET    /api/users/{id}         → User
POST   /api/users              → User
PUT    /api/users/{id}         → User
DELETE /api/users/{id}         → void
POST   /api/auth/register      → TokenResponse
POST   /api/auth/login         → TokenResponse
```

### Route APIs (Port 8082)
```
GET    /api/routes             → Page<Route>
GET    /api/routes/{id}        → Route
POST   /api/routes             → Route
PUT    /api/routes/{id}        → Route
DELETE /api/routes/{id}        → void
```

### Bus APIs (Port 8082)
```
GET    /api/buses              → Page<Buses>
GET    /api/buses/{id}         → Buses
POST   /api/buses              → Buses
PUT    /api/buses/{id}         → Buses
DELETE /api/buses/{id}         → void
```

### Trip APIs (Port 8082)
```
GET    /api/trips              → Page<Trip>
GET    /api/trips/{id}         → Trip
POST   /api/trips              → Trip
PUT    /api/trips/{id}         → Trip
DELETE /api/trips/{id}         → void
```

### Ticket APIs (Port 8082 & 8081)
```
GET    /api/tickets            → Page<Ticket>
GET    /api/tickets/{id}       → Ticket
POST   /api/tickets            → Ticket
PUT    /api/tickets/{id}       → Ticket
DELETE /api/tickets/{id}       → void
POST   /api/booking            → void (Kafka) [Port 8081]
```

### Revenue APIs (Port 8082)
```
GET    /api/revenue/report     → RevenueReport
GET    /api/revenue/by-route   → List<RouteRevenue>
GET    /api/revenue/by-date    → List<DailyRevenue>
GET    /api/revenue/top-customers → List<CustomerRevenue>
```

---

## 📊 DATA MIGRATION CHECKLIST

### Initial Setup
```sql
-- 1. Create tables in order (dependencies)
CREATE TABLE users;
CREATE TABLE profiles;
CREATE TABLE routes;
CREATE TABLE buses;
CREATE TABLE trips;
CREATE TABLE tickets;
CREATE TABLE revenues;
CREATE TABLE loyalty_points;
CREATE TABLE loyalty_rewards;
CREATE TABLE base_loyalty_points;
CREATE TABLE base_salaries;
CREATE TABLE salaries;
CREATE TABLE audit_logs;

-- 2. Insert seed data
INSERT INTO users (email, password, role) 
VALUES ('admin@example.com', '$2a$12$...', 'ADMIN');

INSERT INTO routes (route_name, start_point, end_point, distance_km, status)
VALUES ('Hà Nội - Hải Phòng', 'Hà Nội', 'Hải Phòng', 120.5, 'ACTIVE');

-- 3. Create indexes
CREATE INDEX idx_users_email ON users(email);
-- ... (see above)
```

---

## 🎓 LEARNING RESOURCES

### JPA & Hibernate
- [Spring Data JPA Reference](https://docs.spring.io/spring-data/jpa/docs/current/reference/html/)
- [Hibernate User Guide](https://docs.jboss.org/hibernate/orm/current/userguide/html_single/Hibernate_User_Guide.html)

### Database Design
- [Database Normalization](https://en.wikipedia.org/wiki/Database_normalization)
- [SQL Performance](https://use-the-index-luke.com/)

### Best Practices
- [JPA Best Practices](https://thoughts-on-java.org/jpa-best-practices/)
- [Entity Design Patterns](https://martinfowler.com/eaaCatalog/)

---

## 📝 CHANGE LOG

| Version | Date | Changes |
|---------|------|---------|
| 1.0.0 | 2026-04-09 | Initial entity documentation |

---

**Last Updated**: April 9, 2026  
**Total Entities**: 13  
**Total Tables**: 13  
**Documentation Status**: ✅ Complete

