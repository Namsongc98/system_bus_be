package com.ticket_system.manage_revenue_ticket.Dto.response;

import com.ticket_system.common.Enum.UserRole;
import com.ticket_system.manage_revenue_ticket.Enum.BusStatus;
import com.ticket_system.manage_revenue_ticket.Enum.TripStatus;
import com.ticket_system.manage_revenue_ticket.entity.Buses;
import com.ticket_system.manage_revenue_ticket.entity.Profile;
import com.ticket_system.manage_revenue_ticket.entity.Route;
import com.ticket_system.manage_revenue_ticket.entity.Trip;
import com.ticket_system.manage_revenue_ticket.entity.User;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * A trip as returned by /api/trip. Never expose the Trip entity (B9).
 * ticketCount counts every ticket, cancelled ones included (a trip with any ticket cannot be deleted, B35 e);
 * bookedSeats counts tickets that are not CANCELLED; revenue is the sum of SUCCESS ticket prices
 * (spec review 1.3 D6), both computed by TicketRepository.findTripTicketStats.
 */
public record TripResponse(
        Long id,
        TripStatus status,
        LocalDateTime departureTime,
        LocalDateTime arrivalTime,
        RouteSummary route,
        BusSummary bus,
        DriverSummary driver,
        long ticketCount,
        long bookedSeats,
        BigDecimal revenue,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public record RouteSummary(Long id, String routeName, String startPoint, String endPoint, BigDecimal distanceKm) {
        static RouteSummary from(Route route) {
            return new RouteSummary(route.getId(), route.getRouteName(), route.getStartPoint(),
                    route.getEndPoint(), route.getDistanceKm());
        }
    }

    public record BusSummary(Long id, String plateNumber, Integer capacity, BusStatus status) {
        static BusSummary from(Buses bus) {
            return new BusSummary(bus.getId(), bus.getPlateNumber(), bus.getCapacity(), bus.getStatus());
        }
    }

    /** fullName comes from the driver's profile and is null when there is none. */
    public record DriverSummary(Long id, String email, String fullName, UserRole role) {
        static DriverSummary from(User driver, Profile profile) {
            return new DriverSummary(driver.getId(), driver.getEmail(),
                    profile == null ? null : profile.getFullName(), driver.getRole());
        }
    }

    public static TripResponse from(Trip trip, Profile driverProfile, long ticketCount, long bookedSeats,
                                    BigDecimal revenue) {
        return new TripResponse(
                trip.getId(),
                trip.getStatus(),
                trip.getDepartureTime(),
                trip.getArrivalTime(),
                RouteSummary.from(trip.getRoute()),
                BusSummary.from(trip.getBus()),
                DriverSummary.from(trip.getDriver(), driverProfile),
                ticketCount,
                bookedSeats,
                revenue == null ? BigDecimal.ZERO : revenue,
                trip.getCreatedAt(),
                trip.getUpdatedAt()
        );
    }
}
