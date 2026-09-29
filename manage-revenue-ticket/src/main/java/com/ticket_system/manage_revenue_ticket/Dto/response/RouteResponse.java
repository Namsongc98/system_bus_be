package com.ticket_system.manage_revenue_ticket.Dto.response;

import com.ticket_system.manage_revenue_ticket.Enum.RouteStatus;
import com.ticket_system.manage_revenue_ticket.entity.Route;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** A route as returned by /api/route. Never expose the Route entity itself (B9). */
public record RouteResponse(
        Long id,
        String routeName,
        String startPoint,
        String endPoint,
        BigDecimal distanceKm,
        RouteStatus status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static RouteResponse from(Route route) {
        return new RouteResponse(
                route.getId(),
                route.getRouteName(),
                route.getStartPoint(),
                route.getEndPoint(),
                route.getDistanceKm(),
                route.getStatus(),
                route.getCreatedAt(),
                route.getUpdatedAt()
        );
    }
}
