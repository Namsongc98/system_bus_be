package com.ticket_system.manage_revenue_ticket.service;

import com.ticket_system.common.Dto.response.PageResponse;
import com.ticket_system.common.Enum.UserRole;
import com.ticket_system.common.exception.ConflictException;
import com.ticket_system.common.exception.ResourceNotFoundException;
import com.ticket_system.common.util.PageRequests;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Trips for the admin screen (task 1.3). Rules agreed in spec review 1.3:
 * D1 — a bus / driver may hold several future trips as long as their times do not overlap;
 *      IN_USE / driverStatus ACTIVE only mean "has an unfinished trip" and are recomputed here;
 * D3 — cancelling a trip cancels its PENDING / SUCCESS tickets;
 * D4 — status moves are manual, only the order is enforced;
 * D5 — departure may not be in the past;
 * D6 — revenue is the sum of SUCCESS ticket prices.
 */
@Service
public class TripService {

    private static final Logger log = LoggerFactory.getLogger(TripService.class);

    static final List<TripStatus> UNFINISHED = BusService.UNFINISHED_TRIP_STATUSES;
    // Allowed status moves (plan §1.3); anything else is 409.
    private static final Map<TripStatus, Set<TripStatus>> TRANSITIONS = Map.of(
            TripStatus.SCHEDULED, Set.of(TripStatus.ONGOING, TripStatus.CANCELLED),
            TripStatus.ONGOING, Set.of(TripStatus.COMPLETED),
            TripStatus.COMPLETED, Set.of(),
            TripStatus.CANCELLED, Set.of());

    private final TripRepository tripRepository;
    private final RouteRepository routeRepository;
    private final BusesRepository busRepository;
    private final UserRepository userRepository;
    private final ProfileRepository profileRepository;
    private final TicketRepository ticketRepository;
    private final RevenueRepository revenueRepository;
    private final Clock clock;

    @Autowired
    public TripService(TripRepository tripRepository, RouteRepository routeRepository,
                       BusesRepository busRepository, UserRepository userRepository,
                       ProfileRepository profileRepository, TicketRepository ticketRepository,
                       RevenueRepository revenueRepository) {
        this(tripRepository, routeRepository, busRepository, userRepository, profileRepository,
                ticketRepository, revenueRepository, Clock.systemDefaultZone());
    }

    // Tests pin "now" through the clock.
    TripService(TripRepository tripRepository, RouteRepository routeRepository,
                BusesRepository busRepository, UserRepository userRepository,
                ProfileRepository profileRepository, TicketRepository ticketRepository,
                RevenueRepository revenueRepository, Clock clock) {
        this.tripRepository = tripRepository;
        this.routeRepository = routeRepository;
        this.busRepository = busRepository;
        this.userRepository = userRepository;
        this.profileRepository = profileRepository;
        this.ticketRepository = ticketRepository;
        this.revenueRepository = revenueRepository;
        this.clock = clock;
    }

