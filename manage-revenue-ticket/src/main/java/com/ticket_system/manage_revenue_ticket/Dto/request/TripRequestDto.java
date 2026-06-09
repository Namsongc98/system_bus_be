package com.ticket_system.manage_revenue_ticket.Dto.request;

import com.ticket_system.manage_revenue_ticket.Enum.TripStatus;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Setter
@Getter
public class TripRequestDto {
    private Long routeId;
    private Long busId;
    private Long driverId;
    private LocalDateTime departureTime;
    private LocalDateTime arrivalTime;
    private BigDecimal revenue = BigDecimal.ZERO;
    private TripStatus status = TripStatus.SCHEDULED; // Mặc định là SCHEDULED
}
