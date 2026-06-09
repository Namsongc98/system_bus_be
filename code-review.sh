#!/bin/bash

################################################################################
# 🔍 CODE REVIEW CLI TOOL
# Usage: ./code-review.sh [command] [options]
################################################################################

VERSION="1.0.0"
REPORT_DIR=".github/code-review-reports"
TIMESTAMP=$(date +"%Y%m%d_%H%M%S")

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
PURPLE='\033[0;35m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

################################################################################
# Helper Functions
################################################################################

print_header() {
    echo -e "${BLUE}╔═══════════════════════════════════════════════════════════╗${NC}"
    echo -e "${BLUE}║${NC}  🔍 ${CYAN}Code Review CLI Tool${NC} v${VERSION}                     ${BLUE}║${NC}"
    echo -e "${BLUE}╔═══════════════════════════════════════════════════════════╗${NC}"
}

print_section() {
    echo -e "\n${PURPLE}▶ $1${NC}"
    echo -e "${PURPLE}$(printf '─%.0s' {1..60})${NC}"
}

print_success() {
    echo -e "${GREEN}✅ $1${NC}"
}

print_error() {
    echo -e "${RED}❌ $1${NC}"
}

print_warning() {
    echo -e "${YELLOW}⚠️  $1${NC}"
}

print_info() {
    echo -e "${CYAN}ℹ️  $1${NC}"
}

################################################################################
# Command: /scan - Scan for common issues
################################################################################

cmd_scan() {
    print_section "Scanning for Common Issues"

    local issues_found=0

    # 1. Check for hardcoded secrets
    print_info "Checking for hardcoded secrets..."
    if grep -r "password=" --include="*.properties" --include="*.yml" . 2>/dev/null | grep -v ".git" > /dev/null; then
        print_error "Found hardcoded passwords in config files!"
        grep -r "password=" --include="*.properties" --include="*.yml" . 2>/dev/null | grep -v ".git"
        ((issues_found++))
    else
        print_success "No hardcoded passwords found"
    fi

    # 2. Check for System.out.println
    print_info "Checking for System.out.println..."
    local sysout_count=$(grep -r "System.out.println" --include="*.java" src/ 2>/dev/null | wc -l | tr -d ' ')
    if [ "$sysout_count" -gt 0 ]; then
        print_warning "Found $sysout_count instances of System.out.println"
        print_info "Use SLF4J logger instead"
        ((issues_found++))
    else
        print_success "No System.out.println found"
    fi

    # 3. Check for field injection
    print_info "Checking for field injection..."
    local field_injection=$(grep -r "@Autowired" --include="*.java" src/ 2>/dev/null | grep "private" | wc -l | tr -d ' ')
    if [ "$field_injection" -gt 0 ]; then
        print_warning "Found $field_injection instances of field injection"
        print_info "Use constructor injection instead"
        ((issues_found++))
    else
        print_success "No field injection found"
    fi

    # 4. Check for missing @Transactional
    print_info "Checking for methods with multiple save() calls..."
    local multi_save=$(grep -A5 "\.save(" src/**/*.java 2>/dev/null | grep "\.save(" | wc -l | tr -d ' ')
    if [ "$multi_save" -gt 5 ]; then
        print_warning "Found multiple save() calls - check if @Transactional is needed"
        ((issues_found++))
    else
        print_success "Transaction management looks OK"
    fi

    # 5. Check for TODO/FIXME
    print_info "Checking for TODO/FIXME comments..."
    local todo_count=$(grep -r "TODO\|FIXME" --include="*.java" src/ 2>/dev/null | wc -l | tr -d ' ')
    if [ "$todo_count" -gt 0 ]; then
        print_warning "Found $todo_count TODO/FIXME comments"
        ((issues_found++))
    else
        print_success "No pending TODO/FIXME"
    fi

    # Summary
    echo ""
    if [ $issues_found -eq 0 ]; then
        print_success "All checks passed! ✨"
    else
        print_warning "Found $issues_found types of issues"
        print_info "Run './code-review.sh fix' for suggestions"
    fi
}

################################################################################
# Command: /analyze - Analyze specific file
################################################################################

