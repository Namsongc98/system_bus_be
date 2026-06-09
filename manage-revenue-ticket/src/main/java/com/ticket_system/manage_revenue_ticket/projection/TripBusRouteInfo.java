package com.ticket_system.manage_revenue_ticket.projection;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public interface TripBusRouteInfo {
    Long getBusId();
    String getPlateNumber();
    Integer getCapacity();
    String getBusStatus();
    BigDecimal getRevenue();
    LocalDateTime getDepartureTime();
    LocalDateTime getArrivalTime();
    String getNameTrip();
    String getStartPoint();
    String geTenPoint();
    Double getKm();
}
