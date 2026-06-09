package com.ticket_system.booking_ticket.entity;

import com.ticket_system.booking_ticket.Enum.BaseLoyaltyPointStatus;
import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "base_loyalty_points")
@Data
public class BaseLoyaltyPoints extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String roleName;

    private int ticketsPerPoint;

    private BigDecimal pointValue;

    private Integer maxPointsPerMonth;

    private LocalDate startDate;

    private LocalDate endDate;

    @Enumerated(EnumType.STRING)
    private BaseLoyaltyPointStatus status;
}
