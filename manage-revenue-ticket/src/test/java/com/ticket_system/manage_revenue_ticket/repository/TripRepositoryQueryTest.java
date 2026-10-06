package com.ticket_system.manage_revenue_ticket.repository;

import com.ticket_system.manage_revenue_ticket.Enum.TicketStatus;
import com.ticket_system.manage_revenue_ticket.Enum.TripStatus;
import com.ticket_system.manage_revenue_ticket.entity.Trip;
import com.ticket_system.manage_revenue_ticket.projection.TripTicketStatsProjection;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Task 1.3: the queries behind /api/trip against real MySQL — list filters + count query + order,
 * per-trip ticket stats, overlap check, cancel cascade. Requires a running Docker daemon.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=true"
})
class TripRepositoryQueryTest {

    private static final List<TripStatus> UNFINISHED = List.of(TripStatus.SCHEDULED, TripStatus.ONGOING);
    private static final LocalDateTime D1 = LocalDateTime.of(2030, 1, 10, 8, 0);

    @Container
    @ServiceConnection
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0");

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private TripRepository tripRepository;

    @Autowired
    private TicketRepository ticketRepository;

    @Autowired
    private EntityManager entityManager;

    private long driver;
    private long otherDriver;
    private long customer;
    private long routeA;
    private long routeB;
    private long bus;
    private long otherBus;
    private long morning;    // SCHEDULED, routeA, bus, driver, D1 08:00-11:00
    private long afternoon;  // SCHEDULED, routeB, bus, driver, D1 13:00-15:00
    private long done;       // COMPLETED, routeA, otherBus, otherDriver, D1 08:00-11:00
    private long nextMonth;  // CANCELLED, routeA, bus, driver, +1 month

    @BeforeEach
    void seed() {
        driver = insert("INSERT INTO users (email, password, role) VALUES ('d1@test.vn', 'x', 'DRIVER')");
        otherDriver = insert("INSERT INTO users (email, password, role) VALUES ('d2@test.vn', 'x', 'DRIVER')");
        customer = insert("INSERT INTO users (email, password, role) VALUES ('c@test.vn', 'x', 'CUSTOMER')");
        routeA = insert("INSERT INTO routes (route_name) VALUES ('HN - HP')");
        routeB = insert("INSERT INTO routes (route_name) VALUES ('HN - ND')");
        bus = insert("INSERT INTO buses (plate_number, capacity) VALUES ('29A-20001', 40)");
        otherBus = insert("INSERT INTO buses (plate_number, capacity) VALUES ('29A-20002', 40)");
        morning = trip(routeA, bus, driver, D1, D1.plusHours(3), "SCHEDULED");
        afternoon = trip(routeB, bus, driver, D1.withHour(13), D1.withHour(15), "SCHEDULED");
        done = trip(routeA, otherBus, otherDriver, D1, D1.plusHours(3), "COMPLETED");
        nextMonth = trip(routeA, bus, driver, D1.plusMonths(1), D1.plusMonths(1).plusHours(2), "CANCELLED");

        ticket(morning, 1, "SUCCESS", "150000.00");
        ticket(morning, 2, "PENDING", "150000.00");
        ticket(morning, 3, "CANCELLED", "150000.00");
        ticket(done, 1, "SUCCESS", "200000.00");
    }

    @Test
    void searchWithoutFiltersReturnsAllByDepartureThenId() {
        Page<Trip> page = tripRepository.search(null, null, null, null, null, null, PageRequest.of(0, 10));

        assertThat(page.getTotalElements()).isEqualTo(4);
        assertThat(ids(page)).containsExactly(morning, done, afternoon, nextMonth);
        assertThat(page.getContent().get(0).getRoute().getRouteName()).isEqualTo("HN - HP");
    }

