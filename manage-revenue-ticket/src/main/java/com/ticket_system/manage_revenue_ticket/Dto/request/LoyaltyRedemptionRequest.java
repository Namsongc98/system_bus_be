package com.ticket_system.manage_revenue_ticket.Dto.request;

import com.ticket_system.manage_revenue_ticket.entity.LoyaltyReward;
import lombok.Data;

@Data
public class LoyaltyRedemptionRequest {
    private Long customerId;
    private Long rewardId;
    private String rewardName;
    private String description;
    private Integer pointsRequired; // tương ứng pointsSpent / requiredPoints
    private LoyaltyReward.RewardType rewardType;
    private Boolean active;
    private LoyaltyReward.Status status;
}
