package com.ticket_system.booking_ticket.dto.request;

import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class BusRequest {
    private String plateNumber;
    private Integer capacity;
    private String status;
}