cmd_analyze() {
    local file=$1

    if [ -z "$file" ]; then
        print_error "Usage: ./code-review.sh analyze <file>"
        exit 1
    fi

    if [ ! -f "$file" ]; then
        print_error "File not found: $file"
        exit 1
    fi

    print_section "Analyzing: $file"

    local lines=$(wc -l < "$file")
    local methods=$(grep -c "public\|private\|protected" "$file" 2>/dev/null || echo "0")

    echo -e "📄 ${CYAN}File Stats:${NC}"
    echo "   Lines: $lines"
    echo "   Methods: $methods"
    echo ""

    # Check complexity
    if [ "$lines" -gt 300 ]; then
        print_warning "File is too long ($lines lines) - consider splitting"
    else
        print_success "File size is reasonable"
    fi

    # Check for specific issues
    echo -e "\n📊 ${CYAN}Issue Check:${NC}"

    if grep -q "System.out.println" "$file"; then
        print_error "Found System.out.println - use logger"
    fi

    if grep -q "@Autowired" "$file" | grep -q "private"; then
        print_error "Found field injection - use constructor"
    fi

    if grep -q "RuntimeException" "$file"; then
        print_warning "Generic RuntimeException found - use custom exceptions"
    fi

    if grep -q "//.*TODO\|//.*FIXME" "$file"; then
        print_warning "Found TODO/FIXME comments"
    fi
}

################################################################################
# Command: /security - Security check
################################################################################

cmd_security() {
    print_section "Security Scan"

    local issues=0

    # 1. Hardcoded secrets
    print_info "Scanning for hardcoded credentials..."
    if grep -rE "(password|secret|key|token)\s*=\s*['\"][^'\"]+['\"]" \
        --include="*.properties" --include="*.yml" --include="*.java" . 2>/dev/null | grep -v ".git" > /dev/null; then
        print_error "Found potential hardcoded secrets!"
        ((issues++))
    else
        print_success "No hardcoded secrets detected"
    fi

    # 2. SQL Injection risk
    print_info "Checking for SQL injection risks..."
    if grep -r "SELECT.*+\|UPDATE.*+\|DELETE.*+" --include="*.java" src/ 2>/dev/null | grep -v "nativeQuery" > /dev/null; then
        print_warning "Potential SQL injection - use parameterized queries"
        ((issues++))
    else
        print_success "No SQL injection risks found"
    fi

    # 3. Weak random
    print_info "Checking for weak random generators..."
    if grep -r "new Random()" --include="*.java" src/ 2>/dev/null > /dev/null; then
        print_warning "Found Math.random() or new Random() - use SecureRandom for security"
        ((issues++))
    else
        print_success "Random generation looks secure"
    fi

    # 4. Missing input validation
    print_info "Checking for input validation..."
    local controllers=$(find src/ -name "*Controller.java" 2>/dev/null)
    local validated=0
    for controller in $controllers; do
        if grep -q "@Valid" "$controller"; then
            ((validated++))
        fi
    done

    if [ $validated -eq 0 ] && [ -n "$controllers" ]; then
        print_warning "No @Valid annotations found in controllers"
        ((issues++))
    else
        print_success "Input validation present"
    fi

    # Summary
    echo ""
    if [ $issues -eq 0 ]; then
        print_success "Security scan passed! 🔒"
    else
        print_error "Found $issues security issues"
        print_info "Review CODE_REVIEW_REPORT.md section 1 for details"
    fi
}

################################################################################
# Command: /performance - Performance check
################################################################################

cmd_performance() {
    print_section "Performance Analysis"

    # 1. N+1 Query detection
    print_info "Checking for potential N+1 queries..."
    if grep -r "findById\|findAll" --include="*.java" src/ 2>/dev/null | \
       grep -v "JOIN FETCH" | wc -l | grep -qv "^0$"; then
        print_warning "Potential N+1 queries - use JOIN FETCH"
    else
        print_success "No obvious N+1 query issues"
    fi

    # 2. Missing pagination
    print_info "Checking for pagination..."
    if grep -r "findAll()" --include="*.java" src/ 2>/dev/null | wc -l | grep -qv "^0$"; then
        print_warning "Found findAll() without pagination"
        print_info "Use Pageable parameter"
    else
        print_success "Pagination looks good"
    fi

    # 3. Missing indexes (check if migrations exist)
    print_info "Checking for database indexes..."
    if [ -d "src/main/resources/db/migration" ]; then
        if grep -r "CREATE INDEX" src/main/resources/db/migration 2>/dev/null > /dev/null; then
            print_success "Database indexes found"
        else
            print_warning "No indexes found in migrations"
        fi
    else
        print_info "No migration files found"
    fi

    # 4. Large methods
    print_info "Checking for large methods..."
    local large_methods=$(find src/ -name "*.java" -exec awk '/public|private|protected/ {
        start=NR;
        brace=0;
        name=$0
    }
    /{/ {brace++}
    /}/ {
        brace--;
        if(brace==0 && start>0) {
            lines=NR-start;
            if(lines>50) print FILENAME":"lines
            start=0
        }
    }' {} \; 2>/dev/null | wc -l)

    if [ "$large_methods" -gt 0 ]; then
        print_warning "Found $large_methods methods > 50 lines"
        print_info "Consider refactoring large methods"
    else
        print_success "Method sizes look good"
    fi
}

