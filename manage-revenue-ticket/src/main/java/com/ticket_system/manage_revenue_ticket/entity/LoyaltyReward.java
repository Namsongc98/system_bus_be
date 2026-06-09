package com.ticket_system.manage_revenue_ticket.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.Id;

@Entity
@Table(name = "loyalty_rewards")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoyaltyReward extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String rewardName; // Ví dụ: "Vé miễn phí", "Giảm 50%", "Voucher 100k"

    @Enumerated(EnumType.STRING)
    private RewardType rewardType; // TICKET_DISCOUNT, VOUCHER, FREE_TICKET,...

    private String description;
    private Boolean active;

    private Integer pointsRequired;

    @Enumerated(EnumType.STRING)
    private Status status;

    public enum RewardType {
        TICKET_DISCOUNT, VOUCHER, FREE_TICKET
    }

    public enum Status {
        ACTIVE, INACTIVE;

    }
}
