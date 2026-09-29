package com.ticket_system.manage_revenue_ticket.Dto.response;

import com.ticket_system.manage_revenue_ticket.Enum.BusStatus;
import com.ticket_system.manage_revenue_ticket.entity.Buses;

import java.time.LocalDateTime;

/** A bus as returned by /api/bus. Never expose the Buses entity itself (B9). */
public record BusResponse(
        Long id,
        String plateNumber,
        Integer capacity,
        BusStatus status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static BusResponse from(Buses bus) {
        return new BusResponse(
                bus.getId(),
                bus.getPlateNumber(),
                bus.getCapacity(),
                bus.getStatus(),
                bus.getCreatedAt(),
                bus.getUpdatedAt()
        );
    }
}