################################################################################
# Command: /fix - Auto-fix suggestions
################################################################################

cmd_fix() {
    local file=$1

    print_section "Auto-Fix Suggestions"

    if [ -z "$file" ]; then
        print_info "Scanning all files for fixable issues..."
        file="src/"
    fi

    # Generate fix suggestions
    mkdir -p "$REPORT_DIR"
    local report="$REPORT_DIR/fix-suggestions-$TIMESTAMP.md"

    cat > "$report" << 'EOF'
# 🔧 Auto-Fix Suggestions

## Issues Found

EOF

    # Find System.out.println
    if grep -r "System.out.println" --include="*.java" "$file" 2>/dev/null > /dev/null; then
        cat >> "$report" << 'EOF'
### 1. Replace System.out.println with Logger

**Files:**
EOF
        grep -rn "System.out.println" --include="*.java" "$file" 2>/dev/null >> "$report"

        cat >> "$report" << 'EOF'

**Fix:**
```java
// Add to class
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class YourClass {
    // Replace System.out.println(msg)
    // With: log.info("{}", msg)
}
```

EOF
        print_info "Added System.out.println fixes to report"
    fi

    # Find field injection
    if grep -r "@Autowired" --include="*.java" "$file" 2>/dev/null | grep "private" > /dev/null; then
        cat >> "$report" << 'EOF'
### 2. Replace Field Injection with Constructor

**Files:**
EOF
        grep -rn "@Autowired" --include="*.java" "$file" 2>/dev/null | grep "private" >> "$report"

        cat >> "$report" << 'EOF'

**Fix:**
```java
// Add Lombok annotation
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class YourService {
    private final YourRepository repository; // Remove @Autowired
}
```

EOF
        print_info "Added field injection fixes to report"
    fi

    print_success "Fix suggestions saved to: $report"
    print_info "Review and apply manually"

    # Open report if possible
    if command -v open &> /dev/null; then
        open "$report"
    elif command -v xdg-open &> /dev/null; then
        xdg-open "$report"
    fi
}

################################################################################
# Command: /report - Generate full report
################################################################################

cmd_report() {
    print_section "Generating Full Report"

    mkdir -p "$REPORT_DIR"
    local report="$REPORT_DIR/code-review-$TIMESTAMP.md"

    print_info "Running all checks..."

    cat > "$report" << EOF
# 🔍 Code Review Report
**Generated**: $(date)
**Version**: $VERSION

## Summary

EOF

    # Run all checks and append to report
    {
        echo "### Security Scan"
        cmd_security | sed 's/\x1b\[[0-9;]*m//g'

        echo -e "\n### Performance Analysis"
        cmd_performance | sed 's/\x1b\[[0-9;]*m//g'

        echo -e "\n### Common Issues"
        cmd_scan | sed 's/\x1b\[[0-9;]*m//g'
    } >> "$report"

    cat >> "$report" << 'EOF'

## Recommendations

1. Fix all critical security issues immediately
2. Add @Transactional where needed
3. Replace System.out.println with logger
4. Use constructor injection
5. Add database indexes

## Next Steps

1. Review this report
2. Run `./code-review.sh fix` for auto-fix suggestions
3. Apply fixes one by one
4. Run `./code-review.sh scan` to verify

EOF

    print_success "Full report saved to: $report"

    # Open report
    if command -v open &> /dev/null; then
        open "$report"
    elif command -v xdg-open &> /dev/null; then
        xdg-open "$report"
    fi
}

################################################################################
# Command: /test - Run tests
################################################################################

cmd_test() {
    print_section "Running Tests"

    if [ -f "pom.xml" ]; then
        print_info "Maven project detected"
        mvn test
    elif [ -f "build.gradle" ]; then
        print_info "Gradle project detected"
        ./gradlew test
    else
        print_error "No build file found"
        exit 1
    fi
}

