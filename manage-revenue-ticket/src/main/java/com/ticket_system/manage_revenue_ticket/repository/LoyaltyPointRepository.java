package com.ticket_system.manage_revenue_ticket.repository;

import com.ticket_system.manage_revenue_ticket.entity.LoyaltyPoint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface LoyaltyPointRepository extends JpaRepository<LoyaltyPoint, Long> {

    Optional<LoyaltyPoint> findFirstByCustomerId(Long customerId);

    @Query("SELECT lp FROM LoyaltyPoint lp WHERE lp.customer.id = :customerId ORDER BY lp.updatedAt DESC")
    List<LoyaltyPoint> findAllByCustomerIdOrderByUpdatedAtDesc(@Param("customerId") Long customerId);
}
