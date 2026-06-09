package com.ticket_system.booking_ticket.dto.request;

import com.ticket_system.booking_ticket.Enum.LoyaltyRewardStatus;
import com.ticket_system.booking_ticket.Enum.RewardType;
import lombok.Data;

@Data
public class LoyaltyRedemptionRequest {
    private Long customerId;
    private Long rewardId;
    private String rewardName;
    private String description;
    private Integer pointsRequired; // tương ứng pointsSpent / requiredPoints
    private RewardType rewardType;
    private Boolean active;
    private LoyaltyRewardStatus status;
}
