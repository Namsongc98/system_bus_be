package com.ticket_system.manage_revenue_ticket.service;

import com.ticket_system.common.Dto.response.PageResponse;
import com.ticket_system.common.exception.ConflictException;
import com.ticket_system.common.exception.ResourceNotFoundException;
import com.ticket_system.manage_revenue_ticket.Dto.request.BusRequest;
import com.ticket_system.manage_revenue_ticket.Dto.response.BusResponse;
import com.ticket_system.manage_revenue_ticket.Enum.BusStatus;
import com.ticket_system.manage_revenue_ticket.entity.Buses;
import com.ticket_system.manage_revenue_ticket.repository.BusesRepository;
import com.ticket_system.manage_revenue_ticket.repository.TripRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Task 1.1, T3: duplicate plate, IN_USE rules (D1 = A) and delete rules (D2 = A). */
class BusServiceTest {

    private final BusesRepository busRepository = mock(BusesRepository.class);
    private final TripRepository tripRepository = mock(TripRepository.class);
    private final BusService busService = new BusService(busRepository, tripRepository);

    @Test
    void listSortsByIdDescAndFiltersByStatus() {
        when(busRepository.findByStatus(eq(BusStatus.AVAILABLE), any()))
                .thenAnswer(inv -> new PageImpl<>(List.of(bus(1L, "51A-1", BusStatus.AVAILABLE)), inv.getArgument(1), 1));

        PageResponse<BusResponse> page = busService.getBuses(BusStatus.AVAILABLE, 0, 12);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(busRepository).findByStatus(eq(BusStatus.AVAILABLE), pageable.capture());
        assertThat(pageable.getValue().getSort()).isEqualTo(Sort.by(Sort.Direction.DESC, "id"));
        assertThat(page.content()).extracting(BusResponse::plateNumber).containsExactly("51A-1");
        verify(busRepository, never()).findAll(any(Pageable.class));
    }

