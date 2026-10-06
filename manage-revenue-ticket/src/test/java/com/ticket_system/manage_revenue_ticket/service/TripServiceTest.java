package com.ticket_system.manage_revenue_ticket.service;

import com.ticket_system.common.Dto.response.PageResponse;
import com.ticket_system.common.Enum.UserRole;
import com.ticket_system.common.exception.ConflictException;
import com.ticket_system.common.exception.ResourceNotFoundException;
import com.ticket_system.manage_revenue_ticket.Dto.request.TripRequest;
import com.ticket_system.manage_revenue_ticket.Dto.response.TripResponse;
import com.ticket_system.manage_revenue_ticket.Enum.BusStatus;
import com.ticket_system.manage_revenue_ticket.Enum.CustomerStatus;
import com.ticket_system.manage_revenue_ticket.Enum.DriverStatus;
import com.ticket_system.manage_revenue_ticket.Enum.RouteStatus;
import com.ticket_system.manage_revenue_ticket.Enum.TicketStatus;
import com.ticket_system.manage_revenue_ticket.Enum.TripStatus;
import com.ticket_system.manage_revenue_ticket.entity.Buses;
import com.ticket_system.manage_revenue_ticket.entity.Profile;
import com.ticket_system.manage_revenue_ticket.entity.Route;
import com.ticket_system.manage_revenue_ticket.entity.Trip;
import com.ticket_system.manage_revenue_ticket.entity.User;
import com.ticket_system.manage_revenue_ticket.projection.TripTicketStatsProjection;
import com.ticket_system.manage_revenue_ticket.repository.BusesRepository;
import com.ticket_system.manage_revenue_ticket.repository.ProfileRepository;
import com.ticket_system.manage_revenue_ticket.repository.RevenueRepository;
import com.ticket_system.manage_revenue_ticket.repository.RouteRepository;
import com.ticket_system.manage_revenue_ticket.repository.TicketRepository;
import com.ticket_system.manage_revenue_ticket.repository.TripRepository;
import com.ticket_system.manage_revenue_ticket.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Task 1.3: trip rules from spec review 1.3 — overlap instead of status blocking (D1 = A), cancel
 * cascades to tickets (D3 = A), manual status order (D4 = A), no past departure (D5 = A), revenue
 * from SUCCESS tickets (D6 = A), delete only without tickets (plan §1.3).
 */
class TripServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 5, 8, 0);
    private static final LocalDateTime DEP = NOW.plusDays(1);
    private static final LocalDateTime ARR = DEP.plusHours(3);

    private final TripRepository tripRepository = mock(TripRepository.class);
    private final RouteRepository routeRepository = mock(RouteRepository.class);
    private final BusesRepository busRepository = mock(BusesRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final ProfileRepository profileRepository = mock(ProfileRepository.class);
    private final TicketRepository ticketRepository = mock(TicketRepository.class);
    private final RevenueRepository revenueRepository = mock(RevenueRepository.class);
    private final Clock clock = Clock.fixed(NOW.atZone(ZoneId.systemDefault()).toInstant(), ZoneId.systemDefault());
    private final TripService tripService = new TripService(tripRepository, routeRepository, busRepository,
            userRepository, profileRepository, ticketRepository, revenueRepository, clock);

    private Route route;
    private Buses bus;
    private User driver;

    @BeforeEach
    void seed() {
        route = Route.builder().id(1L).routeName("HN - HP").startPoint("Hà Nội").endPoint("Hải Phòng")
                .distanceKm(new BigDecimal("120.00")).status(RouteStatus.ACTIVE).build();
        bus = Buses.builder().id(2L).plateNumber("51A-1").capacity(40).status(BusStatus.AVAILABLE).build();
        driver = user(3L, UserRole.DRIVER, DriverStatus.PENDING);
        when(routeRepository.findById(1L)).thenReturn(Optional.of(route));
        when(routeRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(route));
        when(busRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(bus));
        when(userRepository.findByIdForUpdate(3L)).thenReturn(Optional.of(driver));
        when(tripRepository.save(any())).thenAnswer(inv -> {
            Trip trip = inv.getArgument(0);
            if (trip.getId() == null) trip.setId(99L);
            return trip;
        });
        when(ticketRepository.findTripTicketStats(anyCollection())).thenReturn(List.of());
        when(profileRepository.findByUserIdIn(anyCollection())).thenReturn(List.of());
    }

    // ─── list / get ─────────────────────────────────────────────────────────

    @Test
    void listMapsTicketStatsAndDriverProfileInOneQueryEach() {
        Trip trip = trip(5L, TripStatus.SCHEDULED);
        when(tripRepository.search(eq(TripStatus.SCHEDULED), eq(1L), isNull(), isNull(), eq(DEP), eq(ARR), any()))
                .thenAnswer(inv -> new PageImpl<>(List.of(trip), inv.getArgument(6), 1));
        when(ticketRepository.findTripTicketStats(List.of(5L))).thenReturn(List.of(stats(5L, 14L, 12L, "900000.00")));
        when(profileRepository.findByUserIdIn(List.of(3L))).thenReturn(List.of(profile(driver, "Tài Xế")));

        PageResponse<TripResponse> page = tripService.getTrips(TripStatus.SCHEDULED, 1L, null, null, DEP, ARR, 0, 10);

        TripResponse row = page.content().get(0);
        assertThat(row.ticketCount()).isEqualTo(14);
        assertThat(row.bookedSeats()).isEqualTo(12);
        assertThat(row.revenue()).isEqualByComparingTo("900000");
        assertThat(row.driver().fullName()).isEqualTo("Tài Xế");
        assertThat(row.route().startPoint()).isEqualTo("Hà Nội");
        assertThat(row.bus().capacity()).isEqualTo(40);
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(tripRepository).search(any(), any(), any(), any(), any(), any(), pageable.capture());
        assertThat(pageable.getValue().getPageSize()).isEqualTo(10);
    }

    @Test
    void tripWithoutTicketsHasZeroSeatsAndRevenue() {
        stubTrip(trip(5L, TripStatus.SCHEDULED));

        TripResponse response = tripService.getTrip(5L);

        assertThat(response.ticketCount()).isZero();
        assertThat(response.bookedSeats()).isZero();
        assertThat(response.revenue()).isEqualByComparingTo("0");
        assertThat(response.driver().fullName()).isNull();
    }

    @Test
    void listRejectsBadRangeAndPageSize() {
        assertThatThrownBy(() -> tripService.getTrips(null, null, null, null, ARR, DEP, 0, 10))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> tripService.getTrips(null, null, null, null, DEP, DEP, 0, 10))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> tripService.getTrips(null, null, null, null, null, null, 0, 101))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void unknownTripIsNotFound() {
        when(tripRepository.findDetailedById(anyLong())).thenReturn(Optional.empty());
        when(tripRepository.findByIdForUpdate(anyLong())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> tripService.getTrip(7L)).isInstanceOf(ResourceNotFoundException.class);
    }

    // ─── create ─────────────────────────────────────────────────────────────

    @Test
    void createSchedulesTheTripAndMarksBusAndDriverBusy() {
        when(tripRepository.existsByBusIdAndStatusIn(eq(2L), anyCollection())).thenReturn(true);
        when(tripRepository.existsByDriverIdAndStatusIn(eq(3L), anyCollection())).thenReturn(true);

        TripResponse created = tripService.create(request());

        ArgumentCaptor<Trip> saved = ArgumentCaptor.forClass(Trip.class);
        verify(tripRepository).save(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo(TripStatus.SCHEDULED);
        assertThat(saved.getValue().getRevenue()).isEqualByComparingTo("0");
        assertThat(created.id()).isEqualTo(99L);
        assertThat(bus.getStatus()).isEqualTo(BusStatus.IN_USE);
        assertThat(driver.getDriverStatus()).isEqualTo(DriverStatus.ACTIVE);
    }

    @Test
    void busAlreadyInUseCanTakeANonOverlappingTrip() {
        // D1 = A: IN_USE no longer blocks; only an overlapping trip does.
        bus.setStatus(BusStatus.IN_USE);
        driver.setDriverStatus(DriverStatus.ACTIVE);

        tripService.create(request());

        verify(tripRepository).save(any());
    }

    @Test
    void overlappingBusTripIsConflict() {
        when(tripRepository.existsBusOverlap(eq(2L), eq(DEP), eq(ARR), isNull(), anyCollection())).thenReturn(true);

        assertThatThrownBy(() -> tripService.create(request()))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("51A-1");
        verify(tripRepository, never()).save(any());
    }

    @Test
    void overlappingDriverTripIsConflict() {
        when(tripRepository.existsDriverOverlap(eq(3L), eq(DEP), eq(ARR), isNull(), anyCollection())).thenReturn(true);

        assertThatThrownBy(() -> tripService.create(request())).isInstanceOf(ConflictException.class);
        verify(tripRepository, never()).save(any());
    }

    @Test
    void arrivalNotAfterDepartureIsBadRequest() {
        TripRequest request = request();
        request.setArrivalTime(DEP);

        assertThatThrownBy(() -> tripService.create(request)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void departureInThePastIsBadRequest() {
        TripRequest request = request();
        request.setDepartureTime(NOW.minusMinutes(1));
        request.setArrivalTime(NOW.plusHours(2));

        assertThatThrownBy(() -> tripService.create(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("quá khứ");
    }

    @Test
    void inactiveRouteIsConflict() {
        route.setStatus(RouteStatus.INACTIVE);

        assertThatThrownBy(() -> tripService.create(request())).isInstanceOf(ConflictException.class);
    }

    @Test
    void busInMaintenanceIsConflict() {
        bus.setStatus(BusStatus.MAINTENANCE);

        assertThatThrownBy(() -> tripService.create(request())).isInstanceOf(ConflictException.class);
        verify(tripRepository, never()).save(any());
    }

    @Test
    void nonDriverIsBadRequest() {
        driver.setRole(UserRole.COLLECTOR);

        assertThatThrownBy(() -> tripService.create(request())).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void lockedOrInactiveDriverIsConflict() {
        driver.setIsActive(false);
        assertThatThrownBy(() -> tripService.create(request())).isInstanceOf(ConflictException.class);

        driver.setIsActive(true);
        driver.setDriverStatus(DriverStatus.INACTIVE);
        assertThatThrownBy(() -> tripService.create(request())).isInstanceOf(ConflictException.class);
    }

    @Test
    void unknownRouteBusOrDriverIsNotFound() {
        TripRequest request = request();
        request.setBusId(42L);
        when(busRepository.findByIdForUpdate(42L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> tripService.create(request)).isInstanceOf(ResourceNotFoundException.class);
    }

    // ─── update ─────────────────────────────────────────────────────────────

    @Test
    void updateExcludesTheTripItselfFromTheOverlapCheck() {
        Trip trip = trip(5L, TripStatus.SCHEDULED);
        stubTrip(trip);

        tripService.update(5L, request());

        verify(tripRepository).existsBusOverlap(eq(2L), eq(DEP), eq(ARR), eq(5L), anyCollection());
        verify(tripRepository).existsDriverOverlap(eq(3L), eq(DEP), eq(ARR), eq(5L), anyCollection());
    }

    @Test
    void updateOnlyAllowedWhileScheduled() {
        stubTrip(trip(5L, TripStatus.ONGOING));

        assertThatThrownBy(() -> tripService.update(5L, request())).isInstanceOf(ConflictException.class);
    }

    @Test
    void switchingToABusSmallerThanTheSoldSeatsIsConflict() {
        stubTrip(trip(5L, TripStatus.SCHEDULED));
        when(ticketRepository.countActiveTicketsByTripId(5L)).thenReturn(41L);

        assertThatThrownBy(() -> tripService.update(5L, request()))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("41");
    }

    @Test
    void switchingBusAndDriverFreesTheOldOnes() {
        Buses oldBus = Buses.builder().id(20L).plateNumber("OLD").capacity(40).status(BusStatus.IN_USE).build();
        User oldDriver = user(30L, UserRole.DRIVER, DriverStatus.ACTIVE);
        Trip trip = trip(5L, TripStatus.SCHEDULED);
        trip.setBus(oldBus);
        trip.setDriver(oldDriver);
        stubTrip(trip);
        when(busRepository.findByIdForUpdate(20L)).thenReturn(Optional.of(oldBus));
        when(userRepository.findByIdForUpdate(30L)).thenReturn(Optional.of(oldDriver));
        when(tripRepository.existsByBusIdAndStatusIn(eq(20L), anyCollection())).thenReturn(false);
        when(tripRepository.existsByDriverIdAndStatusIn(eq(30L), anyCollection())).thenReturn(false);

        tripService.update(5L, request());

        assertThat(oldBus.getStatus()).isEqualTo(BusStatus.AVAILABLE);
        assertThat(oldDriver.getDriverStatus()).isEqualTo(DriverStatus.PENDING);
        assertThat(trip.getBus()).isSameAs(bus);
        // Lock order: old/new buses by ascending id (2 before 20), then drivers (3 before 30).
        org.mockito.InOrder order = org.mockito.Mockito.inOrder(tripRepository, busRepository, userRepository);
        order.verify(tripRepository).findByIdForUpdate(5L);
        order.verify(busRepository).findByIdForUpdate(2L);
        order.verify(busRepository).findByIdForUpdate(20L);
        order.verify(userRepository).findByIdForUpdate(3L);
        order.verify(userRepository).findByIdForUpdate(30L);
    }

    @Test
    void oldBusWithAnotherUnfinishedTripStaysInUse() {
        Buses oldBus = Buses.builder().id(20L).plateNumber("OLD").capacity(40).status(BusStatus.IN_USE).build();
        Trip trip = trip(5L, TripStatus.SCHEDULED);
        trip.setBus(oldBus);
        stubTrip(trip);
        when(busRepository.findByIdForUpdate(20L)).thenReturn(Optional.of(oldBus));
        when(tripRepository.existsByBusIdAndStatusIn(eq(20L), anyCollection())).thenReturn(true);

        tripService.update(5L, request());

        assertThat(oldBus.getStatus()).isEqualTo(BusStatus.IN_USE);
    }

    @Test
    void movingToABusThatLacksAnAlreadySoldSeatNumberIsConflict() {
        // Backend review M2: 1 ticket is fine for a 40-seat bus, but its seat 35 does not exist on it.
        bus.setCapacity(20);
        stubTrip(trip(5L, TripStatus.SCHEDULED));
        when(ticketRepository.countActiveTicketsByTripId(5L)).thenReturn(1L);
        when(ticketRepository.findMaxActiveSeatNumberByTripId(5L)).thenReturn(35);

        assertThatThrownBy(() -> tripService.update(5L, request()))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("35");
        verify(tripRepository, never()).save(any());
    }

    @Test
    void delayedTripKeepsItsPastDepartureWhenOnlyTheDriverChanges() {
        // D5 = A applies to a new departure time; a late trip can still be reassigned.
        Trip late = trip(5L, TripStatus.SCHEDULED);
        late.setDepartureTime(NOW.minusHours(1));
        late.setArrivalTime(NOW.plusHours(2));
        stubTrip(late);
        TripRequest request = request();
        request.setDepartureTime(NOW.minusHours(1));
        request.setArrivalTime(NOW.plusHours(2));

        tripService.update(5L, request);

        verify(tripRepository).save(late);
    }

    @Test
    void movingADepartureIntoThePastIsBadRequest() {
        stubTrip(trip(5L, TripStatus.SCHEDULED));
        TripRequest request = request();
        request.setDepartureTime(NOW.minusMinutes(5));
        request.setArrivalTime(NOW.plusHours(1));

        assertThatThrownBy(() -> tripService.update(5L, request)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void updateRejectsTheSameRulesAsCreate() {
        stubTrip(trip(5L, TripStatus.SCHEDULED));

        TripRequest backwards = request();
        backwards.setArrivalTime(DEP.minusHours(1));
        assertThatThrownBy(() -> tripService.update(5L, backwards)).isInstanceOf(IllegalArgumentException.class);

        route.setStatus(RouteStatus.INACTIVE);
        assertThatThrownBy(() -> tripService.update(5L, request())).isInstanceOf(ConflictException.class);
        route.setStatus(RouteStatus.ACTIVE);

        bus.setStatus(BusStatus.MAINTENANCE);
        assertThatThrownBy(() -> tripService.update(5L, request())).isInstanceOf(ConflictException.class);
        bus.setStatus(BusStatus.AVAILABLE);

        driver.setIsActive(false);
        assertThatThrownBy(() -> tripService.update(5L, request())).isInstanceOf(ConflictException.class);
        driver.setIsActive(true);

        when(tripRepository.existsBusOverlap(eq(2L), any(), any(), eq(5L), anyCollection())).thenReturn(true);
        assertThatThrownBy(() -> tripService.update(5L, request())).isInstanceOf(ConflictException.class);

        verify(tripRepository, never()).save(any());
    }

    @Test
    void unknownTripOnWriteIsNotFound() {
        when(tripRepository.findByIdForUpdate(77L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> tripService.update(77L, request())).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> tripService.changeStatus(77L, TripStatus.ONGOING))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> tripService.delete(77L)).isInstanceOf(ResourceNotFoundException.class);
    }

    // ─── status ─────────────────────────────────────────────────────────────

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({"SCHEDULED,ONGOING", "ONGOING,COMPLETED", "SCHEDULED,CANCELLED"})
    void allowedMoves(TripStatus from, TripStatus to) {
        stubTrip(trip(5L, from));

        assertThat(tripService.changeStatus(5L, to).status()).isEqualTo(to);
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({"SCHEDULED,COMPLETED", "ONGOING,SCHEDULED", "ONGOING,CANCELLED", "COMPLETED,ONGOING",
            "CANCELLED,SCHEDULED", "COMPLETED,CANCELLED"})
    void forbiddenMovesAreConflict(TripStatus from, TripStatus to) {
        stubTrip(trip(5L, from));

        assertThatThrownBy(() -> tripService.changeStatus(5L, to)).isInstanceOf(ConflictException.class);
    }

    @Test
    void sameStatusIsIdempotent() {
        stubTrip(trip(5L, TripStatus.ONGOING));

        assertThat(tripService.changeStatus(5L, TripStatus.ONGOING).status()).isEqualTo(TripStatus.ONGOING);
        verify(tripRepository, never()).save(any());
    }

    @Test
    void startingEarlyIsAllowed() {
        // D4 = A: no time constraint, the trip departs tomorrow.
        stubTrip(trip(5L, TripStatus.SCHEDULED));

        assertThat(tripService.changeStatus(5L, TripStatus.ONGOING).status()).isEqualTo(TripStatus.ONGOING);
    }

    @Test
    void cancellingCancelsTicketsAndFreesBusAndDriver() {
        bus.setStatus(BusStatus.IN_USE);
        driver.setDriverStatus(DriverStatus.ACTIVE);
        User customer = user(50L, UserRole.CUSTOMER, null);
        customer.setUserStatus(CustomerStatus.BOOKED);
        stubTrip(trip(5L, TripStatus.SCHEDULED));
        when(ticketRepository.findActiveCustomerIdsByTripId(5L)).thenReturn(List.of(50L));
        when(ticketRepository.cancelActiveTicketsByTripId(5L)).thenReturn(3);
        when(userRepository.findAllById(List.of(50L))).thenReturn(List.of(customer));

        tripService.changeStatus(5L, TripStatus.CANCELLED);

        verify(ticketRepository).cancelActiveTicketsByTripId(5L);
        assertThat(customer.getUserStatus()).isEqualTo(CustomerStatus.NOT_BOOKED);
        assertThat(bus.getStatus()).isEqualTo(BusStatus.AVAILABLE);
        assertThat(driver.getDriverStatus()).isEqualTo(DriverStatus.PENDING);
    }

    @Test
    void cancellingKeepsACustomerBookedWhileTheyHoldATicketOnAnotherTrip() {
        User customer = user(50L, UserRole.CUSTOMER, null);
        customer.setUserStatus(CustomerStatus.BOOKED);
        stubTrip(trip(5L, TripStatus.SCHEDULED));
        when(ticketRepository.findActiveCustomerIdsByTripId(5L)).thenReturn(List.of(50L));
        when(userRepository.findAllById(List.of(50L))).thenReturn(List.of(customer));
        when(ticketRepository.existsByCustomerIdAndStatusTicketNot(50L, TicketStatus.CANCELLED)).thenReturn(true);

        tripService.changeStatus(5L, TripStatus.CANCELLED);

        assertThat(customer.getUserStatus()).isEqualTo(CustomerStatus.BOOKED);
    }

    @Test
    void cancellingATripWithoutTicketsWorks() {
        stubTrip(trip(5L, TripStatus.SCHEDULED));
        when(ticketRepository.findActiveCustomerIdsByTripId(5L)).thenReturn(List.of());

        assertThat(tripService.changeStatus(5L, TripStatus.CANCELLED).status()).isEqualTo(TripStatus.CANCELLED);
    }

    @Test
    void completingRecomputesTheDriverAndKeepsAnInactiveDriverInactive() {
        driver.setDriverStatus(DriverStatus.ACTIVE);
        stubTrip(trip(5L, TripStatus.ONGOING));
        when(tripRepository.existsByDriverIdAndStatusIn(eq(3L), anyCollection())).thenReturn(true);

        tripService.changeStatus(5L, TripStatus.COMPLETED);
        assertThat(driver.getDriverStatus()).isEqualTo(DriverStatus.ACTIVE);

        driver.setDriverStatus(DriverStatus.INACTIVE);
        Trip other = trip(6L, TripStatus.ONGOING);
        stubTrip(other);
        tripService.changeStatus(6L, TripStatus.COMPLETED);
        assertThat(driver.getDriverStatus()).isEqualTo(DriverStatus.INACTIVE);
    }

    @Test
    void writesLockTheTripThenBusThenDriver() {
        stubTrip(trip(5L, TripStatus.SCHEDULED));

        tripService.changeStatus(5L, TripStatus.ONGOING);

        org.mockito.InOrder order = org.mockito.Mockito.inOrder(tripRepository, busRepository, userRepository);
        order.verify(tripRepository).findByIdForUpdate(5L);
        order.verify(busRepository).findByIdForUpdate(2L);
        order.verify(userRepository).findByIdForUpdate(3L);
    }

    @Test
    void completingKeepsBusInUseWhileItHasAnotherScheduledTrip() {
        bus.setStatus(BusStatus.IN_USE);
        stubTrip(trip(5L, TripStatus.ONGOING));
        when(tripRepository.existsByBusIdAndStatusIn(eq(2L), anyCollection())).thenReturn(true);

        tripService.changeStatus(5L, TripStatus.COMPLETED);

        assertThat(bus.getStatus()).isEqualTo(BusStatus.IN_USE);
        verify(ticketRepository, never()).cancelActiveTicketsByTripId(anyLong());
    }

    @Test
    void finishingATripDoesNotTouchABusInMaintenance() {
        bus.setStatus(BusStatus.MAINTENANCE);
        stubTrip(trip(5L, TripStatus.ONGOING));

        tripService.changeStatus(5L, TripStatus.COMPLETED);

        assertThat(bus.getStatus()).isEqualTo(BusStatus.MAINTENANCE);
        verify(busRepository, never()).save(any());
    }

    // ─── delete ─────────────────────────────────────────────────────────────

    @Test
    void deleteWithoutTicketsFreesBusAndDriver() {
        bus.setStatus(BusStatus.IN_USE);
        Trip trip = trip(5L, TripStatus.SCHEDULED);
        stubTrip(trip);

        tripService.delete(5L);

        verify(tripRepository).delete(trip);
        assertThat(bus.getStatus()).isEqualTo(BusStatus.AVAILABLE);
    }

    @Test
    void deleteWithTicketsOrRevenueIsConflict() {
        stubTrip(trip(5L, TripStatus.SCHEDULED));
        when(ticketRepository.existsByTripId(5L)).thenReturn(true);
        assertThatThrownBy(() -> tripService.delete(5L)).isInstanceOf(ConflictException.class);

        when(ticketRepository.existsByTripId(5L)).thenReturn(false);
        when(revenueRepository.existsByTripId(5L)).thenReturn(true);
        assertThatThrownBy(() -> tripService.delete(5L)).isInstanceOf(ConflictException.class);
        verify(tripRepository, never()).delete(any(Trip.class));
    }

    @Test
    void deleteRacingATicketInsertIsConflict() {
        Trip trip = trip(5L, TripStatus.SCHEDULED);
        stubTrip(trip);
        doThrow(new DataIntegrityViolationException("tickets_fk_trip")).when(tripRepository).flush();

        assertThatThrownBy(() -> tripService.delete(5L)).isInstanceOf(ConflictException.class);
    }

    // ─── helpers ────────────────────────────────────────────────────────────

    private void stubTrip(Trip trip) {
        when(tripRepository.findDetailedById(trip.getId())).thenReturn(Optional.of(trip));
        when(tripRepository.findByIdForUpdate(trip.getId())).thenReturn(Optional.of(trip));
    }

    private Trip trip(long id, TripStatus status) {
        Trip trip = Trip.builder().route(route).bus(bus).driver(driver)
                .departureTime(DEP).arrivalTime(ARR).status(status).revenue(BigDecimal.ZERO).build();
        trip.setId(id);
        return trip;
    }

    private static User user(long id, UserRole role, DriverStatus driverStatus) {
        return User.builder().id(id).email("u" + id + "@test.vn").password("x").role(role)
                .isActive(true).driverStatus(driverStatus).build();
    }

    private static Profile profile(User user, String fullName) {
        return Profile.builder().user(user).fullName(fullName).build();
    }

    private static TripTicketStatsProjection stats(long tripId, long tickets, long booked, String revenue) {
        return new TripTicketStatsProjection() {
            @Override public Long getTripId() { return tripId; }
            @Override public Long getTicketCount() { return tickets; }
            @Override public Long getBookedSeats() { return booked; }
            @Override public BigDecimal getRevenue() { return new BigDecimal(revenue); }
        };
    }

    private static TripRequest request() {
        TripRequest request = new TripRequest();
        request.setRouteId(1L);
        request.setBusId(2L);
        request.setDriverId(3L);
        request.setDepartureTime(DEP);
        request.setArrivalTime(ARR);
        return request;
    }
}
