package com.ticket_system.manage_revenue_ticket.service;

import com.ticket_system.common.exception.ConflictException;
import com.ticket_system.common.exception.ResourceNotFoundException;
import com.ticket_system.manage_revenue_ticket.Dto.request.RouteRequestDto;
import com.ticket_system.manage_revenue_ticket.Dto.response.RouteResponse;
import com.ticket_system.manage_revenue_ticket.Enum.RouteStatus;
import com.ticket_system.manage_revenue_ticket.entity.Route;
import com.ticket_system.manage_revenue_ticket.repository.RouteRepository;
import com.ticket_system.manage_revenue_ticket.repository.TripRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Task 1.1, T3: start != end, not found and delete rules (D2 = A) for routes. */
class RouteServiceTest {

    private final RouteRepository routeRepository = mock(RouteRepository.class);
    private final TripRepository tripRepository = mock(TripRepository.class);
    private final RouteService routeService = new RouteService(routeRepository, tripRepository);

    @Test
    void createTrimsFieldsAndSaves() {
        when(routeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        RouteResponse created = routeService.createRoute(request(" Hà Nội ", "Hải Phòng "));

        assertThat(created.startPoint()).isEqualTo("Hà Nội");
        assertThat(created.endPoint()).isEqualTo("Hải Phòng");
        assertThat(created.status()).isEqualTo(RouteStatus.ACTIVE);
    }

    @Test
    void sameStartAndEndIgnoringCaseAndSpacesIsRejected() {
        assertThatThrownBy(() -> routeService.createRoute(request("Hà Nội", " hà nội ")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Điểm đi và điểm đến phải khác nhau");
        verify(routeRepository, never()).save(any());
    }

    @Test
    void updateUnknownRouteIsNotFoundWithRouteMessage() {
        stubRoute(9L, Optional.empty());

        assertThatThrownBy(() -> routeService.updateRoute(9L, request("A", "B")))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("tuyến");
    }

    @Test
    void updateReturnsUpdatedRoute() {
        Route route = new Route();
        route.setId(9L);
        stubRoute(9L, Optional.of(route));
        when(routeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        RouteResponse updated = routeService.updateRoute(9L, request("A", "B"));

        assertThat(updated.id()).isEqualTo(9L);
        assertThat(updated.endPoint()).isEqualTo("B");
    }

    @Test
    void listSortsByIdDescAndUsesFindAllWithoutStatus() {
        when(routeRepository.findAll(any(Pageable.class)))
                .thenAnswer(inv -> new PageImpl<>(List.of(), inv.getArgument(0), 0));

        routeService.getRoutes(null, 0, 10);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(routeRepository).findAll(pageable.capture());
        assertThat(pageable.getValue().getSort()).isEqualTo(Sort.by(Sort.Direction.DESC, "id"));
        verify(routeRepository, never()).findByStatus(any(), any());
    }

    @Test
    void listFiltersByStatusAndRejectsOutOfRangeSize() {
        when(routeRepository.findByStatus(eq(RouteStatus.ACTIVE), any()))
                .thenAnswer(inv -> new PageImpl<>(List.of(), inv.getArgument(1), 0));

        routeService.getRoutes(RouteStatus.ACTIVE, 0, 10);

        verify(routeRepository).findByStatus(eq(RouteStatus.ACTIVE), any());
        assertThatThrownBy(() -> routeService.getRoutes(null, 0, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> routeService.getRoutes(null, 0, 101)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void getUnknownRouteIsNotFound() {
        stubRoute(9L, Optional.empty());

        assertThatThrownBy(() -> routeService.getRoute(9L)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void updateWithSameStartAndEndIsRejected() {
        stubRoute(9L, Optional.of(new Route()));

        assertThatThrownBy(() -> routeService.updateRoute(9L, request("Đà Nẵng", "ĐÀ NẴNG")))
                .isInstanceOf(IllegalArgumentException.class);
        verify(routeRepository, never()).save(any());
    }

    @Test
    void deleteUnknownRouteIsNotFound() {
        stubRoute(9L, Optional.empty());

        assertThatThrownBy(() -> routeService.deleteRoute(9L)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void deleteRacingATripInsertIsConflict() {
        stubRoute(1L, Optional.of(new Route()));
        doThrow(new DataIntegrityViolationException("trips_fk_route")).when(routeRepository).flush();

        assertThatThrownBy(() -> routeService.deleteRoute(1L))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("INACTIVE");
    }

    @Test
    void deleteRouteWithUnfinishedTripIsConflict() {
        stubRoute(1L, Optional.of(new Route()));
        when(tripRepository.existsByRouteIdAndStatusIn(1L, BusService.UNFINISHED_TRIP_STATUSES)).thenReturn(true);

        assertThatThrownBy(() -> routeService.deleteRoute(1L))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Tuyến đang có chuyến chưa kết thúc");
        verify(routeRepository, never()).delete(any());
    }

    @Test
    void deleteRouteWithTripHistoryIsConflict() {
        stubRoute(1L, Optional.of(new Route()));
        when(tripRepository.existsByRouteId(1L)).thenReturn(true);

        assertThatThrownBy(() -> routeService.deleteRoute(1L))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("INACTIVE");
        verify(routeRepository, never()).delete(any());
    }

    @Test
    void deleteRouteWithoutTripsDeletes() {
        Route route = new Route();
        stubRoute(1L, Optional.of(route));

        routeService.deleteRoute(1L);

        verify(routeRepository).delete(route);
    }

    @Test
    void deactivatingARouteWithUnfinishedTripsIsConflict() {
        // Spec 1.3 D8 = A.
        Route route = new Route();
        route.setId(9L);
        route.setRouteName("HN - HP");
        route.setStatus(RouteStatus.ACTIVE);
        stubRoute(9L, Optional.of(route));
        when(tripRepository.existsByRouteIdAndStatusIn(9L, BusService.UNFINISHED_TRIP_STATUSES)).thenReturn(true);
        RouteRequestDto deactivate = request("A", "B");
        deactivate.setStatus(RouteStatus.INACTIVE);

        assertThatThrownBy(() -> routeService.updateRoute(9L, deactivate))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("chưa kết thúc");
        verify(routeRepository, never()).save(any());
    }

    @Test
    void deactivatingARouteWithoutUnfinishedTripsWorks() {
        Route route = new Route();
        route.setId(9L);
        route.setStatus(RouteStatus.ACTIVE);
        stubRoute(9L, Optional.of(route));
        when(routeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        RouteRequestDto deactivate = request("A", "B");
        deactivate.setStatus(RouteStatus.INACTIVE);

        assertThat(routeService.updateRoute(9L, deactivate).status()).isEqualTo(RouteStatus.INACTIVE);
    }

    private static RouteRequestDto request(String start, String end) {
        RouteRequestDto dto = new RouteRequestDto();
        dto.setRouteName("Tuyến test");
        dto.setStartPoint(start);
        dto.setEndPoint(end);
        dto.setDistanceKm(new BigDecimal("120.00"));
        dto.setStatus(RouteStatus.ACTIVE);
        return dto;
    }

    // update() takes a row lock (findByIdForUpdate); other paths read with findById.
    private void stubRoute(Long id, Optional<Route> result) {
        when(routeRepository.findById(id)).thenReturn(result);
        when(routeRepository.findByIdForUpdate(id)).thenReturn(result);
    }
}
