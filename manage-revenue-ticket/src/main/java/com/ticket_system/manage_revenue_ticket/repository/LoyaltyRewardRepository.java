package com.ticket_system.manage_revenue_ticket.repository;

import com.ticket_system.manage_revenue_ticket.entity.LoyaltyReward;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LoyaltyRewardRepository extends JpaRepository<LoyaltyReward, Long> {
    List<LoyaltyReward> findByActiveTrue();
    Optional<LoyaltyReward> findById(Long idReward);
}
