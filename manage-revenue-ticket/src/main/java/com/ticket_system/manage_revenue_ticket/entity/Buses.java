package com.ticket_system.manage_revenue_ticket.entity;
import com.ticket_system.manage_revenue_ticket.Enum.BusStatus;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(
        name = "buses",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = "plate_number", name = "plate_number")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Buses extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, updatable = false)
    private Long id;

    @Column(name = "plate_number", length = 20, nullable = false, unique = true)
    private String plateNumber;

    @Column(name = "capacity", nullable = false)
    private Integer capacity;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, columnDefinition = "ENUM('ACTIVE','INACTIVE','PENDING') DEFAULT 'ACTIVE'")
    private BusStatus status = BusStatus.PENDING;

}
