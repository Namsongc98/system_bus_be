# 🎯 CODE REVIEW - QUICK REFERENCE CARD

## 📋 TÓM TẮT NHANH

### 🔴 TOP 5 CRITICAL (FIX NGAY!)

| # | Issue | File | Line | Fix Time |
|---|-------|------|------|----------|
| 1 | Hardcoded ADMIN role | `JwtAuthFilter.java` | 66 | 30min |
| 2 | Hardcoded secrets | `application.properties` | Multiple | 15min |
| 3 | Missing @Transactional | `TicketService.java` | Methods | 1h |
| 4 | Password encoder not bean | `AuthService.java` | 21 | 20min |
| 5 | Race condition in status | `TripService.java` | 108-119 | 1h |

**Total Effort**: ~3-4 hours

---

## 🎓 QUICK FIXES

### Fix #1: Hardcoded ADMIN Authority
```java
// ❌ BEFORE
List<GrantedAuthority> authorities =
    Collections.singletonList(new SimpleGrantedAuthority("ADMIN"));

// ✅ AFTER
String role = jwtUtil.getRoleFromToken(jwtToken);
List<GrantedAuthority> authorities =
    Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + role));
```
**Impact**: 🔴 CRITICAL - Security vulnerability
**Test**: Login với different roles, verify permissions

---

### Fix #2: Hardcoded Secrets
```properties
# ❌ BEFORE
spring.datasource.password=123456789
jwt.secret=Y2hhbmdl...

# ✅ AFTER
spring.datasource.password=${DB_PASSWORD}
jwt.secret=${JWT_SECRET}
```
**Setup ENV**:
```bash
export DB_PASSWORD=your_secure_password
export JWT_SECRET=$(openssl rand -base64 64)
```
**Impact**: 🔴 CRITICAL - Security risk
**Test**: App starts with env variables

---

### Fix #3: Missing @Transactional
```java
// ❌ BEFORE
public Ticket createTicket(TicketRequestDto dto) {
    ticketRepository.save(ticket);
    userRepository.save(customer);
    loyaltyPointRepository.save(points);
}

// ✅ AFTER
@Transactional
public Ticket createTicket(TicketRequestDto dto) {
    ticketRepository.save(ticket);
    userRepository.save(customer);
    loyaltyPointRepository.save(points);
}
```
**Impact**: 🔴 CRITICAL - Data consistency
**Test**: Throw exception mid-method, verify rollback

---

### Fix #4: Password Encoder Bean
```java
// ❌ BEFORE (AuthService.java)
private final BCryptPasswordEncoder passwordEncoder = 
    new BCryptPasswordEncoder();

// ✅ AFTER
// Step 1: Create Config
@Configuration
public class SecurityBeanConfig {
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }
}

// Step 2: Inject in Service
@Service
@RequiredArgsConstructor
public class AuthService {
    private final PasswordEncoder passwordEncoder;
}
```
**Impact**: 🟡 HIGH - Resource usage
**Test**: Register/login still works

---

### Fix #5: Race Condition
```java
// ❌ BEFORE
if (bus.getStatus() == BusStatus.ACTIVE) {
    throw new IllegalArgumentException("Bus busy");
}
bus.setStatus(BusStatus.ACTIVE);
busRepository.save(bus);

// ✅ AFTER
@Transactional(isolation = Isolation.SERIALIZABLE)
public Trip createTrip(TripRequestDto dto) {
    // Use pessimistic lock
    Buses bus = busRepository.findByIdWithLock(dto.getBusId())
        .orElseThrow(...);
    
    if (bus.getStatus() == BusStatus.ACTIVE) {
        throw new BusAlreadyInUseException(bus.getId());
    }
    
    bus.setStatus(BusStatus.ACTIVE);
    return busRepository.save(bus);
}

// In Repository
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT b FROM Buses b WHERE b.id = :id")
Optional<Buses> findByIdWithLock(@Param("id") Long id);
```
**Impact**: 🔴 CRITICAL - Concurrent booking issues
**Test**: Concurrent requests for same bus

---

## 🛠️ COMMON PATTERNS

### Pattern 1: Field Injection → Constructor
```java
// ❌ BEFORE
@Service
public class MyService {
    @Autowired
    private MyRepository repository;
}

// ✅ AFTER
@Service
@RequiredArgsConstructor
public class MyService {
    private final MyRepository repository;
}
```

---

### Pattern 2: Generic Exception → Custom
```java
// ❌ BEFORE
throw new RuntimeException("User not found");

// ✅ AFTER
public class UserNotFoundException extends RuntimeException {
    public UserNotFoundException(Long userId) {
        super("User not found: " + userId);
    }
}

throw new UserNotFoundException(userId);
```

---

### Pattern 3: System.out → Logger
```java
// ❌ BEFORE
System.out.println("Debug info: " + data);

// ✅ AFTER
@Slf4j
@Service
public class MyService {
    public void method() {
        log.info("Debug info: {}", data);
    }
}
```

---

### Pattern 4: Duplicate Code → Extract Method
```java
// ❌ BEFORE
Trip trip = tripRepository.findById(id)
    .orElseThrow(() -> new ResourceNotFoundException("Trip not found"));
if (trip.getStatus() == TripStatus.ONGOING) {
    throw new IllegalArgumentException("Trip ongoing");
}

// Repeated 3 times...

// ✅ AFTER
private Trip validateAndGetTrip(Long tripId) {
    Trip trip = tripRepository.findById(tripId)
        .orElseThrow(() -> new TripNotFoundException(tripId));
    
    if (trip.getStatus() == TripStatus.ONGOING) {
        throw new TripOngoingException(tripId);
    }
    
    return trip;
}

// Usage
Trip trip = validateAndGetTrip(dto.getTripId());
```

