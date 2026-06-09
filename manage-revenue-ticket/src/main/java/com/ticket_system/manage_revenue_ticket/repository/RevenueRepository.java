package com.ticket_system.manage_revenue_ticket.repository;

import com.ticket_system.manage_revenue_ticket.entity.Revenue;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public interface RevenueRepository extends JpaRepository<Revenue,Long> {
    // Doanh thu của 1 bus theo ngày
    @Query("""
        SELECT SUM(r.totalAmount)
        FROM Revenue r
        WHERE r.trip.bus.id = :busId
          AND r.reportDate = :date
    """)
    BigDecimal getRevenueByBusAndDate(@Param("busId") Long busId, @Param("date") LocalDate date);

    // Doanh thu của 1 bus trong khoảng ngày
    @Query("""
        SELECT SUM(r.totalAmount)
        FROM Revenue r
        WHERE r.trip.bus.id = :busId
          AND r.reportDate BETWEEN :startDate AND :endDate
    """)
    BigDecimal getRevenueByBusAndRange(@Param("busId") Long busId,
                                       @Param("startDate") LocalDate startDate,
                                       @Param("endDate") LocalDate endDate);

    // Doanh thu tổng hợp tất cả bus trong ngày
    @Query("""
        SELECT r.trip.bus.id, r.trip.bus.plateNumber, SUM(r.totalAmount)
        FROM Revenue r
        WHERE r.reportDate = :date
        GROUP BY r.trip.bus.id, r.trip.bus.plateNumber
    """)
    List<Object[]> getTotalRevenueByAllBusesInDate(@Param("date") LocalDate date);
}