    // ─── read ────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public PageResponse<TripResponse> getTrips(TripStatus status, Long routeId, Long busId, Long driverId,
                                               LocalDateTime from, LocalDateTime to, int page, int size) {
        if (from != null && to != null && !from.isBefore(to)) {
            throw new IllegalArgumentException("'from' phải trước 'to'");
        }
        // The query orders by departureTime, id itself; the Pageable only carries page and size.
        Page<Trip> trips = tripRepository.search(status, routeId, busId, driverId, from, to, PageRequests.of(page, size));
        List<TripResponse> content = toResponses(trips.getContent());
        return new PageResponse<>(content, trips.getNumber(), trips.getSize(),
                trips.getTotalElements(), trips.getTotalPages());
    }

    @Transactional(readOnly = true)
    public TripResponse getTrip(Long tripId) {
        return toResponse(findDetailed(tripId));
    }

    // ─── write ───────────────────────────────────────────────────────────────
    //
    // Concurrency (lead review 1.3 / backend review H1–H5): every write locks the rows it decides on
    // BEFORE reading them, always in the order trip → route → buses (id asc) → drivers (id asc), and runs
    // at READ_COMMITTED so the overlap and "has unfinished trip" checks made after a lock see what other
    // transactions committed meanwhile (under REPEATABLE READ they would read the transaction's first
    // snapshot). TicketService locks the trip row too, and BusService / RouteService lock their row.

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public TripResponse create(TripRequest request) {
        validateTimes(request, true);
        Route route = lockedActiveRoute(request.getRouteId());
        Buses bus = usableBus(lockBuses(List.of(request.getBusId())).get(request.getBusId()));
        User driver = usableDriver(lockDrivers(List.of(request.getDriverId())).get(request.getDriverId()));
        assertNoOverlap(bus, driver, request, null);

        Trip trip = Trip.builder()
                .route(route)
                .bus(bus)
                .driver(driver)
                .departureTime(request.getDepartureTime())
                .arrivalTime(request.getArrivalTime())
                .status(TripStatus.SCHEDULED)
                .revenue(BigDecimal.ZERO)
                .build();
        Trip saved = tripRepository.save(trip);
        refreshBusStatus(bus);
        refreshDriverStatus(driver);
        log.info("Trip {} created: route {}, bus {}, driver {}", saved.getId(), route.getId(), bus.getId(), driver.getId());
        return toResponse(saved);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public TripResponse update(Long tripId, TripRequest request) {
        Trip trip = lockedTrip(tripId);
        if (trip.getStatus() != TripStatus.SCHEDULED) {
            throw new ConflictException("Chỉ sửa được chuyến đang SCHEDULED (hiện là " + trip.getStatus() + ")");
        }
        // D5 = A guards new departure times; a delayed trip can still get another bus / driver.
        validateTimes(request, !request.getDepartureTime().equals(trip.getDepartureTime()));
        Route route = lockedActiveRoute(request.getRouteId());
        Long oldBusId = trip.getBus().getId();
        Long oldDriverId = trip.getDriver().getId();
        Map<Long, Buses> buses = lockBuses(List.of(oldBusId, request.getBusId()));
        Map<Long, User> drivers = lockDrivers(List.of(oldDriverId, request.getDriverId()));
        Buses bus = usableBus(buses.get(request.getBusId()));
        User driver = usableDriver(drivers.get(request.getDriverId()));

        long bookedSeats = ticketRepository.countActiveTicketsByTripId(tripId);
        Integer highestSeat = ticketRepository.findMaxActiveSeatNumberByTripId(tripId);
        if (bus.getCapacity() < bookedSeats || (highestSeat != null && highestSeat > bus.getCapacity())) {
            throw new ConflictException("Xe " + bus.getPlateNumber() + " chỉ có " + bus.getCapacity()
                    + " ghế, chuyến đã bán " + bookedSeats + " vé (ghế cao nhất " + highestSeat + ")");
        }
        assertNoOverlap(bus, driver, request, tripId);

        trip.setRoute(route);
        trip.setBus(bus);
        trip.setDriver(driver);
        trip.setDepartureTime(request.getDepartureTime());
        trip.setArrivalTime(request.getArrivalTime());
        Trip saved = tripRepository.save(trip);

        buses.values().forEach(this::refreshBusStatus);
        drivers.values().forEach(this::refreshDriverStatus);
        return toResponse(saved);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public TripResponse changeStatus(Long tripId, TripStatus target) {
        Trip trip = lockedTrip(tripId);
        TripStatus current = trip.getStatus();
        if (current == target) {
            return toResponse(trip);
        }
        if (!TRANSITIONS.get(current).contains(target)) {
            throw new ConflictException("Không thể chuyển chuyến từ " + current + " sang " + target);
        }
        Buses bus = lockBuses(List.of(trip.getBus().getId())).get(trip.getBus().getId());
        User driver = lockDrivers(List.of(trip.getDriver().getId())).get(trip.getDriver().getId());
        if (target == TripStatus.CANCELLED) {
            cancelTickets(tripId);
        }
        trip.setStatus(target);
        Trip saved = tripRepository.save(trip);
        if (target == TripStatus.COMPLETED || target == TripStatus.CANCELLED) {
            refreshBusStatus(bus);
            refreshDriverStatus(driver);
        }
        log.info("Trip {} status {} -> {}", tripId, current, target);
        return toResponse(saved);
    }

    // Plan §1.3: only a trip without any ticket may be deleted; otherwise cancel it.
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void delete(Long tripId) {
        Trip trip = lockedTrip(tripId);
        Buses bus = lockBuses(List.of(trip.getBus().getId())).get(trip.getBus().getId());
        User driver = lockDrivers(List.of(trip.getDriver().getId())).get(trip.getDriver().getId());
        if (ticketRepository.existsByTripId(tripId) || revenueRepository.existsByTripId(tripId)) {
            throw new ConflictException("Chuyến đã có vé, hãy huỷ chuyến thay vì xoá");
        }
        try {
            tripRepository.delete(trip);
            tripRepository.flush();
        } catch (DataIntegrityViolationException ex) {
            // A ticket was inserted between the check and the delete.
            throw new ConflictException("Chuyến đã có vé, hãy huỷ chuyến thay vì xoá");
        }
        refreshBusStatus(bus);
        refreshDriverStatus(driver);
        log.info("Trip {} deleted", tripId);
    }

    // ─── rules ───────────────────────────────────────────────────────────────

    private void validateTimes(TripRequest request, boolean checkPast) {
        if (!request.getArrivalTime().isAfter(request.getDepartureTime())) {
            throw new IllegalArgumentException("Giờ đến phải sau giờ khởi hành");
        }
        if (checkPast && request.getDepartureTime().isBefore(LocalDateTime.now(clock))) {
            throw new IllegalArgumentException("Giờ khởi hành không được ở quá khứ");
        }
    }

    private Route lockedActiveRoute(Long routeId) {
        Route route = routeRepository.findByIdForUpdate(routeId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tuyến có ID: " + routeId));
        if (route.getStatus() != RouteStatus.ACTIVE) {
            throw new ConflictException("Tuyến " + route.getRouteName() + " đang ngừng hoạt động");
        }
        return route;
    }

    // Locks each bus once, in ascending id order, so two writers never wait on each other in a cycle.
    private Map<Long, Buses> lockBuses(Collection<Long> busIds) {
        Map<Long, Buses> locked = new LinkedHashMap<>();
        for (Long busId : new TreeSet<>(busIds)) {
            locked.put(busId, busRepository.findByIdForUpdate(busId)
                    .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy xe có ID: " + busId)));
        }
        return locked;
    }

    private Map<Long, User> lockDrivers(Collection<Long> driverIds) {
        Map<Long, User> locked = new LinkedHashMap<>();
        for (Long driverId : new TreeSet<>(driverIds)) {
            locked.put(driverId, userRepository.findByIdForUpdate(driverId)
                    .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tài xế có ID: " + driverId)));
        }
        return locked;
    }

    private static Buses usableBus(Buses bus) {
        if (bus.getStatus() == BusStatus.MAINTENANCE) {
            throw new ConflictException("Xe " + bus.getPlateNumber() + " đang bảo dưỡng");
        }
        return bus;
    }

    private static User usableDriver(User driver) {
        if (driver.getRole() != UserRole.DRIVER) {
            throw new IllegalArgumentException("Người dùng ID " + driver.getId() + " không phải là tài xế (DRIVER)");
        }
        if (Boolean.FALSE.equals(driver.getIsActive()) || driver.getDriverStatus() == DriverStatus.INACTIVE) {
            throw new ConflictException("Tài xế " + driver.getEmail() + " đang bị khoá hoặc không hoạt động");
        }
        return driver;
    }

    private void assertNoOverlap(Buses bus, User driver, TripRequest request, Long excludeTripId) {
        if (tripRepository.existsBusOverlap(bus.getId(), request.getDepartureTime(), request.getArrivalTime(),
                excludeTripId, UNFINISHED)) {
            throw new ConflictException("Xe " + bus.getPlateNumber() + " đã có chuyến khác trong khung giờ này");
        }
        if (tripRepository.existsDriverOverlap(driver.getId(), request.getDepartureTime(), request.getArrivalTime(),
                excludeTripId, UNFINISHED)) {
            throw new ConflictException("Tài xế " + driver.getEmail() + " đã có chuyến khác trong khung giờ này");
        }
    }

    // D3 = A: every PENDING / SUCCESS ticket of the trip becomes CANCELLED; refunds are task 2.4.
    private void cancelTickets(Long tripId) {
        List<Long> customerIds = ticketRepository.findActiveCustomerIdsByTripId(tripId);
        int cancelled = ticketRepository.cancelActiveTicketsByTripId(tripId);
        // Same customer bookkeeping as TicketService.cancelTicket (rule B5, decided in 2.2), but only
        // for customers left without any active ticket on another trip.
        for (User customer : userRepository.findAllById(customerIds)) {
            if (!ticketRepository.existsByCustomerIdAndStatusTicketNot(customer.getId(), TicketStatus.CANCELLED)) {
                customer.setUserStatus(CustomerStatus.NOT_BOOKED);
            }
        }
        log.info("Trip {} cancelled: {} ticket(s) cancelled", tripId, cancelled);
    }

    // D1 = A: IN_USE while the bus has an unfinished trip, AVAILABLE otherwise. MAINTENANCE is the admin's.
    private void refreshBusStatus(Buses bus) {
        if (bus.getStatus() == BusStatus.MAINTENANCE) {
            return;
        }
        bus.setStatus(tripRepository.existsByBusIdAndStatusIn(bus.getId(), UNFINISHED)
                ? BusStatus.IN_USE : BusStatus.AVAILABLE);
        busRepository.save(bus);
    }

    // Same rule for the driver: ACTIVE while driving / scheduled, PENDING (free) otherwise. INACTIVE is kept.
    private void refreshDriverStatus(User driver) {
        if (driver.getDriverStatus() == DriverStatus.INACTIVE) {
            return;
        }
        driver.setDriverStatus(tripRepository.existsByDriverIdAndStatusIn(driver.getId(), UNFINISHED)
                ? DriverStatus.ACTIVE : DriverStatus.PENDING);
        userRepository.save(driver);
    }

    // ─── mapping ─────────────────────────────────────────────────────────────

    private Trip findDetailed(Long tripId) {
        return tripRepository.findDetailedById(tripId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy chuyến có ID: " + tripId));
    }

    // Locks the trip row only (serialises with ticket sales, see TicketService). Route, bus and driver
    // stay lazy here and are locked — and so read fresh — by the caller, in the global lock order.
    private Trip lockedTrip(Long tripId) {
        return tripRepository.findByIdForUpdate(tripId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy chuyến có ID: " + tripId));
    }

    private TripResponse toResponse(Trip trip) {
        return toResponses(List.of(trip)).get(0);
    }

    // Ticket numbers and driver profiles for the whole page in two queries, not one per trip.
    private List<TripResponse> toResponses(List<Trip> trips) {
        if (trips.isEmpty()) {
            return List.of();
        }
        List<Long> tripIds = trips.stream().map(Trip::getId).toList();
        Map<Long, TripTicketStatsProjection> stats = ticketRepository.findTripTicketStats(tripIds).stream()
                .collect(Collectors.toMap(TripTicketStatsProjection::getTripId, Function.identity()));
        List<Long> driverIds = trips.stream().map(trip -> trip.getDriver().getId()).distinct().toList();
        Map<Long, Profile> profiles = profileRepository.findByUserIdIn(driverIds).stream()
                .collect(Collectors.toMap(profile -> profile.getUser().getId(), Function.identity(), (a, b) -> a));
        return trips.stream().map(trip -> {
            TripTicketStatsProjection row = stats.get(trip.getId());
            return TripResponse.from(trip,
                    profiles.get(trip.getDriver().getId()),
                    row == null || row.getTicketCount() == null ? 0 : row.getTicketCount(),
                    row == null || row.getBookedSeats() == null ? 0 : row.getBookedSeats(),
                    row == null ? BigDecimal.ZERO : row.getRevenue());
        }).toList();
    }
}
