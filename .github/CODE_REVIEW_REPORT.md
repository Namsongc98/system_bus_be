# 🔍 CODE REVIEW REPORT - Ticket System
**Reviewer**: Senior Java Spring Boot Developer (10 Years Experience)  
**Review Date**: April 9, 2026  
**Project**: Ticket Management System - Microservices Architecture

---

## 📊 Executive Summary

| Metric | Rating | Comment |
|--------|--------|---------|
| **Code Quality** | 6/10 | Nhiều vấn đề cần cải thiện |
| **Security** | 5/10 | Có lỗ hổng bảo mật nghiêm trọng |
| **Performance** | 6/10 | Cần tối ưu nhiều điểm |
| **Best Practices** | 5/10 | Vi phạm nhiều best practices |
| **Maintainability** | 6/10 | Code chưa đủ clean |

**Overall**: ⚠️ **MAJOR ISSUES FOUND** - Cần refactor và cải thiện ngay

---

## 🚨 CRITICAL ISSUES (Must Fix Immediately)

### 1. **Security Vulnerabilities** 🔴

#### 1.1. Hardcoded Sensitive Information
**File**: `application.properties`

```properties
# ❌ CRITICAL: Hardcoded credentials
spring.datasource.password=123456789
spring.mail.password=lxfn ujqb yunq mruq
jwt.secret=Y2hhbmdlbWVzdXJlZ2VuZXJhdGVkMTIzNDU2Nzg5MDEyMzQ1Njc4OTAxMjM0NQ==
```

**Vấn đề**:
- Mật khẩu database, email, JWT secret được hardcode trong file
- Dễ bị lộ thông tin khi push lên Git
- Không thể thay đổi giữa các môi trường

**Solution**:
```properties
# ✅ CORRECT: Sử dụng environment variables
spring.datasource.password=${DB_PASSWORD}
spring.mail.password=${MAIL_PASSWORD}
jwt.secret=${JWT_SECRET}
```

**Best Practice**:
- Sử dụng Spring Cloud Config Server
- Sử dụng HashiCorp Vault
- Sử dụng AWS Secrets Manager / Azure Key Vault

---

#### 1.2. JWT Token Hardcoded Authority
**File**: `JwtAuthFilter.java` (Line 66)

```java
// ❌ CRITICAL: Hardcoded authority "ADMIN" cho mọi user
List<GrantedAuthority> authorities =
    Collections.singletonList(new SimpleGrantedAuthority("ADMIN"));
```

**Vấn đề**:
- Mọi user authenticated đều có role ADMIN
- Bypass hoàn toàn hệ thống phân quyền
- **LỖ HỔNG BẢO MẬT NGHIÊM TRỌNG**

**Solution**:
```java
// ✅ CORRECT: Extract role from JWT token
String role = jwtUtil.getRoleFromToken(jwtToken);
List<GrantedAuthority> authorities =
    Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + role));

UsernamePasswordAuthenticationToken authToken =
    new UsernamePasswordAuthenticationToken(userId, null, authorities);
```

---

#### 1.3. Password Encoder Created Multiple Times
**File**: `AuthService.java` (Line 21)

```java
// ❌ WRONG: Tạo BCryptPasswordEncoder mỗi khi service được khởi tạo
private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
```

**Vấn đề**:
- BCryptPasswordEncoder nên là singleton bean
- Tốn tài nguyên khi tạo nhiều instance
- Không consistent với Spring Security best practices

**Solution**:
```java
// ✅ CORRECT: Inject as Bean
@Configuration
public class SecurityBeanConfig {
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12); // strength 12
    }
}

// In AuthService
@Service
public class AuthService {
    private final PasswordEncoder passwordEncoder;
    
    @Autowired
    public AuthService(PasswordEncoder passwordEncoder) {
        this.passwordEncoder = passwordEncoder;
    }
}
```

---

#### 1.4. SQL Injection Risk
**File**: `TicketRepository.java`

```java
// ⚠️ Native queries không có validation
@Query(value = """
    SELECT COUNT(tk.id)
    FROM tickets tk
    WHERE tr.bus_id = :busId
""", nativeQuery = true)
```

**Recommendation**:
- Luôn dùng parameterized queries (đã dùng `:busId` - OK)
- Validate input parameters
- Cân nhắc dùng JPQL thay vì native SQL

---

### 2. **Exception Handling Issues** 🔴

#### 2.1. Generic RuntimeException
**File**: `AuthService.java` (Line 44, 51, 55, 69, 73)

```java
// ❌ BAD: Generic RuntimeException không rõ ràng
throw new RuntimeException(e);
throw new RuntimeException("User not found");
throw new RuntimeException("Mật khẩu cũ không đúng");
```

**Vấn đề**:
- Không thể distinguish giữa các loại lỗi
- Client không biết lỗi gì để xử lý
- Log không rõ ràng

**Solution**:
```java
// ✅ CORRECT: Custom exceptions
public class UserNotFoundException extends RuntimeException {
    public UserNotFoundException(String email) {
        super("User not found with email: " + email);
    }
}

public class InvalidPasswordException extends RuntimeException {
    public InvalidPasswordException() {
        super("Invalid password");
    }
}

public class EmailAlreadyExistsException extends RuntimeException {
    public EmailAlreadyExistsException(String email) {
        super("Email already exists: " + email);
    }
}

// Usage
if (userRepository.existsByEmail(email)) {
    throw new EmailAlreadyExistsException(email);
}
```

---

