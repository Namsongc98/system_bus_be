package com.ticket_system.manage_revenue_ticket.entity;


import com.ticket_system.manage_revenue_ticket.Enum.TransactionType;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "loyalty_points")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LoyaltyPoint extends BaseEntity{

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Quan hệ tới bảng users (customer)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    @JsonIgnore
    private User customer;

    @Column(nullable = false)
    private Integer points;

    @Enumerated(EnumType.STRING)
    private LoyaltyPoint.status status;

    private Integer  alocate;

    @Column( name = "requied_point")
    private Integer  requiedPoint;

    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false)
    private TransactionType transactionType;

    @Column(length = 255)
    private String description;

    public enum status {
        UNUSED,REDEEMED
    }
}
