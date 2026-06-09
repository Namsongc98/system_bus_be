package com.ticket_system.booking_ticket.entity;

import com.ticket_system.common.Enum.UserRole;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "base_salaries")
@Getter
@Setter
public class BaseSalary extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Khóa ngoại tới bảng users
     * Nếu user_id null -> áp dụng cho toàn role
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    /**
     * Role áp dụng (nếu không gán user cụ thể)
     * Ví dụ: DRIVER, MANAGER, STAFF
     */
    @Column(name = "role", length = 20)
    @Enumerated(EnumType.STRING)
    private UserRole role;

    /**
     * Lương cơ bản
     */
    @Column(name = "base_salary", precision = 12, scale = 2, nullable = false)
    private BigDecimal baseSalary;
    /**
     * Trợ cấp
     */
    @Column(name = "allowance", precision = 12, scale = 2, nullable = false)
    private BigDecimal allowance = BigDecimal.ZERO;

    /**
     * lợi nhuận mỗi chuyến đi
     */
    @Column(name = "commission", precision = 12, scale = 2, nullable = false)
    private BigDecimal commission = BigDecimal.ZERO;

    /**
     * tiền thưởng
     */
    @Column(name = "bonus", precision = 12, scale = 2, nullable = false)
    private BigDecimal bonus = BigDecimal.ZERO;

    /**
     * Ngày bắt đầu hiệu lực
     */
    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    /**
     * Ngày kết thúc hiệu lực (có thể null)
     */
    @Column(name = "effective_to")
    private LocalDate effectiveTo;

}