    @Test
    void searchCombinesFiltersAndPagesWithMatchingCount() {
        Page<Trip> scheduledOnBus = tripRepository.search(TripStatus.SCHEDULED, null, bus, null, null, null,
                PageRequest.of(0, 1));
        assertThat(scheduledOnBus.getTotalElements()).isEqualTo(2);
        assertThat(scheduledOnBus.getTotalPages()).isEqualTo(2);
        assertThat(ids(scheduledOnBus)).containsExactly(morning);

        assertThat(ids(tripRepository.search(null, routeB, null, null, null, null, PageRequest.of(0, 10))))
                .containsExactly(afternoon);
        assertThat(ids(tripRepository.search(null, null, null, otherDriver, null, null, PageRequest.of(0, 10))))
                .containsExactly(done);
    }

    @Test
    void dateRangeIsFromInclusiveToExclusive() {
        // [D1 08:00, D1 13:00): morning and done start at 08:00, afternoon starts exactly at the upper bound.
        assertThat(ids(tripRepository.search(null, null, null, null, D1, D1.withHour(13), PageRequest.of(0, 10))))
                .containsExactlyInAnyOrder(morning, done);
    }

    @Test
    void ticketStatsCountNonCancelledSeatsAndSuccessRevenue() {
        Map<Long, TripTicketStatsProjection> stats = ticketRepository.findTripTicketStats(List.of(morning, done, afternoon))
                .stream().collect(Collectors.toMap(TripTicketStatsProjection::getTripId, s -> s));

        // ticketCount includes the cancelled seat 3 (a trip with any ticket cannot be deleted, B35 e).
        assertThat(stats.get(morning).getTicketCount()).isEqualTo(3);
        assertThat(stats.get(morning).getBookedSeats()).isEqualTo(2);
        assertThat(stats.get(morning).getRevenue()).isEqualByComparingTo("150000");
        assertThat(stats.get(done).getRevenue()).isEqualByComparingTo("200000");
        assertThat(stats).doesNotContainKey(afternoon);
    }

    @Test
    void overlapIgnoresTouchingEndsFinishedTripsAndTheTripItself() {
        // Overlaps the morning trip.
        assertThat(tripRepository.existsBusOverlap(bus, D1.plusHours(2), D1.plusHours(4), null, UNFINISHED)).isTrue();
        assertThat(tripRepository.existsDriverOverlap(driver, D1.plusHours(2), D1.plusHours(4), null, UNFINISHED)).isTrue();
        // Starts exactly when the morning trip arrives.
        assertThat(tripRepository.existsBusOverlap(bus, D1.plusHours(3), D1.withHour(12), null, UNFINISHED)).isFalse();
        // The morning trip itself, when editing it.
        assertThat(tripRepository.existsBusOverlap(bus, D1, D1.plusHours(3), morning, UNFINISHED)).isFalse();
        // otherBus only has a COMPLETED trip at that time; the cancelled next-month slot is free too.
        assertThat(tripRepository.existsBusOverlap(otherBus, D1, D1.plusHours(3), null, UNFINISHED)).isFalse();
        assertThat(tripRepository.existsBusOverlap(bus, D1.plusMonths(1), D1.plusMonths(1).plusHours(2), null, UNFINISHED))
                .isFalse();
    }

    @Test
    void oneSidedRangesKeepFromInclusiveAndToExclusive() {
        assertThat(ids(tripRepository.search(null, null, null, null, D1.withHour(13), null, PageRequest.of(0, 10))))
                .containsExactly(afternoon, nextMonth);
        assertThat(ids(tripRepository.search(null, null, null, null, null, D1.withHour(13), PageRequest.of(0, 10))))
                .containsExactlyInAnyOrder(morning, done);
        Page<Trip> beyond = tripRepository.search(null, null, null, null, null, null, PageRequest.of(5, 10));
        assertThat(beyond.getContent()).isEmpty();
        assertThat(beyond.getTotalElements()).isEqualTo(4);
    }