    @Test
    void listRejectsOutOfRangeSize() {
        assertThatThrownBy(() -> busService.getBuses(null, 0, 101)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> busService.getBuses(null, 0, 0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void createTrimsPlateAndSaves() {
        when(busRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        BusResponse created = busService.create(request("  51A-00001 ", BusStatus.AVAILABLE));

        assertThat(created.plateNumber()).isEqualTo("51A-00001");
        verify(busRepository).existsByPlateNumber("51A-00001");
    }

    @Test
    void createWithDuplicatePlateIsConflict() {
        when(busRepository.existsByPlateNumber("51A-00001")).thenReturn(true);

        assertThatThrownBy(() -> busService.create(request("51A-00001", BusStatus.AVAILABLE)))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("51A-00001");
        verify(busRepository, never()).saveAndFlush(any());
    }

    @Test
    void concurrentDuplicateCaughtByUniqueConstraintIsConflict() {
        when(busRepository.saveAndFlush(any())).thenThrow(plateViolation());

        assertThatThrownBy(() -> busService.create(request("51A-00001", BusStatus.AVAILABLE)))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void createWithInUseIsRejected() {
        assertThatThrownBy(() -> busService.create(request("51A-00001", BusStatus.IN_USE)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void updateToPlateOfAnotherBusIsConflict() {
        when(busRepository.findById(1L)).thenReturn(Optional.of(bus(1L, "51A-1", BusStatus.AVAILABLE)));
        when(busRepository.existsByPlateNumberAndIdNot("51A-2", 1L)).thenReturn(true);

        assertThatThrownBy(() -> busService.update(1L, request("51A-2", BusStatus.AVAILABLE)))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void updateUnknownBusIsNotFound() {
        when(busRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> busService.update(1L, request("51A-1", BusStatus.AVAILABLE)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void updateCannotSetInUseManually() {
        when(busRepository.findById(1L)).thenReturn(Optional.of(bus(1L, "51A-1", BusStatus.AVAILABLE)));

        assertThatThrownBy(() -> busService.update(1L, request("51A-1", BusStatus.IN_USE)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void busRunningATripCannotChangeStatusButCanChangeOtherFields() {
        Buses running = bus(1L, "51A-1", BusStatus.IN_USE);
        when(busRepository.findById(1L)).thenReturn(Optional.of(running));
        when(tripRepository.existsByBusIdAndStatusIn(1L, BusService.UNFINISHED_TRIP_STATUSES)).thenReturn(true);
        when(busRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        assertThatThrownBy(() -> busService.update(1L, request("51A-1", BusStatus.MAINTENANCE)))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("đang chạy chuyến");

        BusResponse updated = busService.update(1L, request("51A-9", BusStatus.IN_USE));
        assertThat(updated.plateNumber()).isEqualTo("51A-9");
        assertThat(updated.status()).isEqualTo(BusStatus.IN_USE);
    }

    @Test
    void staleInUseBusWithoutUnfinishedTripCanBeFreed() {
        // Nothing resets IN_USE when a trip ends yet (1.3), so the admin must be able to free the bus.
        when(busRepository.findById(1L)).thenReturn(Optional.of(bus(1L, "51A-1", BusStatus.IN_USE)));
        when(busRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        BusResponse updated = busService.update(1L, request("51A-1", BusStatus.MAINTENANCE));

        assertThat(updated.status()).isEqualTo(BusStatus.MAINTENANCE);
    }

    @Test
    void updateHitByConcurrentDuplicatePlateIsConflict() {
        when(busRepository.findById(1L)).thenReturn(Optional.of(bus(1L, "51A-1", BusStatus.AVAILABLE)));
        when(busRepository.saveAndFlush(any())).thenThrow(plateViolation());

        assertThatThrownBy(() -> busService.update(1L, request("51A-2", BusStatus.AVAILABLE)))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("51A-2");
    }

    @Test
    void otherIntegrityViolationIsNotReportedAsDuplicatePlate() {
        DataIntegrityViolationException other = new DataIntegrityViolationException(
                "x", new RuntimeException("Column 'capacity' cannot be null"));
        when(busRepository.saveAndFlush(any())).thenThrow(other);

        assertThatThrownBy(() -> busService.create(request("51A-00001", BusStatus.AVAILABLE)))
                .isSameAs(other);
    }

    @Test
    void getBusReturnsResponseOrNotFound() {
        when(busRepository.findById(1L)).thenReturn(Optional.of(bus(1L, "51A-1", BusStatus.AVAILABLE)));
        when(busRepository.findById(2L)).thenReturn(Optional.empty());

        assertThat(busService.getBus(1L).plateNumber()).isEqualTo("51A-1");
        assertThatThrownBy(() -> busService.getBus(2L)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void listWithoutStatusUsesFindAll() {
        when(busRepository.findAll(any(Pageable.class)))
                .thenAnswer(inv -> new PageImpl<>(List.of(), inv.getArgument(0), 0));

        busService.getBuses(null, 0, 12);

        verify(busRepository).findAll(any(Pageable.class));
        verify(busRepository, never()).findByStatus(any(), any());
    }

    @Test
    void deleteRacingATripInsertIsConflict() {
        Buses bus = bus(1L, "51A-1", BusStatus.AVAILABLE);
        when(busRepository.findById(1L)).thenReturn(Optional.of(bus));
        doThrow(new DataIntegrityViolationException("trips_fk_bus")).when(busRepository).flush();

        assertThatThrownBy(() -> busService.delete(1L))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("MAINTENANCE");
    }

    @Test
    void deleteBusWithUnfinishedTripIsConflict() {
        when(busRepository.findById(1L)).thenReturn(Optional.of(bus(1L, "51A-1", BusStatus.IN_USE)));
        when(tripRepository.existsByBusIdAndStatusIn(1L, BusService.UNFINISHED_TRIP_STATUSES)).thenReturn(true);

        assertThatThrownBy(() -> busService.delete(1L))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Xe đang được gán cho chuyến chưa kết thúc");
        verify(busRepository, never()).delete(any());
    }

    @Test
    void deleteBusWithTripHistoryIsConflict() {
        when(busRepository.findById(1L)).thenReturn(Optional.of(bus(1L, "51A-1", BusStatus.AVAILABLE)));
        when(tripRepository.existsByBusId(1L)).thenReturn(true);

        assertThatThrownBy(() -> busService.delete(1L))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("MAINTENANCE");
        verify(busRepository, never()).delete(any());
    }

    @Test
    void deleteBusWithoutTripsDeletes() {
        Buses bus = bus(1L, "51A-1", BusStatus.AVAILABLE);
        when(busRepository.findById(1L)).thenReturn(Optional.of(bus));

        busService.delete(1L);

        verify(busRepository).delete(bus);
    }

    @Test
    void deleteUnknownBusIsNotFound() {
        when(busRepository.findById(anyLong())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> busService.delete(1L)).isInstanceOf(ResourceNotFoundException.class);
    }

    private static DataIntegrityViolationException plateViolation() {
        return new DataIntegrityViolationException("could not execute statement",
                new RuntimeException("Duplicate entry '51A-00001' for key 'buses.plate_number'"));
    }

    private static BusRequest request(String plate, BusStatus status) {
        BusRequest request = new BusRequest();
        request.setPlateNumber(plate);
        request.setCapacity(45);
        request.setStatus(status);
        return request;
    }

    private static Buses bus(Long id, String plate, BusStatus status) {
        Buses bus = Buses.builder().id(id).plateNumber(plate).capacity(45).status(status).build();
        return bus;
    }
}