#### 2.2. Swallowing Stack Trace
**File**: `AuthService.java` (Line 43-45)

```java
// ❌ BAD: Mất thông tin gốc của exception
try {
    User newUser = User.builder()...
    return userRepository.save(newUser);
} catch (Exception e) {
    throw new RuntimeException(e); // Chỉ wrap, không log
}
```

**Solution**:
```java
// ✅ CORRECT: Log và preserve stack trace
@Slf4j
@Service
public class AuthService {
    try {
        User newUser = User.builder()...
        return userRepository.save(newUser);
    } catch (DataIntegrityViolationException e) {
        log.error("Database constraint violation during user registration", e);
        throw new UserRegistrationException("Failed to register user", e);
    } catch (Exception e) {
        log.error("Unexpected error during user registration", e);
        throw new UserRegistrationException("Failed to register user", e);
    }
}
```

---

### 3. **Dependency Injection Issues** 🟡

#### 3.1. Field Injection Instead of Constructor
**File**: `AuthService.java`, `TicketService.java`, `TripService.java`

```java
// ❌ BAD: Field injection
@Autowired
private TicketRepository ticketRepository;

@Autowired
private TripRepository tripRepository;
```

**Vấn đề**:
- Không thể inject trong unit tests
- Không immutable
- Không final
- Khó phát hiện circular dependencies

**Solution**:
```java
// ✅ CORRECT: Constructor injection với Lombok
@Service
@RequiredArgsConstructor
public class TicketService {
    private final TicketRepository ticketRepository;
    private final TripRepository tripRepository;
    private final UserRepository userRepository;
    private final BaseLoyaltyPointsService baseLoyaltyPointsService;
    // Spring sẽ tự động inject qua constructor
}
```

---

#### 3.2. Unused Dependency
**File**: `AuthService.java` (Line 16)

```java
// ❌ UNUSED: Khai báo nhưng không dùng
private UserService userService;
```

**Solution**: Xóa bỏ

---

### 4. **Code Duplication** 🟡

#### 4.1. Duplicate Repository Checks
**File**: `TicketService.java`

```java
// ❌ DUPLICATION: Logic kiểm tra trip lặp lại nhiều lần
// Line 59-63
Trip trip = tripRepository.findById(responseDto.getTripId())
    .orElseThrow(()->new ResourceNotFoundException("Không tìm thấy chuyến xe"));
if(trip.getStatus() == TripStatus.ONGOING){
    throw new IllegalArgumentException("Chuyến xe này đang trong chuyến!");
}

// Line 126-130 (Duplicate)
Trip trip = tripRepository.findById(responseDto.getTripId())
    .orElseThrow(()->new ResourceNotFoundException("Không tìm thấy chuyến xe"));
if(trip.getStatus() == TripStatus.ONGOING){
    throw new IllegalArgumentException("Chuyến xe này đang trong chuyến!");
}

// Line 150-154 (Duplicate again!)
```

**Solution**:
```java
// ✅ CORRECT: Extract to private method
private Trip validateAndGetTrip(Long tripId) {
    Trip trip = tripRepository.findById(tripId)
        .orElseThrow(() -> new TripNotFoundException(tripId));
    
    if (trip.getStatus() == TripStatus.ONGOING) {
        throw new TripOngoingException(tripId);
    }
    
    return trip;
}

// Usage
Trip trip = validateAndGetTrip(responseDto.getTripId());
```

---

#### 4.2. Duplicate User Validation
**File**: `TicketService.java` (Line 64-68, 131-135, 155-159)

```java
// ❌ DUPLICATION
User customer = userRepository.findById(responseDto.getCustomerId())
    .orElseThrow(()->new ResourceNotFoundException("Không thấy người dùng này"));

User seller = userRepository.findById(responseDto.getSellerId())
    .orElseThrow(()->new ResourceNotFoundException("Không thấy người bán này"));
```

**Solution**:
```java
// ✅ CORRECT: Extract validation methods
private User validateCustomer(Long customerId) {
    User customer = userRepository.findById(customerId)
        .orElseThrow(() -> new UserNotFoundException(customerId));
    
    if (customer.getUserStatus() == CustomerStatus.BOOKED) {
        throw new CustomerAlreadyBookedException(customerId);
    }
    
    return customer;
}

private User validateSeller(Long sellerId) {
    User seller = userRepository.findById(sellerId)
        .orElseThrow(() -> new UserNotFoundException(sellerId));
    
    if (seller.getRole() != UserRole.COLLECTOR) {
        throw new InvalidRoleException(sellerId, UserRole.COLLECTOR);
    }
    
    return seller;
}
```

---

### 5. **Performance Issues** 🟡

#### 5.1. N+1 Query Problem
**File**: `TicketService.java` (Line 73-77)

```java
// ❌ PERFORMANCE: Potential N+1 query
Buses bus = trip.getBus(); // Lazy loading - triggers separate query
int countTicket = ticketRepository.countTicketsByBusId(bus.getId());
```

**Solution**:
```java
// ✅ CORRECT: Fetch join in repository
@Query("SELECT t FROM Trip t " +
       "JOIN FETCH t.bus " +
       "JOIN FETCH t.route " +
       "WHERE t.id = :tripId")
Optional<Trip> findByIdWithBus(@Param("tripId") Long tripId);
```

---

#### 5.2. Unnecessary EntityManager Operations
**File**: `TicketService.java` (Line 88-89)

