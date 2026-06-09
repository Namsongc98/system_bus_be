package com.ticket_system.manage_revenue_ticket.entity;

import com.ticket_system.manage_revenue_ticket.Enum.RouteStatus;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "routes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Route extends BaseEntity{

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, updatable = false)
    private Long id;

    @Column(name = "route_name", length = 100, nullable = false)
    private String routeName;

    @Column(name = "start_point", length = 100)
    private String startPoint;

    @Column(name = "end_point", length = 100)
    private String endPoint;

    @Column(name = "distance_km", precision = 6, scale = 2)
    private BigDecimal distanceKm;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, columnDefinition = "ENUM('ACTIVE','INACTIVE') DEFAULT 'ACTIVE'")
    private RouteStatus status = RouteStatus.ACTIVE;

}
