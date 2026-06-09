package com.ticket_system.manage_revenue_ticket.repository;

import com.ticket_system.common.Enum.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;
import com.ticket_system.manage_revenue_ticket.entity.BaseSalary;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface BaseSalaryRepository extends JpaRepository<BaseSalary, Long> {
    Optional<BaseSalary> findTopByUserIdOrderByUpdatedAtDesc(Long userId);

    @Query("""
        SELECT bs FROM BaseSalary bs
        WHERE bs.user.id = :userId
          AND bs.effectiveFrom <= :salaryDate
        ORDER BY bs.effectiveFrom DESC
    """)
    List<BaseSalary> findLatestByUser(@Param("userId") Long userId,
                                      @Param("salaryDate") LocalDate salaryDate);

    @Query("""
        SELECT bs FROM BaseSalary bs
        WHERE bs.role = :role
          AND bs.effectiveFrom <= :salaryDate
        ORDER BY bs.effectiveFrom DESC
    """)
    List<BaseSalary> findLatestByRole(@Param("role") UserRole role,
                                      @Param("salaryDate") LocalDate salaryDate);

    @Query("""
            SELECT bs FROM BaseSalary bs
            WHERE bs.user.id = :userId
            ORDER BY bs.updatedAt DESC
            """)
    List<BaseSalary> findLatestByUserId(@Param("userId")  Long userId);
}