```java
// ❌ QUESTIONABLE: Flush và refresh không cần thiết
Ticket savedTicket = ticketRepository.save(ticket);
entityManager.flush();
entityManager.refresh(savedTicket);
```

**Vấn đề**:
- `save()` đã auto-flush trong transaction
- `refresh()` trigger thêm SELECT query

**Solution**:
```java
// ✅ CORRECT: Chỉ cần save
Ticket savedTicket = ticketRepository.save(ticket);
// Spring Data JPA sẽ tự động manage lifecycle
```

---

#### 5.3. Stream Without Parallel Processing
**File**: `RevenueService.java` (Line 59-63)

```java
// ⚠️ IMPROVEMENT: Có thể parallel
return result.stream().map(row -> Map.of(
    "busId", row[0],
    "plateNumber", row[1],
    "totalRevenue", row[2]
)).toList();
```

**Solution** (nếu list lớn):
```java
// ✅ BETTER: Parallel stream cho big data
return result.parallelStream()
    .map(row -> Map.of(
        "busId", row[0],
        "plateNumber", row[1],
        "totalRevenue", row[2]
    ))
    .toList();
```

---

#### 5.4. Không Dùng Pagination
**File**: `TicketService.java`, `RevenueService.java`

```java
// ❌ MISSING: Không có pagination cho list methods
public List<Ticket> getAllTickets() {
    return ticketRepository.findAll(); // Load toàn bộ DB!
}
```

**Solution**:
```java
// ✅ CORRECT: Always use Pageable
public Page<Ticket> getAllTickets(Pageable pageable) {
    return ticketRepository.findAll(pageable);
}
```

---

### 6. **Transaction Management** 🟡

#### 6.1. Missing @Transactional
**File**: `TicketService.java` - `createTicket()`, `updateTicket()`

```java
// ❌ MISSING: Không có @Transactional
public Ticket createTicket(TicketRequestDto responseDto) {
    // Multiple database operations
    ticketRepository.save(ticket);
    userRepository.save(customer);
    loyaltyPointRepository.save(loyaltyPoints);
}
```

**Vấn đề**:
- Không atomic - có thể bị inconsistent data
- Nếu lỗi giữa chừng, data bị corrupt

**Solution**:
```java
// ✅ CORRECT: Add @Transactional
@Transactional
public Ticket createTicket(TicketRequestDto responseDto) {
    // All or nothing
    ticketRepository.save(ticket);
    userRepository.save(customer);
    loyaltyPointRepository.save(loyaltyPoints);
}
```

---

#### 6.2. Wrong Transaction Isolation
**File**: `TripService.java` - `createTrip()`

```java
// ⚠️ RACE CONDITION: Kiểm tra bus status không atomic
if (bus.getStatus() == BusStatus.ACTIVE) {
    throw new IllegalArgumentException("Bus đang chạy chuyến khác.");
}
// ... (có thể bị race condition)
bus.setStatus(BusStatus.ACTIVE);
busRepository.save(bus);
```

**Solution**:
```java
// ✅ CORRECT: Pessimistic locking
@Transactional(isolation = Isolation.SERIALIZABLE)
public Trip createTrip(TripRequestDto requestDto) {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Buses bus = busRepository.findById(requestDto.getBusId())
        .orElseThrow(...);
    
    // Now safe to check and update
    if (bus.getStatus() == BusStatus.ACTIVE) {
        throw new BusAlreadyInUseException(bus.getId());
    }
    
    bus.setStatus(BusStatus.ACTIVE);
    return busRepository.save(bus);
}
```

---

### 7. **Code Style & Clean Code** 🟡

#### 7.1. Commented Out Code
**File**: `TicketService.java` (Line 91-116)

```java
// ❌ BAD: 25 dòng code bị comment
//        List<LoyaltyPoint> loyaltyPoint = loyaltyPointRepository...
//        customer.setUserStatus(CustomerStatus.BOOKED);
//        ...
```

**Solution**:
- Xóa code đã comment
- Dùng Git để track history
- Nếu cần reference, tạo ticket/note

---

#### 7.2. Magic Numbers
**File**: `TicketService.java` (Line 199)

```java
// ❌ BAD: Magic number 0.5
priceCustomer = responseDto.getPrice().multiply(new BigDecimal("0.5"));
```

**Solution**:
```java
// ✅ CORRECT: Named constants
public class DiscountConstants {
    public static final BigDecimal TICKET_DISCOUNT_RATE = new BigDecimal("0.50");
    public static final BigDecimal VIP_DISCOUNT_RATE = new BigDecimal("0.30");
}

// Usage
priceCustomer = responseDto.getPrice()
    .multiply(DiscountConstants.TICKET_DISCOUNT_RATE);
```

---

#### 7.3. Poor Variable Naming
**File**: `TripService.java` (Line 189)

```java
// ❌ BAD: Tên biến không rõ nghĩa
List<Map<String, Object>> content = rows.getContent().stream().map(row -> {
    Map<String, Object> map = new LinkedHashMap<>();
    map.put("busId", row[0]);
    // ...
```

**Solution**:
```java
// ✅ CORRECT: Clear naming
List<TripScheduleDto> tripSchedules = rows.getContent().stream()
    .map(this::mapToTripScheduleDto)
    .collect(Collectors.toList());

private TripScheduleDto mapToTripScheduleDto(Object[] row) {
    return TripScheduleDto.builder()
        .busId((Long) row[0])
        .plateNumber((String) row[1])
        .capacity((Integer) row[2])
        .busStatus((String) row[3])
        .build();
}
```

