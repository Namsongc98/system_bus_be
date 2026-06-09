package com.ticket_system.manage_revenue_ticket.service;

import com.ticket_system.manage_revenue_ticket.entity.LoyaltyReward;
import com.ticket_system.manage_revenue_ticket.repository.LoyaltyRewardRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class LoyaltyRewardService {

    @Autowired
    private LoyaltyRewardRepository rewardRepo;



    public LoyaltyReward create(LoyaltyReward payload) {
        // ensure status/active consistency

        LoyaltyReward loyaltyReward = new LoyaltyReward();
        loyaltyReward.setDescription(payload.getDescription());
        loyaltyReward.setPointsRequired(payload.getPointsRequired());
        loyaltyReward.setRewardName(payload.getRewardName());
        loyaltyReward.setRewardType(payload.getRewardType());

        if (payload.getStatus() == null) payload.setStatus(LoyaltyReward.Status.ACTIVE);
        if (payload.getActive() == null) payload.setActive(Boolean.TRUE);
        return rewardRepo.save(loyaltyReward);
    }

    public LoyaltyReward update(Long id, LoyaltyReward payload) {
        LoyaltyReward existing = rewardRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Reward not found: " + id));
        existing.setRewardName(payload.getRewardName());
        existing.setDescription(payload.getDescription());
        existing.setPointsRequired(payload.getPointsRequired());
        existing.setRewardType(payload.getRewardType());
        existing.setActive(payload.getActive());
        existing.setStatus(payload.getStatus());
        existing.setUpdatedAt(LocalDateTime.now());
        return rewardRepo.save(existing);
    }
}
