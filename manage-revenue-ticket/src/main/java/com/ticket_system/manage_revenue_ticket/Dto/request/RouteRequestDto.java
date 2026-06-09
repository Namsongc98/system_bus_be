package com.ticket_system.manage_revenue_ticket.Dto.request;

import com.ticket_system.manage_revenue_ticket.Enum.RouteStatus;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class RouteRequestDto {
    private String routeName;
    private String startPoint;
    private String endPoint;
    private BigDecimal distanceKm;
    private RouteStatus status = RouteStatus.ACTIVE; // Mặc định ACTIVE
}
