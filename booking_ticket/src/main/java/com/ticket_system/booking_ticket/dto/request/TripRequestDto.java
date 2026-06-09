package com.ticket_system.booking_ticket.dto.request;

import com.ticket_system.booking_ticket.Enum.TripStatus;
import com.ticket_system.booking_ticket.entity.Buses;
import com.ticket_system.booking_ticket.entity.Route;
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

    private Long id;
    private Route route;
    private Buses bus;

}
