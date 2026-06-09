package com.ticket_system.manage_revenue_ticket.Dto.request;

import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class BusRequest {
    private String plateNumber;
    private Integer capacity;
    private String status;
}