---

#### 7.4. Inconsistent Error Messages
```java
// ❌ INCONSISTENT: Mix English & Vietnamese
throw new RuntimeException("User not found");
throw new RuntimeException("Không tìm thấy người dùng");
throw new RuntimeException("Chuyến xe này đang trong chuyến!");
```

**Solution**:
```java
// ✅ CORRECT: Sử dụng i18n
@Service
public class TicketService {
    @Autowired
    private MessageSource messageSource;
    
    throw new ResourceNotFoundException(
        messageSource.getMessage("user.not.found", 
            new Object[]{userId}, 
            LocaleContextHolder.getLocale())
    );
}
```

---

### 8. **Logging Issues** 🟡

#### 8.1. System.out.println() Instead of Logger
**File**: Multiple files

```java
// ❌ BAD: Sử dụng System.out
System.out.println("thông tin booking tại producer: " + booking);
System.out.println("🔍 [Cache Miss] Đang gọi Database cho tripId: " + tripId);
System.out.println(userId);
```

**Solution**:
```java
// ✅ CORRECT: Sử dụng SLF4J Logger
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class BookingProducer {
    public void sendBookingEvent(TicketRequestDto booking) {
        log.info("Sending booking event: {}", booking);
        log.debug("Booking details - Customer: {}, Trip: {}", 
            booking.getCustomerEmail(), booking.getTripId());
    }
}

@Slf4j
@Service
public class TripService {
    public RevenueResponse getRevenue(int tripId) {
        log.debug("Cache miss for tripId: {}", tripId);
        log.info("Successfully fetched revenue for tripId: {}", tripId);
    }
}
```

---

#### 8.2. Không Log Exception Chi Tiết
**File**: `TripService.java` (Line 88)

```java
// ❌ BAD: printStackTrace() trong production
catch (Exception e) {
    e.printStackTrace(); // Không structured logging
    throw new RuntimeException("Lỗi xử lý doanh thu: " + e.getMessage());
}
```

**Solution**:
```java
// ✅ CORRECT: Structured logging
@Slf4j
@Service
public class TripService {
    catch (Exception e) {
        log.error("Error processing revenue for tripId: {}", tripId, e);
        throw new RevenueProcessingException(
            String.format("Failed to process revenue for trip %d", tripId), e);
    }
}
```

---

### 9. **JWT & Security Configuration** 🔴

#### 9.1. JWT Secret Không Đủ Mạnh
**File**: `application.properties`

```properties
# ❌ WEAK: Secret có thể bị crack
jwt.secret=Y2hhbmdlbWVzdXJlZ2VuZXJhdGVkMTIzNDU2Nzg5MDEyMzQ1Njc4OTAxMjM0NQ==
```

**Solution**:
```bash
# ✅ CORRECT: Generate strong secret (512-bit)
openssl rand -base64 64

# Hoặc dùng Java
KeyGenerator keyGen = KeyGenerator.getInstance("HmacSHA512");
SecretKey key = keyGen.generateKey();
String base64Key = Base64.getEncoder().encodeToString(key.getEncoded());
```

---

#### 9.2. JWT Token Expiration Giống Nhau
**File**: `JwtUtil.java` (Line 32, 44)

```java
// ❌ WRONG: Access token và refresh token có cùng expiration
long accessTokenExpiration = 1000 * 60 * 60 * 24; // 24h
long refreshTokenExpiration = 1000 * 60 * 60 * 24; // 24h ???
```

**Solution**:
```java
// ✅ CORRECT: Refresh token phải dài hơn
@Value("${jwt.access-token.expiration:900000}") // 15 phút
private long accessTokenExpiration;

@Value("${jwt.refresh-token.expiration:604800000}") // 7 ngày
private long refreshTokenExpiration;
```

---

#### 9.3. Không Validate Token Expiration
**File**: `JwtUtil.java` (Line 84-91)

```java
// ⚠️ METHOD EXISTS nhưng không được dùng trong filter
public boolean isTokenExpired(String token) {
    Claims claims = Jwts.parserBuilder()...
    return claims.getExpiration().before(new Date());
}
```

**Solution**:
```java
// ✅ CORRECT: Validate trong filter
public boolean validateToken(String token) {
    try {
        Jwts.parserBuilder()
            .setSigningKey(getSigningKey())
            .build()
            .parseClaimsJws(token);
        return !isTokenExpired(token); // Check expiration
    } catch (ExpiredJwtException e) {
        log.warn("JWT token is expired: {}", e.getMessage());
        return false;
    } catch (JwtException e) {
        log.error("JWT validation error: {}", e.getMessage());
        return false;
    }
}
```

---

### 10. **DTO & Validation Issues** 🟡

#### 10.1. Missing Input Validation
**File**: Controllers

```java
// ❌ MISSING: Không validate input
@PostMapping
public void addTicket(@RequestBody TicketRequestDto responseDto) {
    bookingProducer.sendBookingEvent(responseDto);
}
```

**Solution**:
```java
// ✅ CORRECT: Add validation
@PostMapping
public ResponseEntity<?> addTicket(
    @Valid @RequestBody TicketRequestDto responseDto) {
    bookingProducer.sendBookingEvent(responseDto);
    return ResponseEntity.ok().build();
}

// In DTO
@Data
public class TicketRequestDto {
    @NotNull(message = "Trip ID is required")
    private Long tripId;
    
    @NotNull(message = "Customer ID is required")
    private Long customerId;
    
    @NotNull(message = "Seat number is required")
    @Min(value = 1, message = "Seat number must be positive")
    private Integer seatNumber;
    
    @NotNull(message = "Price is required")
    @DecimalMin(value = "0.0", inclusive = false, message = "Price must be positive")
    private BigDecimal price;
    
    @Email(message = "Invalid email format")
    private String customerEmail;
}
```

