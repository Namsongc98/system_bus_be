# 🚀 Code Review CLI - SUPER SHORT COMMANDS

## ✅ Đã Setup Xong!

Bây giờ bạn có thể dùng **4 CÁCH** để gọi commands:

---

## 1️⃣ CÁCH NGẮN NHẤT (Sau khi setup)

### Option A: Dùng wrapper script
```bash
cd /Users/ADMIN/Desktop/Wordspace/Programing/Java/ticket-system

# Ultra short commands (không cần /)
./review scan          # Thay vì ./code-review.sh /scan
./review s             # Còn ngắn hơn nữa!
./review a BusService.java
./review fix
./review m             # metrics
```

### Option B: Global command (nếu có sudo)
```bash
# Setup 1 lần (cần password)
cd /Users/ADMIN/Desktop/Wordspace/Programing/Java/ticket-system
sudo ln -s $(pwd)/review /usr/local/bin/review

# Sau đó dùng từ mọi nơi:
review scan
review s
review fix
```

---

## 2️⃣ CÁCH VỪA PHẢI

### Dùng alias (đã setup)
```bash
# Đã có trong ~/.bashrc hoặc ~/.zshrc
review /scan
review /security
review /fix
```

---

## 3️⃣ CÁCH ĐẦY ĐỦ (Luôn work)

```bash
cd /Users/ADMIN/Desktop/Wordspace/Programing/Java/ticket-system
./code-review.sh /scan
./code-review.sh /fix
```

---

## 🎯 SO SÁNH

| Cách | Command | Độ dài | Setup |
|------|---------|--------|-------|
| **Ultra Short** | `review s` | ⭐⭐⭐⭐⭐ | Cần wrapper |
| **Short** | `review /scan` | ⭐⭐⭐⭐ | Đã có alias |
| **Normal** | `./code-review.sh /scan` | ⭐⭐⭐ | Không cần |
| **Full** | `cd ... && ./code-review.sh /scan` | ⭐⭐ | Không cần |

---

## 💡 DEMO VỚI FILE CỦA BẠN

### Analyze BusService.java
```bash
# Cách 1: Short
cd /Users/ADMIN/Desktop/Wordspace/Programing/Java/ticket-system
./review a manage-revenue-ticket/src/main/java/com/ticket_system/manage_revenue_ticket/service/BusService.java

# Cách 2: Normal
./code-review.sh /analyze manage-revenue-ticket/src/main/java/com/ticket_system/manage_revenue_ticket/service/BusService.java
```

### Output đã test:
```
╔═══════════════════════════════════════════════════════════╗
║  🔍 Code Review CLI Tool v1.0.0                     ║
╔═══════════════════════════════════════════════════════════╗

▶ Analyzing: BusService.java
────────────────────────────────────────────────────────────
📄 File Stats:
   Lines:       47
   Methods: 5

✅ File size is reasonable

📊 Issue Check:
⚠️  Generic RuntimeException found - use custom exceptions
```

---

## 🔧 QUICK SETUP

### Setup Ultra-Short Commands (30s)
```bash
cd /Users/ADMIN/Desktop/Wordspace/Programing/Java/ticket-system

# Make wrapper executable
chmod +x review

# Test locally first
./review scan
./review a BusService.java

# If works, make global (optional - needs password)
sudo ln -s $(pwd)/review /usr/local/bin/review

# Now use from anywhere!
cd ~
review scan
```

---

## 📚 ALL SHORT COMMANDS

| Full Command | Short | Ultra Short |
|-------------|-------|-------------|
| `/scan` | `scan` | `s` |
| `/analyze` | `analyze` | `a` |
| `/security` | `security` | `sec` |
| `/performance` | `performance` | `p` |
| `/fix` | `fix` | `f` |
| `/report` | `report` | `r` |
| `/test` | `test` | `t` |
| `/metrics` | `metrics` | `m` |

### Examples:
```bash
# All these work:
./review scan
./review s

./review analyze BusService.java
./review a BusService.java

./review performance
./review perf
./review p

./review metrics
./review m
```

---

## 🎯 USE CASES

### Morning Check
```bash
cd project
./review s          # Quick scan
./review sec        # Security
./review m          # Metrics
```

### Before Commit
```bash
./review s          # Scan
./review t          # Test
git commit -m "..."
```

### Code Review File
```bash
./review a src/main/java/Service.java
./review f src/main/java/Service.java
```

### Weekly Report
```bash
./review r          # Full report
```

---

## 💻 INTEGRATE VỚI IDE

### VS Code Task
Create `.vscode/tasks.json`:
```json
{
  "version": "2.0.0",
  "tasks": [
    {
      "label": "Code Review Scan",
      "type": "shell",
      "command": "./review",
      "args": ["scan"],
      "problemMatcher": [],
      "group": "build"
    }
  ]
}
```

### IntelliJ IDEA External Tool
```
Settings → Tools → External Tools → +

Name: Code Review
Program: $ProjectFileDir$/review
Arguments: scan
Working directory: $ProjectFileDir$
```

---

## 🎬 VIDEO DEMO

```bash
# Scenario: Review BusService.java

# Step 1: Navigate
cd /Users/ADMIN/Desktop/Wordspace/Programing/Java/ticket-system

# Step 2: Quick scan
./review s
# Output: ⚠️  Found 1 types of issues

# Step 3: Analyze specific file
./review a manage-revenue-ticket/src/main/java/com/ticket_system/manage_revenue_ticket/service/BusService.java
# Output: ⚠️  Generic RuntimeException found

# Step 4: Get fix suggestions
./review f
# Output: ✅ Fix suggestions saved

# Step 5: Check metrics
./review m
# Output: 📈 Quality Score: 100%

# DONE! 🎉
```

---

## 🐛 ISSUES FOUND IN BusService.java

Từ kết quả analyze:
```
⚠️  Generic RuntimeException found - use custom exceptions
```

### Fix Suggestion:
```java
// ❌ BEFORE (Line 22, 35)
throw new RuntimeException("Biển số xe đã tồn tại: " + request.getPlateNumber());
throw new RuntimeException("Không tìm thấy xe với id: " + busId);

// ✅ AFTER
// Create custom exceptions
public class BusAlreadyExistsException extends RuntimeException {
    public BusAlreadyExistsException(String plateNumber) {
        super("Biển số xe đã tồn tại: " + plateNumber);
    }
}

public class BusNotFoundException extends RuntimeException {
    public BusNotFoundException(Long busId) {
        super("Không tìm thấy xe với id: " + busId);
    }
}

// Usage
throw new BusAlreadyExistsException(request.getPlateNumber());
throw new BusNotFoundException(busId);
```

---

## 🎉 YOU'RE READY!

Now you can use:
```bash
./review s          # Ultra short!
./review scan       # Short
./code-review.sh /scan  # Full (always works)
```

**Choose your style! 😊**

---

## 📞 Quick Reference

```bash
# Location
cd /Users/ADMIN/Desktop/Wordspace/Programing/Java/ticket-system

# Commands
./review help       # Show all commands
./review s          # Quick scan
./review a FILE     # Analyze file
./review f          # Fix suggestions

# Test with your file
./review a manage-revenue-ticket/src/main/java/com/ticket_system/manage_revenue_ticket/service/BusService.java
```

**Enjoy! 🚀**

