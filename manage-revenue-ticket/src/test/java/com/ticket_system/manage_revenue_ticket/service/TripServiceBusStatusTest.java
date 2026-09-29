package com.ticket_system.manage_revenue_ticket.service;

import com.ticket_system.common.Enum.UserRole;
import com.ticket_system.manage_revenue_ticket.Dto.request.TripRequestDto;
import com.ticket_system.manage_revenue_ticket.Enum.BusStatus;
import com.ticket_system.manage_revenue_ticket.Enum.DriverStatus;
import com.ticket_system.manage_revenue_ticket.entity.Buses;
import com.ticket_system.manage_revenue_ticket.entity.Route;
import com.ticket_system.manage_revenue_ticket.entity.Trip;
import com.ticket_system.manage_revenue_ticket.entity.User;
import com.ticket_system.manage_revenue_ticket.repository.BusesRepository;
import com.ticket_system.manage_revenue_ticket.repository.RouteRepository;
import com.ticket_system.manage_revenue_ticket.repository.TripRepository;
import com.ticket_system.manage_revenue_ticket.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Bus status meaning after the V3 rename (task 1.1, D1 = A): only AVAILABLE buses can take a trip. */
class TripServiceBusStatusTest {

    private final TripRepository tripRepository = mock(TripRepository.class);
    private final RouteRepository routeRepository = mock(RouteRepository.class);
    private final BusesRepository busRepository = mock(BusesRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final TripService tripService = new TripService(
            tripRepository, routeRepository, busRepository, userRepository, mock(JdbcTemplate.class));

    private final Buses bus = Buses.builder().id(2L).plateNumber("51A-1").capacity(40).build();

    @BeforeEach
    void seed() {
        User driver = new User();
        driver.setId(3L);
        driver.setRole(UserRole.DRIVER);
        driver.setDriverStatus(DriverStatus.PENDING);
        when(routeRepository.findById(1L)).thenReturn(Optional.of(new Route()));
        when(busRepository.findById(2L)).thenReturn(Optional.of(bus));
        when(userRepository.findById(3L)).thenReturn(Optional.of(driver));
        when(tripRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void newBusDefaultsToAvailable() {
        assertThat(bus.getStatus()).isEqualTo(BusStatus.AVAILABLE);
    }

    @Test
    void creatingATripOnAnAvailableBusMarksItInUse() {
        Trip trip = tripService.createTrip(request());

        assertThat(trip.getBus().getStatus()).isEqualTo(BusStatus.IN_USE);
        verify(busRepository).save(bus);
    }

    @Test
    void busInMaintenanceCannotTakeATrip() {
        bus.setStatus(BusStatus.MAINTENANCE);

        assertThatThrownBy(() -> tripService.createTrip(request()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("không thể chạy");
        verify(tripRepository, never()).save(any());
    }

    @Test
    void busAlreadyInUseCannotTakeAnotherTrip() {
        bus.setStatus(BusStatus.IN_USE);

        assertThatThrownBy(() -> tripService.createTrip(request()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("đang chạy");
        verify(tripRepository, never()).save(any());
    }

    @Test
    void reassigningATripToABusInMaintenanceIsRejected() {
        bus.setStatus(BusStatus.MAINTENANCE);
        when(tripRepository.findById(9L)).thenReturn(Optional.of(new Trip()));
        TripRequestDto update = new TripRequestDto();
        update.setBusId(2L);

        assertThatThrownBy(() -> tripService.updateTrip(9L, update))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static TripRequestDto request() {
        TripRequestDto dto = new TripRequestDto();
        dto.setRouteId(1L);
        dto.setBusId(2L);
        dto.setDriverId(3L);
        dto.setDepartureTime(LocalDateTime.now().plusDays(1));
        return dto;
    }
}