---

### 11. **Redis Configuration Issues** 🟡

#### 11.1. Hardcoded Cache TTL
**File**: `RedisConfig.java`

```java
// ❌ MISSING: Không có TTL configuration cho cache
@Bean
public RedisCacheManager cacheManager(RedisConnectionFactory factory) {
    RedisCacheConfiguration config =
        RedisCacheConfiguration.defaultCacheConfig()
            .serializeKeysWith(...)
            .serializeValuesWith(...);
    // No TTL!
```

**Solution**:
```java
// ✅ CORRECT: Set TTL cho cache
@Bean
public RedisCacheManager cacheManager(RedisConnectionFactory factory) {
    RedisCacheConfiguration config =
        RedisCacheConfiguration.defaultCacheConfig()
            .entryTtl(Duration.ofMinutes(30)) // Cache expire sau 30 phút
            .disableCachingNullValues() // Không cache null
            .serializeKeysWith(
                RedisSerializationContext.SerializationPair
                    .fromSerializer(new StringRedisSerializer())
            )
            .serializeValuesWith(
                RedisSerializationContext.SerializationPair
                    .fromSerializer(new GenericJackson2JsonRedisSerializer())
            );
    
    // Custom TTL cho từng cache name
    Map<String, RedisCacheConfiguration> cacheConfigurations = new HashMap<>();
    cacheConfigurations.put("revenue_cache", config.entryTtl(Duration.ofHours(1)));
    cacheConfigurations.put("user_cache", config.entryTtl(Duration.ofMinutes(15)));
    
    return RedisCacheManager.builder(factory)
        .cacheDefaults(config)
        .withInitialCacheConfigurations(cacheConfigurations)
        .build();
}
```

---

### 12. **Kafka Configuration Issues** 🟡

#### 12.1. Error Handling Trong Producer
**File**: `BookingProducer.java` (Line 32-34)

```java
// ❌ BAD: Empty catch block
try {
    CompletableFuture<SendResult<String, Object>> future = ...
} catch (Exception e) {
    System.out.println("Lỗi tại message ");
    // Không re-throw, không retry!
}
```

**Solution**:
```java
// ✅ CORRECT: Proper error handling
@Slf4j
@Service
public class BookingProducer {
    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;
    
    public void sendBookingEvent(TicketRequestDto booking) {
        try {
            CompletableFuture<SendResult<String, Object>> future = 
                kafkaTemplate.send("order-events", booking.getCustomerEmail(), booking);
            
            future.whenComplete((result, ex) -> {
                if (ex == null) {
                    log.info("Booking event sent successfully. Offset: {}, Partition: {}", 
                        result.getRecordMetadata().offset(),
                        result.getRecordMetadata().partition());
                } else {
                    log.error("Failed to send booking event for customer: {}", 
                        booking.getCustomerEmail(), ex);
                    // Gửi notification, save to dead letter queue, etc.
                    handleSendFailure(booking, ex);
                }
            });
        } catch (Exception e) {
            log.error("Unexpected error sending booking event", e);
            throw new BookingEventException("Failed to send booking event", e);
        }
    }
    
    private void handleSendFailure(TicketRequestDto booking, Throwable error) {
        // Save to database for retry
        // Or send to DLQ (Dead Letter Queue)
    }
}
```

---

#### 12.2. Missing Kafka Error Handler
**File**: `application.properties`

```properties
# ❌ MISSING: Không có error handler configuration
spring.kafka.consumer.auto-offset-reset=earliest
```

**Solution**:
```java
// ✅ CORRECT: Add error handler
@Configuration
public class KafkaConsumerConfig {
    
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, Object> 
            kafkaListenerContainerFactory(
                ConsumerFactory<String, Object> consumerFactory) {
        
        ConcurrentKafkaListenerContainerFactory<String, Object> factory =
            new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        
        // Error handler
        factory.setCommonErrorHandler(new DefaultErrorHandler(
            new FixedBackOff(1000L, 3L) // Retry 3 lần, mỗi lần cách 1s
        ));
        
        return factory;
    }
}
```

---

## 📋 REFACTORING RECOMMENDATIONS

### 1. Service Layer Improvements

#### Current Structure (❌):
```
TicketService
  ├── createTicket()          [150 lines]
  ├── updateTicket()          [80 lines]
  ├── createTicketByLoyalty() [100 lines]
  └── cancelTicket()          [20 lines]
```