    @Test
    void overlapCoversContainedIdenticalAndStartTouchingIntervals() {
        // morning: D1 08:00-11:00 on bus / driver.
        assertThat(tripRepository.existsBusOverlap(bus, D1.plusHours(1), D1.plusHours(2), null, UNFINISHED)).isTrue();
        assertThat(tripRepository.existsBusOverlap(bus, D1.minusHours(1), D1.plusHours(5), null, UNFINISHED)).isTrue();
        assertThat(tripRepository.existsBusOverlap(bus, D1, D1.plusHours(3), null, UNFINISHED)).isTrue();
        // Ends exactly when the morning trip departs.
        assertThat(tripRepository.existsBusOverlap(bus, D1.minusHours(2), D1, null, UNFINISHED)).isFalse();
    }

    @Test
    void driverOverlapMatchesTheBusRules() {
        assertThat(tripRepository.existsDriverOverlap(driver, D1, D1.plusHours(3), null, UNFINISHED)).isTrue();
        assertThat(tripRepository.existsDriverOverlap(driver, D1.plusHours(3), D1.withHour(12), null, UNFINISHED)).isFalse();
        assertThat(tripRepository.existsDriverOverlap(driver, D1, D1.plusHours(3), morning, UNFINISHED)).isFalse();
        // otherDriver only has a COMPLETED trip at that time.
        assertThat(tripRepository.existsDriverOverlap(otherDriver, D1, D1.plusHours(3), null, UNFINISHED)).isFalse();
        jdbc.update("UPDATE trips SET status = 'ONGOING' WHERE id = ?", done);
        assertThat(tripRepository.existsDriverOverlap(otherDriver, D1, D1.plusHours(3), null, UNFINISHED)).isTrue();
    }

    @Test
    void detailedLookupFetchesRouteBusAndDriver() {
        Trip trip = tripRepository.findDetailedById(morning).orElseThrow();
        entityManager.clear();

        // Usable after the persistence context is gone: the associations were fetched with the trip.
        assertThat(trip.getRoute().getRouteName()).isEqualTo("HN - HP");
        assertThat(trip.getBus().getCapacity()).isEqualTo(40);
        assertThat(trip.getDriver().getEmail()).isEqualTo("d1@test.vn");
        assertThat(tripRepository.findDetailedById(999_999L)).isEmpty();
        assertThat(tripRepository.findByIdForUpdate(morning)).isPresent();
    }

    @Test
    void ticketHelpersForCancelAndBusSwap() {
        assertThat(ticketRepository.findMaxActiveSeatNumberByTripId(morning)).isEqualTo(2);
        assertThat(ticketRepository.findMaxActiveSeatNumberByTripId(afternoon)).isNull();
        assertThat(ticketRepository.existsByCustomerIdAndStatusTicketNot(customer, TicketStatus.CANCELLED)).isTrue();

        ticketRepository.cancelActiveTicketsByTripId(morning);
        ticketRepository.cancelActiveTicketsByTripId(done);

        assertThat(ticketRepository.existsByCustomerIdAndStatusTicketNot(customer, TicketStatus.CANCELLED)).isFalse();
    }

    @Test
    void tripWithoutArrivalOccupiesItsDepartureInstant() {
        jdbc.update("UPDATE trips SET arrival_time = NULL WHERE id = ?", afternoon);

        assertThat(tripRepository.existsBusOverlap(bus, D1.withHour(12), D1.withHour(14), null, UNFINISHED)).isTrue();
        assertThat(tripRepository.existsBusOverlap(bus, D1.withHour(13).plusMinutes(1), D1.withHour(14), null, UNFINISHED))
                .isFalse();
    }

