package com.ticket_system.manage_revenue_ticket.projection;

public interface DashboardTopRouteProjection {
    Long getRouteId();

    String getRouteName();

    Long getTicketsSold();
}
