package com.ticket_system.manage_revenue_ticket.repository;

import com.ticket_system.manage_revenue_ticket.entity.Salary;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SalaryRepository extends JpaRepository<Salary, Long> {
    List<Salary> findByPeriodMonthAndPeriodYearAndUserId(byte month, short year, Long userId);

    @Query("""
            SELECT s FROM Salary s
            WHERE s.periodMonth = :month
            AND s.periodYear = :year
            ORDER BY s.updatedAt DESC
            """)
    Page<Salary> selectSalaryMonth(@Param("month") byte month,
                                   @Param("year") short year,
                                   Pageable pageable);
}