    @Test
    void cancelCascadeCancelsOnlyActiveTicketsOfThatTrip() {
        assertThat(ticketRepository.findActiveCustomerIdsByTripId(morning)).containsExactly(customer);

        int cancelled = ticketRepository.cancelActiveTicketsByTripId(morning);
        entityManager.clear();

        assertThat(cancelled).isEqualTo(2);
        assertThat(ticketRepository.countActiveTicketsByTripId(morning)).isZero();
        assertThat(ticketRepository.countActiveTicketsByTripId(done)).isEqualTo(1);
        assertThat(ticketRepository.findAll()).filteredOn(t -> t.getStatusTicket() == TicketStatus.CANCELLED).hasSize(3);
        assertThat(ticketRepository.existsByTripId(afternoon)).isFalse();
        assertThat(ticketRepository.existsByTripId(morning)).isTrue();
    }

    // ─── findRequiredCapacityForBus (B36 b / lead review 1.3 L23) ──────────

    @Test
    void requiredCapacityIgnoresCancelledTicketsFinishedTripsAndOtherBuses() {
        // bus: morning holds seats 1, 2 (seat 3 cancelled); afternoon has no ticket; nextMonth is CANCELLED.
        assertThat(ticketRepository.findRequiredCapacityForBus(bus)).isEqualTo(2);
        // otherBus only has the COMPLETED trip.
        assertThat(ticketRepository.findRequiredCapacityForBus(otherBus)).isZero();
        assertThat(ticketRepository.findRequiredCapacityForBus(-1L)).isZero();
    }

    @Test
    void requiredCapacityIsTheHighestSeatAcrossTripsNotASum() {
        ticket(afternoon, 7, "PENDING", "150000.00");   // gap: seats 1..6 free on this trip
        ticket(nextMonth, 30, "SUCCESS", "150000.00");  // CANCELLED trip: ignored

        assertThat(ticketRepository.findRequiredCapacityForBus(bus)).isEqualTo(7);
    }

    @Test
    void requiredCapacityCountsOngoingTrips() {
        // A trip on the road still holds its seats: ONGOING is as unfinished as SCHEDULED.
        long running = trip(routeA, otherBus, otherDriver, D1.plusDays(2), D1.plusDays(2).plusHours(3), "ONGOING");
        ticket(running, 12, "SUCCESS", "150000.00");

        assertThat(ticketRepository.findRequiredCapacityForBus(otherBus)).isEqualTo(12);
    }

    @Test
    void requiredCapacityCountsTicketsWithoutASeatNumber() {
        // seat_number is nullable (V1): legacy tickets without a seat still occupy the bus.
        long empty = trip(routeB, otherBus, otherDriver, D1.plusDays(1), D1.plusDays(1).plusHours(2), "SCHEDULED");
        jdbc.update("INSERT INTO tickets (trip_id, customer_id, status_ticket, price) VALUES (?, ?, 'PENDING', 1), (?, ?, 'SUCCESS', 1), (?, ?, 'PENDING', 1)",
                empty, customer, empty, customer, empty, customer);

        assertThat(ticketRepository.findRequiredCapacityForBus(otherBus)).isEqualTo(3);

        ticket(empty, 2, "PENDING", "150000.00");
        // 4 tickets sold, highest seat 2: the count wins.
        assertThat(ticketRepository.findRequiredCapacityForBus(otherBus)).isEqualTo(4);
    }

    private static List<Long> ids(Page<Trip> page) {
        return page.getContent().stream().map(Trip::getId).toList();
    }

    private long trip(long routeId, long busId, long driverId, LocalDateTime dep, LocalDateTime arr, String status) {
        jdbc.update("INSERT INTO trips (route_id, bus_id, driver_id, departure_time, arrival_time, status) VALUES (?, ?, ?, ?, ?, ?)",
                routeId, busId, driverId, dep, arr, status);
        return jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    private void ticket(long tripId, int seat, String status, String price) {
        jdbc.update("INSERT INTO tickets (trip_id, customer_id, seat_number, status_ticket, price) VALUES (?, ?, ?, ?, ?)",
                tripId, customer, seat, status, new java.math.BigDecimal(price));
    }

    private long insert(String sql) {
        jdbc.update(sql);
        return jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }
}