#### Recommended Structure (✅):
```
TicketService (Orchestration layer)
  ├── TripValidator
  ├── UserValidator
  ├── BusCapacityChecker
  ├── LoyaltyPointsCalculator
  └── TicketRepository

// Extract validators
@Component
public class TripValidator {
    public Trip validateAndGetAvailableTrip(Long tripId) {
        // All trip validation logic
    }
}

@Component
public class BusCapacityChecker {
    public void checkCapacity(Long busId, Integer requiredSeats) {
        // Capacity check logic
    }
}

// Ticket Service becomes cleaner
@Service
@RequiredArgsConstructor
@Slf4j
public class TicketService {
    private final TicketRepository ticketRepository;
    private final TripValidator tripValidator;
    private final UserValidator userValidator;
    private final BusCapacityChecker capacityChecker;
    private final LoyaltyPointsService loyaltyPointsService;
    
    @Transactional
    public Ticket createTicket(TicketRequestDto dto) {
        // Clean orchestration
        Trip trip = tripValidator.validateAndGetAvailableTrip(dto.getTripId());
        User customer = userValidator.validateCustomer(dto.getCustomerId());
        User seller = userValidator.validateSeller(dto.getSellerId());
        capacityChecker.checkCapacity(trip.getBus().getId(), 1);
        
        Ticket ticket = buildTicket(trip, customer, seller, dto);
        Ticket saved = ticketRepository.save(ticket);
        
        loyaltyPointsService.addPointsForBooking(customer, ticket);
        
        return saved;
    }
}
```

---

### 2. Domain Event Pattern

#### Current (❌): Tight coupling
```java
// TicketService trực tiếp update user status, loyalty points, etc.
ticket = ticketRepository.save(ticket);
customer.setUserStatus(CustomerStatus.BOOKED);
userRepository.save(customer);
loyaltyPointRepository.save(loyaltyPoints);
```

#### Recommended (✅): Event-driven
```java
// 1. Define events
public class TicketBookedEvent extends ApplicationEvent {
    private final Ticket ticket;
    private final User customer;
    
    public TicketBookedEvent(Object source, Ticket ticket, User customer) {
        super(source);
        this.ticket = ticket;
        this.customer = customer;
    }
}

// 2. Publish event
@Service
@RequiredArgsConstructor
public class TicketService {
    private final ApplicationEventPublisher eventPublisher;
    
    @Transactional
    public Ticket createTicket(TicketRequestDto dto) {
        // ... create ticket logic ...
        
        // Publish event
        eventPublisher.publishEvent(
            new TicketBookedEvent(this, ticket, customer)
        );
        
        return ticket;
    }
}

// 3. Handle events separately
@Component
@Slf4j
public class TicketEventHandler {
    
    @Async
    @EventListener
    @Transactional
    public void handleTicketBooked(TicketBookedEvent event) {
        // Update customer status
        event.getCustomer().setUserStatus(CustomerStatus.BOOKED);
    }
    
    @Async
    @EventListener
    public void addLoyaltyPoints(TicketBookedEvent event) {
        // Calculate and add loyalty points
        loyaltyPointsService.addPoints(event.getCustomer(), event.getTicket());
    }
    
    @Async
    @EventListener
    public void sendConfirmationEmail(TicketBookedEvent event) {
        // Send email
        emailService.sendBookingConfirmation(event.getTicket());
    }
}
```

---

### 3. Repository Custom Implementations

#### Current (❌): Business logic in repository
```java
@Query(value = """
    SELECT COUNT(tk.id), SUM(tk.price), ...
    FROM tickets tk
    JOIN trips tr ON tk.trip_id = tr.id
    WHERE ...
    GROUP BY ...
""", nativeQuery = true)
List<Map<String, Object>> getComplexData(...);
```

#### Recommended (✅): Use specifications
```java
// 1. Create specifications
public class TicketSpecifications {
    public static Specification<Ticket> hasBusId(Long busId) {
        return (root, query, cb) -> {
            Join<Ticket, Trip> trip = root.join("trip");
            Join<Trip, Bus> bus = trip.join("bus");
            return cb.equal(bus.get("id"), busId);
        };
    }
    
    public static Specification<Ticket> issuedBetween(
            LocalDateTime from, LocalDateTime to) {
        return (root, query, cb) -> 
            cb.between(root.get("issuedAt"), from, to);
    }
}

// 2. Use in repository
public interface TicketRepository extends 
        JpaRepository<Ticket, Long>, 
        JpaSpecificationExecutor<Ticket> {
}

// 3. Use in service
public List<Ticket> getTicketsByCriteria(Long busId, LocalDateTime from, LocalDateTime to) {
    Specification<Ticket> spec = Specification
        .where(TicketSpecifications.hasBusId(busId))
        .and(TicketSpecifications.issuedBetween(from, to));
    
    return ticketRepository.findAll(spec);
}
```

---

### 4. API Response Standardization

#### Current (❌): Inconsistent responses
```java
// Some return BaseResponseDto, some return entity directly
public ResponseEntity<BaseResponseDto<TokenResponse>> login(...) {...}
public void addTicket(...) {...} // No response
public Ticket createTicket(...) {...} // Direct entity
```

#### Recommended (✅): Consistent wrapper
```java
// 1. Standard API response
@Data
@Builder
public class ApiResponse<T> {
    private int status;
    private String message;
    private T data;
    private LocalDateTime timestamp;
    private List<String> errors;
    
    public static <T> ApiResponse<T> success(T data) {
        return ApiResponse.<T>builder()
            .status(HttpStatus.OK.value())
            .message("Success")
            .data(data)
            .timestamp(LocalDateTime.now())
            .build();
    }
    
    public static <T> ApiResponse<T> error(String message, List<String> errors) {
        return ApiResponse.<T>builder()
            .status(HttpStatus.BAD_REQUEST.value())
            .message(message)
            .errors(errors)
            .timestamp(LocalDateTime.now())
            .build();
    }
}

// 2. Global ResponseBodyAdvice
@RestControllerAdvice
public class GlobalResponseHandler implements ResponseBodyAdvice<Object> {
    
    @Override
    public boolean supports(MethodParameter returnType, 
                          Class<? extends HttpMessageConverter<?>> converterType) {
        return true;
    }
    
    @Override
    public Object beforeBodyWrite(Object body, 
                                MethodParameter returnType,
                                MediaType selectedContentType,
                                Class<? extends HttpMessageConverter<?>> selectedConverterType,
                                ServerHttpRequest request,
                                ServerHttpResponse response) {
        
        // Wrap if not already wrapped
        if (body instanceof ApiResponse) {
            return body;
        }
        
        return ApiResponse.success(body);
    }
}
```

