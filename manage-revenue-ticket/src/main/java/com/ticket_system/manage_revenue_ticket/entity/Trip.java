package com.ticket_system.manage_revenue_ticket.entity;

import com.ticket_system.manage_revenue_ticket.Enum.TripStatus;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

import java.time.LocalDateTime;

@Entity
@Table(name = "trips")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Trip extends BaseEntity{

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, updatable = false)
    private Long id;

    // Liên kết tới Route (Nhiều chuyến có thể cùng 1 tuyến)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "route_id", nullable = false, foreignKey = @ForeignKey(name = "trips_fk_route"))
    private Route route;

    // Liên kết tới Bus (Nhiều chuyến có thể dùng cùng 1 xe)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bus_id", nullable = false, foreignKey = @ForeignKey(name = "trips_fk_bus"))
    private Buses bus;

    // Liên kết tới User (driver)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "driver_id", nullable = false, foreignKey = @ForeignKey(name = "trips_fk_driver"))
    private User driver;

    @Column(name = "departure_time", nullable = false)
    private LocalDateTime departureTime;

    @Column(name = "arrival_time")
    private LocalDateTime arrivalTime;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, columnDefinition = "ENUM('SCHEDULED','ONGOING','COMPLETED','CANCELLED') DEFAULT 'SCHEDULED'")
    private TripStatus status = TripStatus.SCHEDULED;

    @Column(name = "revenue", precision = 15, scale = 2, columnDefinition = "DECIMAL(15,2) DEFAULT 0.00")
    private BigDecimal revenue = BigDecimal.ZERO;

}
