package com.ticket_system.manage_revenue_ticket.repository;


import com.ticket_system.manage_revenue_ticket.entity.BaseLoyaltyPoints;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface BaseLoyaltyPointsRepository extends JpaRepository<BaseLoyaltyPoints, Long> {
    @Query("""
        SELECT b FROM BaseLoyaltyPoints b
        WHERE b.roleName = :role
        AND b.status = 'ACTIVE'
        AND (b.endDate IS NULL OR b.endDate >= CURRENT_DATE)
        ORDER BY b.startDate DESC
        LIMIT 1
    """)
    List<BaseLoyaltyPoints> findActiveByRole(@Param("role") String role, Pageable pageable);

    default List<BaseLoyaltyPoints> findLatestActiveByRole(String role) {
        Pageable limitOne = PageRequest.of(0, 1);
        return findActiveByRole(role, limitOne);
    }
}
