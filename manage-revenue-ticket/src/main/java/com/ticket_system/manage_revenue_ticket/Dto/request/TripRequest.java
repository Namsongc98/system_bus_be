package com.ticket_system.manage_revenue_ticket.Dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Body of POST /api/trip and PUT /api/trip/{id}. Status and revenue are never taken from the
 * client: a new trip is SCHEDULED, status changes go through PATCH /status, revenue is computed
 * from tickets (spec review 1.3 D6). Time rules (arrival after departure, not in the past) are
 * checked in TripService.
 */
@Setter
@Getter
public class TripRequest {

    @NotNull(message = "Tuyến không được để trống")
    private Long routeId;

    @NotNull(message = "Xe không được để trống")
    private Long busId;

    @NotNull(message = "Tài xế không được để trống")
    private Long driverId;

    @NotNull(message = "Giờ khởi hành không được để trống")
    private LocalDateTime departureTime;

    @NotNull(message = "Giờ đến không được để trống")
    private LocalDateTime arrivalTime;
}
