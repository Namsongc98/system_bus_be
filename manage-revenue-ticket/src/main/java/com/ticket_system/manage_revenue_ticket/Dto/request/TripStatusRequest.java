package com.ticket_system.manage_revenue_ticket.Dto.request;

import com.ticket_system.manage_revenue_ticket.Enum.TripStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/** Body of PATCH /api/trip/{id}/status. Allowed moves are checked in TripService. */
@Setter
@Getter
public class TripStatusRequest {

    @NotNull(message = "Trạng thái không được để trống")
    private TripStatus status;
}
