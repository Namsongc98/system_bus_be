# 🔍 Code Review CLI Tool - Installation Guide

## ✅ Tool đã được cài đặt!

### 📍 Location
```
/Users/ADMIN/Desktop/Wordspace/Programing/Java/ticket-system/code-review.sh
```

---

## 🚀 Cách Sử Dụng

### Option 1: Chạy trực tiếp (✅ WORKING)
```bash
cd /Users/ADMIN/Desktop/Wordspace/Programing/Java/ticket-system
./code-review.sh /scan
./code-review.sh /security
./code-review.sh /fix
```

### Option 2: Tạo alias (Recommended)

#### Thêm vào ~/.zshrc:
```bash
# Mở file config
nano ~/.zshrc

# Thêm dòng này vào cuối file:
alias review='cd /Users/ADMIN/Desktop/Wordspace/Programing/Java/ticket-system && ./code-review.sh'

# Save (Ctrl+O, Enter, Ctrl+X)

# Reload config
source ~/.zshrc
```

#### Sau đó dùng như này:
```bash
review /scan
review /security
review /fix
review /metrics
```

### Option 3: Global command (Advanced)
```bash
# Copy to /usr/local/bin
sudo cp code-review.sh /usr/local/bin/review
sudo chmod +x /usr/local/bin/review

# Dùng từ mọi nơi
review /scan
```

---

## 📚 Available Commands

### 🔍 `/scan` - Quick scan
```bash
./code-review.sh /scan
```
**Output**: Tìm các lỗi phổ biến:
- ❌ Hardcoded secrets
- ❌ System.out.println
- ❌ Field injection
- ❌ Missing @Transactional
- ❌ TODO/FIXME comments

---

### 🔒 `/security` - Security scan
```bash
./code-review.sh /security
```
**Output**: Kiểm tra bảo mật:
- SQL injection risks
- Weak random generators
- Missing input validation
- Hardcoded credentials

---

### ⚡ `/performance` - Performance check
```bash
./code-review.sh /performance
```
**Output**: Phân tích performance:
- N+1 query problems
- Missing pagination
- Large methods (>50 lines)
- Database indexes

---

### 📄 `/analyze` - Analyze file
```bash
./code-review.sh /analyze src/main/java/com/example/Service.java
```
**Output**: Phân tích chi tiết 1 file:
- Line count
- Method count
- Code issues
- Complexity

---

### 🔧 `/fix` - Auto-fix suggestions
```bash
./code-review.sh /fix

# Hoặc fix specific file
./code-review.sh /fix src/main/java/
```
**Output**: Tạo report với code examples để fix

---

### 📊 `/report` - Full report
```bash
./code-review.sh /report
```
**Output**: Generate full markdown report với:
- Security scan
- Performance analysis
- Common issues
- Recommendations

---

### 📈 `/metrics` - Code metrics
```bash
./code-review.sh /metrics
```
**Output**: Thống kê project:
- Number of Java files
- Total lines of code
- Test files ratio
- Quality score

---

### 🧪 `/test` - Run tests
```bash
./code-review.sh /test
```
**Output**: Run Maven/Gradle tests

---

## 🎯 Workflow Đề Xuất

### Morning Routine
```bash
# 1. Quick scan
./code-review.sh /scan

# 2. Nếu có issues, xem chi tiết
./code-review.sh /security
./code-review.sh /performance

# 3. Get fix suggestions
./code-review.sh /fix
```

### Before Commit
```bash
# 1. Scan changes
./code-review.sh /scan

# 2. Run tests
./code-review.sh /test

# 3. Check metrics
./code-review.sh /metrics
```

### Weekly Report
```bash
# Generate full report
./code-review.sh /report

# Opens in browser/editor
```

---

## 📊 Real Output Examples

### Example 1: /scan output
```
╔═══════════════════════════════════════════════════════════╗
║  🔍 Code Review CLI Tool v1.0.0                     ║
╔═══════════════════════════════════════════════════════════╗

▶ Scanning for Common Issues
────────────────────────────────────────────────────────────
ℹ️  Checking for hardcoded secrets...
❌ Found hardcoded passwords in config files!
./application.properties:spring.datasource.password=<redacted>

⚠️  Found 1 types of issues
ℹ️  Run './code-review.sh fix' for suggestions
```

### Example 2: /metrics output
```
📊 Project Statistics
   Java Files: 85
   Total Lines: 8450
   Test Files: 12
   Test Ratio: 14.12%

🎯 Quality Indicators
   ❌ System.out.println found
   ✅ No field injection
   ⚠️  Generic RuntimeException used

📈 Quality Score: 66%
```

---

## 💡 Pro Tips

### Tip 1: Add to Git hooks
```bash
# .git/hooks/pre-commit
#!/bin/bash
./code-review.sh /scan
if [ $? -ne 0 ]; then
    echo "❌ Code review failed. Fix issues before commit."
    exit 1
fi
```

### Tip 2: Integrate with CI/CD
```yaml
# .github/workflows/code-review.yml
name: Code Review
on: [push, pull_request]
jobs:
  review:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v2
      - name: Run Code Review
        run: |
          chmod +x code-review.sh
          ./code-review.sh /scan
          ./code-review.sh /security
```

### Tip 3: Custom checks
Bạn có thể edit `code-review.sh` để thêm custom checks:
```bash
# Add your custom check
print_info "Checking for your custom rule..."
if grep -r "YourPattern" src/; then
    print_warning "Found your pattern"
fi
```

---

## 🔧 Customize

### Add new command
```bash
# In code-review.sh, add new function:
cmd_mycheck() {
    print_section "My Custom Check"
    # Your logic here
}

# Add to main() switch case:
/mycheck|mycheck)
    cmd_mycheck "$@"
    ;;
```

### Change colors
```bash
# Edit these variables in code-review.sh:
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
```

---

## 🆘 Troubleshooting

### Permission denied
```bash
chmod +x code-review.sh
```

### Command not found
```bash
# Use full path
/Users/ADMIN/Desktop/Wordspace/Programing/Java/ticket-system/code-review.sh /scan

# Or add to PATH
export PATH="$PATH:/Users/ADMIN/Desktop/Wordspace/Programing/Java/ticket-system"
```

### No output
```bash
# Check you're in project directory
pwd

# Should output: /Users/ADMIN/Desktop/Wordspace/Programing/Java/ticket-system
```

---

## 📞 Support

- **View code**: `cat code-review.sh`
- **Edit**: `nano code-review.sh`
- **Help**: `./code-review.sh /help`

---

## 🎉 Quick Test

```bash
# Test all commands
./code-review.sh /help
./code-review.sh /scan
./code-review.sh /security
./code-review.sh /metrics

# Should all work! ✅
```

---

**Tool Version**: 1.0.0  
**Created**: April 9, 2026  
**Status**: ✅ Ready to use!