---

## 🎯 PERFORMANCE OPTIMIZATION RECOMMENDATIONS

### 1. Database Indexing

```sql
-- ✅ ADD INDEXES cho các query thường dùng

-- Tickets
CREATE INDEX idx_tickets_trip_id ON tickets(trip_id);
CREATE INDEX idx_tickets_customer_id ON tickets(customer_id);
CREATE INDEX idx_tickets_seller_id ON tickets(seller_id);
CREATE INDEX idx_tickets_issued_at ON tickets(issued_at);
CREATE INDEX idx_tickets_status ON tickets(status_ticket);

-- Trips
CREATE INDEX idx_trips_route_id ON trips(route_id);
CREATE INDEX idx_trips_bus_id ON trips(bus_id);
CREATE INDEX idx_trips_driver_id ON trips(driver_id);
CREATE INDEX idx_trips_departure_time ON trips(departure_time);
CREATE INDEX idx_trips_status ON trips(status);

-- Composite indexes
CREATE INDEX idx_tickets_trip_status ON tickets(trip_id, status_ticket);
CREATE INDEX idx_trips_bus_status ON trips(bus_id, status);
```

---

### 2. Connection Pool Tuning

```properties
# ✅ OPTIMIZE HikariCP settings
spring.datasource.hikari.maximum-pool-size=20
spring.datasource.hikari.minimum-idle=5
spring.datasource.hikari.connection-timeout=30000
spring.datasource.hikari.idle-timeout=600000
spring.datasource.hikari.max-lifetime=1800000
spring.datasource.hikari.leak-detection-threshold=60000
```

---

### 3. JPA Query Optimization

```java
// ✅ FETCH JOINS để tránh N+1
@Query("SELECT t FROM Ticket t " +
       "JOIN FETCH t.trip tr " +
       "JOIN FETCH tr.bus " +
       "JOIN FETCH tr.route " +
       "JOIN FETCH t.customer " +
       "WHERE t.id = :ticketId")
Optional<Ticket> findByIdWithAllRelations(@Param("ticketId") Long ticketId);

// ✅ DTO Projection thay vì load full entity
@Query("SELECT new com.ticket_system.dto.TicketSummaryDto(" +
       "t.id, t.seatNumber, t.price, " +
       "c.email, tr.departureTime) " +
       "FROM Ticket t " +
       "JOIN t.customer c " +
       "JOIN t.trip tr " +
       "WHERE t.statusTicket = :status")
List<TicketSummaryDto> findTicketSummariesByStatus(@Param("status") TicketStatus status);
```

---

### 4. Caching Strategy

```java
// ✅ MULTI-LEVEL CACHING

// L1: Local cache (Caffeine)
@Configuration
@EnableCaching
public class CacheConfig {
    
    @Bean
    public CacheManager cacheManager(RedisConnectionFactory redis) {
        // L1: Caffeine (local)
        CaffeineCacheManager caffeine = new CaffeineCacheManager();
        caffeine.setCaffeine(Caffeine.newBuilder()
            .maximumSize(1000)
            .expireAfterWrite(Duration.ofMinutes(5))
            .recordStats());
        
        // L2: Redis (distributed)
        RedisCacheConfiguration redisConfig = RedisCacheConfiguration
            .defaultCacheConfig()
            .entryTtl(Duration.ofMinutes(30));
        
        RedisCacheManager redisCache = RedisCacheManager.builder(redis)
            .cacheDefaults(redisConfig)
            .build();
        
        // Combine both
        return new CompositeCacheManager(caffeine, redisCache);
    }
}

// Usage
@Cacheable(value = "routes", key = "#id", unless = "#result == null")
public Route getRouteById(Long id) {
    return routeRepository.findById(id)
        .orElseThrow(() -> new RouteNotFoundException(id));
}

@CacheEvict(value = "routes", key = "#id")
public void updateRoute(Long id, Route route) {
    // Update logic
}
```

---

### 5. Async Processing

```java
// ✅ ASYNC cho non-critical operations

@Configuration
@EnableAsync
public class AsyncConfig {
    
    @Bean(name = "taskExecutor")
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5);
        executor.setMaxPoolSize(10);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("async-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}

// Service
@Service
public class EmailService {
    
    @Async("taskExecutor")
    public CompletableFuture<Void> sendBookingConfirmation(Ticket ticket) {
        // Send email logic
        return CompletableFuture.completedFuture(null);
    }
}

// Usage in TicketService
@Transactional
public Ticket createTicket(TicketRequestDto dto) {
    // Synchronous critical operations
    Ticket saved = ticketRepository.save(ticket);
    
    // Asynchronous non-critical operations
    emailService.sendBookingConfirmation(saved); // Non-blocking
    
    return saved;
}
```

---

## 📊 CODE METRICS SUMMARY

### Complexity Analysis