---

## 📊 CHEAT SHEET

### Security Checklist
```markdown
✅ No hardcoded passwords/secrets
✅ JWT validated properly (signature + expiration)
✅ Authorities from token, not hardcoded
✅ Password encoder is @Bean
✅ Input validation with @Valid
✅ SQL injection safe (parameterized queries)
```

---

### Performance Checklist
```markdown
✅ @Transactional on multi-DB operations
✅ No N+1 queries (use JOIN FETCH)
✅ Pagination on list methods
✅ Database indexes on FK and search columns
✅ Redis caching for frequent queries
✅ Async for non-critical operations
```

---

### Code Quality Checklist
```markdown
✅ Constructor injection (not field)
✅ Custom exceptions (not RuntimeException)
✅ SLF4J logging (not System.out)
✅ No commented code
✅ No magic numbers (use constants)
✅ Clear variable names
✅ Methods < 30 lines
```

---

## 🚀 QUICK COMMANDS

### Run Tests
```bash
mvn clean test
```

### Check Code Quality
```bash
mvn sonar:sonar
```

### Find Hardcoded Secrets
```bash
grep -r "password=" --include="*.properties" .
grep -r "secret=" --include="*.properties" .
```

### Find System.out.println
```bash
grep -r "System.out" --include="*.java" src/
```

### Find Field Injection
```bash
grep -r "@Autowired" --include="*.java" src/ | grep "private"
```

### Find Missing @Transactional
```bash
# Look for methods with multiple save() calls
grep -A10 "\.save(" src/**/*.java
```

---

## 📝 PR TEMPLATE

```markdown
## Description
Brief description of changes

## Related Issue
Fixes #123
Reference: CODE_REVIEW_REPORT.md section X.Y

## Type of Change
- [ ] 🔴 Critical Fix
- [ ] 🐛 Bug Fix
- [ ] ♻️ Refactor
- [ ] ✨ New Feature
- [ ] 📝 Documentation
- [ ] ⚡ Performance

## Changes Made
- Change 1
- Change 2

## Testing
- [ ] Unit tests added/updated
- [ ] Integration tests pass
- [ ] Manual testing done

## Checklist
- [ ] Code follows style guidelines
- [ ] Self-review completed
- [ ] No hardcoded secrets
- [ ] Proper exception handling
- [ ] Logging added
- [ ] Documentation updated

## Screenshots/Evidence
(if applicable)
```

---

## 🎯 DAILY WORKFLOW

### Morning (30min)
1. ✅ Pull latest code: `git pull origin main`
2. ✅ Check CI/CD status
3. ✅ Review assigned issues
4. ✅ Plan today's fix

### During Work (4-6h)
1. ✅ Pick ONE issue from list
2. ✅ Read relevant section in CODE_REVIEW_REPORT
3. ✅ Implement fix
4. ✅ Write/update tests
5. ✅ Run tests locally
6. ✅ Commit with proper message
7. ✅ Create PR

### End of Day (30min)
1. ✅ Push all commits
2. ✅ Update issue status
3. ✅ Review someone else's PR
4. ✅ Document blockers

---

## 🆘 EMERGENCY CONTACTS

### When You're Stuck
- **Slack**: #code-review-help
- **Tech Lead**: @tech-lead
- **Senior Dev**: @senior-dev
- **Security Expert**: @security-team

### Reference Links
- [Full Report](CODE_REVIEW_REPORT.md)
- [Usage Guide](CODE_REVIEW_USAGE_GUIDE.md)
- [Project Docs](PROJECT_INSTRUCTIONS.md)

---

## 📈 METRICS TO TRACK

```markdown
Weekly:
- [ ] Issues fixed: X/20
- [ ] PRs merged: X
- [ ] Tests added: X
- [ ] Code coverage: X%

Monthly:
- [ ] SonarQube score: X.X/10
- [ ] Security vulnerabilities: X
- [ ] Average PR review time: Xh
- [ ] Build time: Xm
```

---

## 💡 QUICK TIPS

1. **Start Small**: Fix one issue completely before moving to next
2. **Test First**: Add tests before refactoring
3. **Small PRs**: < 300 lines of changes
4. **Ask Questions**: Better to ask than guess
5. **Document Changes**: Future you will thank you
6. **Review Others**: Learn from peer code
7. **Celebrate Wins**: Mark completed issues ✅

---

## 🎓 LEARNING RESOURCES

### Must Read (This Week)
- [ ] CODE_REVIEW_REPORT.md - Executive Summary
- [ ] CODE_REVIEW_REPORT.md - Your relevant sections
- [ ] OWASP Top 10 Security Risks

### Recommended (This Month)
- [ ] Clean Code (Robert Martin) - Chapters 1-3
- [ ] Effective Java (Joshua Bloch) - Items 1-10
- [ ] Spring Security Reference - Authentication

### Watch (When Available)
- [ ] Team Knowledge Sharing Sessions
- [ ] Spring Boot Best Practices (YouTube)
- [ ] Java Performance Tuning

---

**Print This Card** 🖨️  
Keep it visible at your desk!

**Last Updated**: April 9, 2026  
**Quick Reference Version**: 1.0.0