################################################################################
# Command: /metrics - Code metrics
################################################################################

cmd_metrics() {
    print_section "Code Metrics"

    # Lines of code
    local java_files=$(find src/main/java -name "*.java" 2>/dev/null | wc -l | tr -d ' ')
    local total_lines=$(find src/main/java -name "*.java" -exec cat {} \; 2>/dev/null | wc -l | tr -d ' ')
    local test_files=$(find src/test/java -name "*.java" 2>/dev/null | wc -l | tr -d ' ')

    echo -e "${CYAN}📊 Project Statistics${NC}"
    echo "   Java Files: $java_files"
    echo "   Total Lines: $total_lines"
    echo "   Test Files: $test_files"

    if [ "$test_files" -gt 0 ]; then
        local test_ratio=$(echo "scale=2; $test_files * 100 / $java_files" | bc)
        echo "   Test Ratio: ${test_ratio}%"
    fi

    echo ""

    # Code quality indicators
    echo -e "${CYAN}🎯 Quality Indicators${NC}"

    local issues=0

    if grep -r "System.out" --include="*.java" src/ 2>/dev/null > /dev/null; then
        echo "   ❌ System.out.println found"
        ((issues++))
    else
        echo "   ✅ No System.out.println"
    fi

    if grep -r "@Autowired.*private" --include="*.java" src/ 2>/dev/null > /dev/null; then
        echo "   ❌ Field injection found"
        ((issues++))
    else
        echo "   ✅ No field injection"
    fi

    if grep -r "RuntimeException" --include="*.java" src/ 2>/dev/null > /dev/null; then
        echo "   ⚠️  Generic RuntimeException used"
        ((issues++))
    else
        echo "   ✅ Custom exceptions"
    fi

    echo ""
    echo -e "${CYAN}📈 Quality Score: $(( (3 - issues) * 100 / 3 ))%${NC}"
}

################################################################################
# Command: /help - Show help
################################################################################

cmd_help() {
    print_header
    echo ""
    echo -e "${CYAN}Available Commands:${NC}"
    echo ""
    echo -e "  ${GREEN}/scan${NC}              - Quick scan for common issues"
    echo -e "  ${GREEN}/analyze${NC} <file>    - Analyze specific file"
    echo -e "  ${GREEN}/security${NC}          - Security vulnerability scan"
    echo -e "  ${GREEN}/performance${NC}       - Performance analysis"
    echo -e "  ${GREEN}/fix${NC} [file]        - Generate fix suggestions"
    echo -e "  ${GREEN}/report${NC}            - Generate full code review report"
    echo -e "  ${GREEN}/test${NC}              - Run project tests"
    echo -e "  ${GREEN}/metrics${NC}           - Show code metrics"
    echo -e "  ${GREEN}/help${NC}              - Show this help"
    echo ""
    echo -e "${CYAN}Examples:${NC}"
    echo "  ./code-review.sh /scan"
    echo "  ./code-review.sh /analyze src/main/java/com/example/Service.java"
    echo "  ./code-review.sh /security"
    echo "  ./code-review.sh /fix"
    echo ""
    echo -e "${CYAN}Quick Start:${NC}"
    echo "  1. Run ${GREEN}/scan${NC} to find issues"
    echo "  2. Run ${GREEN}/fix${NC} to get suggestions"
    echo "  3. Run ${GREEN}/test${NC} after fixing"
    echo ""
}

################################################################################
# Main
################################################################################

main() {
    local command=$1
    shift

    case "$command" in
        /scan|scan)
            print_header
            cmd_scan "$@"
            ;;
        /analyze|analyze)
            print_header
            cmd_analyze "$@"
            ;;
        /security|security)
            print_header
            cmd_security "$@"
            ;;
        /performance|performance)
            print_header
            cmd_performance "$@"
            ;;
        /fix|fix)
            print_header
            cmd_fix "$@"
            ;;
        /report|report)
            print_header
            cmd_report "$@"
            ;;
        /test|test)
            print_header
            cmd_test "$@"
            ;;
        /metrics|metrics)
            print_header
            cmd_metrics "$@"
            ;;
        /help|help|"")
            cmd_help
            ;;
        *)
            print_error "Unknown command: $command"
            echo ""
            cmd_help
            exit 1
            ;;
    esac
}

# Run main
main "$@"