| Metric | Current | Target | Status |
|--------|---------|--------|--------|
| Cyclomatic Complexity | 8-15 | < 10 | ⚠️ Need Refactor |
| Lines per Method | 50-150 | < 30 | ❌ Too Long |
| Code Duplication | ~15% | < 5% | ❌ High |
| Test Coverage | 0% | > 80% | ❌ No Tests |
| Technical Debt | High | Low | ❌ Critical |

---

### Security Score

| Category | Score | Issues |
|----------|-------|--------|
| Authentication | 5/10 | Hardcoded authorities |
| Authorization | 4/10 | Weak JWT validation |
| Data Protection | 4/10 | Hardcoded secrets |
| Input Validation | 6/10 | Missing validations |
| Error Handling | 5/10 | Info leakage |

---

## ✅ ACTION ITEMS (Priority Order)

### 🔴 CRITICAL (Fix Immediately)

1. **[SECURITY]** Fix hardcoded ADMIN authority in JwtAuthFilter
2. **[SECURITY]** Move all secrets to environment variables
3. **[SECURITY]** Implement proper role extraction from JWT
4. **[BUG]** Add @Transactional to all multi-operation methods
5. **[BUG]** Fix potential race conditions in status checks

### 🟡 HIGH (Fix This Sprint)

6. **[REFACTOR]** Replace field injection with constructor injection
7. **[REFACTOR]** Extract duplicate validation logic
8. **[PERF]** Add database indexes
9. **[PERF]** Optimize N+1 queries with fetch joins
10. **[CODE]** Replace System.out with proper logging

### 🟢 MEDIUM (Next Sprint)

11. **[ARCH]** Implement domain event pattern
12. **[ARCH]** Separate validators into components
13. **[CODE]** Remove commented code blocks
14. **[CODE]** Standardize error messages with i18n
15. **[TEST]** Add unit tests (target 80% coverage)

### 🔵 LOW (Backlog)

16. **[DOCS]** Add JavaDoc to all public methods
17. **[PERF]** Implement multi-level caching
18. **[FEATURE]** Add health check endpoints
19. **[FEATURE]** Add metrics and monitoring
20. **[REFACTOR]** Implement specification pattern

---

## 📚 BEST PRACTICES CHECKLIST

### Code Quality
- [ ] Tất cả services dùng constructor injection
- [ ] Không còn field injection (@Autowired trên field)
- [ ] Tất cả exceptions là custom exceptions
- [ ] Logging dùng SLF4J thay vì System.out
- [ ] Không còn commented code
- [ ] Magic numbers được extract thành constants

### Security
- [ ] Không còn hardcoded secrets
- [ ] JWT validation đầy đủ (signature + expiration)
- [ ] Authorities được extract từ token
- [ ] Input validation với @Valid
- [ ] Rate limiting cho APIs
- [ ] CORS configuration đúng

### Performance
- [ ] Database indexes đầy đủ
- [ ] N+1 queries đã được fix
- [ ] Pagination cho tất cả list endpoints
- [ ] Caching cho data ít thay đổi
- [ ] Connection pool được tune
- [ ] Async processing cho non-critical operations

### Testing
- [ ] Unit tests > 80% coverage
- [ ] Integration tests cho critical flows
- [ ] Security tests
- [ ] Performance tests
- [ ] Contract tests cho APIs

### Documentation
- [ ] API documentation (Swagger/OpenAPI)
- [ ] JavaDoc cho public methods
- [ ] README with setup instructions
- [ ] Architecture decision records
- [ ] Deployment guide

---

## 🎓 LEARNING RESOURCES

### Books
1. "Clean Code" - Robert C. Martin
2. "Effective Java" - Joshua Bloch
3. "Spring in Action" - Craig Walls
4. "Java Performance" - Scott Oaks

### Online Courses
1. Baeldung - Spring Security
2. Pluralsight - Spring Boot Best Practices
3. Udemy - Microservices with Spring Boot

### Articles
1. [Spring Security Architecture](https://spring.io/guides/topicals/spring-security-architecture)
2. [JPA Best Practices](https://thoughts-on-java.org/tips-to-boost-your-hibernate-performance/)
3. [Microservices Patterns](https://microservices.io/patterns/)

---

## 📝 CONCLUSION

Dự án **Ticket System** có nền tảng tốt với architecture microservices đúng hướng. Tuy nhiên, có **nhiều vấn đề nghiêm trọng** cần được fix ngay:

### Điểm Mạnh ✅
- Architecture microservices được thiết kế tốt
- Sử dụng đúng công nghệ hiện đại (Spring Boot, Kafka, Redis)
- Database schema hợp lý
- Có caching với Redis

### Điểm Yếu ❌
- **Security vulnerabilities nghiêm trọng**
- Thiếu proper exception handling
- Code duplication cao
- Không có tests
- Missing transaction management
- Poor logging practices
- Hardcoded values

### Next Steps 🎯
1. Fix tất cả CRITICAL issues trong 1 tuần
2. Refactor code theo recommendations
3. Add comprehensive tests
4. Setup CI/CD pipeline
5. Add monitoring & alerting

**Overall Recommendation**: **REFACTOR REQUIRED** trước khi deploy production!

---

**Reviewed by**: Senior Java Spring Boot Developer  
**Review Date**: April 9, 2026  
**Next Review**: After critical fixes implemented

---

## 📧 Contact for Questions

Nếu có thắc mắc về review này, vui lòng liên hệ:
- Email: tech-lead@company.com
- Slack: #code-review-channel

---

*"Code is read much more often than it is written. Make it count."* - Uncle Bob

