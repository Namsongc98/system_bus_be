package com.ticket_system.manage_revenue_ticket.service;

import com.ticket_system.common.Dto.request.TicketRequestDto;
import com.ticket_system.common.exception.ConflictException;
import com.ticket_system.manage_revenue_ticket.Dto.request.BusRequest;
import com.ticket_system.manage_revenue_ticket.Dto.request.TripRequest;
import com.ticket_system.manage_revenue_ticket.Enum.BusStatus;
import com.ticket_system.manage_revenue_ticket.Enum.TripStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Lead review 1.3 (backend review H1, H4): two admins acting at the same moment on real MySQL. Each
 * call runs in its own transaction (the test itself is not transactional), started together.
 * Requires a running Docker daemon.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@Import({TripService.class, TicketService.class, UserService.class, AuthService.class, BusService.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=true"
})
class TripConcurrencyTest {

    private static final LocalDateTime DEP = LocalDateTime.of(2031, 3, 1, 8, 0);

    @Container
    @ServiceConnection
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0");

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private TripService tripService;

    @Autowired
    private TicketService ticketService;

    @Autowired
    private UserService userService;

    @Autowired
    private BusService busService;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private long route;
    private long bus;
    private long driverA;
    private long driverB;
    private long customer;
    private long seller;

    @BeforeEach
    void seed() {
        route = insert("INSERT INTO routes (route_name, status) VALUES ('HN - HP', 'ACTIVE')");
        bus = insert("INSERT INTO buses (plate_number, capacity) VALUES ('29A-30001', 40)");
        driverA = insert("INSERT INTO users (email, password, role) VALUES ('da@test.vn', 'x', 'DRIVER')");
        driverB = insert("INSERT INTO users (email, password, role) VALUES ('db@test.vn', 'x', 'DRIVER')");
        customer = insert("INSERT INTO users (email, password, role) VALUES ('c@test.vn', 'x', 'CUSTOMER')");
        seller = insert("INSERT INTO users (email, password, role) VALUES ('s@test.vn', 'x', 'COLLECTOR')");
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM tickets");
        jdbc.update("DELETE FROM profiles");
        jdbc.update("DELETE FROM trips");
        jdbc.update("DELETE FROM buses");
        jdbc.update("DELETE FROM routes");
        jdbc.update("DELETE FROM users");
    }

    @RepeatedTest(5)
    void twoAdminsBookingTheSameBusSlotOnlyOneWins() throws Exception {
        // Different drivers, same bus, overlapping hours: only the bus lock + fresh overlap read decide.
        List<Outcome> outcomes = race(
                () -> tripService.create(request(driverA, DEP, DEP.plusHours(3))),
                () -> tripService.create(request(driverB, DEP.plusHours(1), DEP.plusHours(4))));

        assertThat(outcomes).containsExactlyInAnyOrder(Outcome.OK, Outcome.CONFLICT);
        assertThat(count("SELECT COUNT(*) FROM trips WHERE bus_id = ?", bus)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT status FROM buses WHERE id = ?", String.class, bus)).isEqualTo("IN_USE");
    }

    @RepeatedTest(5)
    void cancellingATripWhileATicketIsSoldNeverLeavesALiveTicket() throws Exception {
        long trip = tripService.create(request(driverA, DEP, DEP.plusHours(3))).id();

        List<Outcome> outcomes = race(
                () -> tripService.changeStatus(trip, TripStatus.CANCELLED),
                () -> ticketService.createTicket(ticket(trip, 1)));

        assertThat(outcomes.get(0)).isEqualTo(Outcome.OK);
        assertThat(jdbc.queryForObject("SELECT status FROM trips WHERE id = ?", String.class, trip))
                .isEqualTo("CANCELLED");
        // Either the sale went first and was cancelled with the trip, or it came second and was refused.
        assertThat(count("SELECT COUNT(*) FROM tickets WHERE trip_id = ? AND status_ticket <> 'CANCELLED'", trip))
                .isZero();
    }

    @RepeatedTest(5)
    void editingATicketWhileItsTripIsCancelledNeverRevivesIt() throws Exception {
        // Lead review 1.3 L13.
        long trip = tripService.create(request(driverA, DEP, DEP.plusHours(3))).id();
        long ticketId = ticketService.createTicket(ticket(trip, 1)).getId();

        List<Outcome> outcomes = race(
                () -> tripService.changeStatus(trip, TripStatus.CANCELLED),
                () -> ticketService.updateTicket(ticketId, ticket(trip, 2)));

        assertThat(outcomes.get(0)).isEqualTo(Outcome.OK);
        assertThat(count("SELECT COUNT(*) FROM tickets WHERE trip_id = ? AND status_ticket <> 'CANCELLED'", trip))
                .isZero();
    }

    @RepeatedTest(5)
    void lockingADriverWhileATripIsCreatedForThemNeverLeavesBoth() throws Exception {
        // Lead review 1.3 L14: either the trip goes first and the lock is refused (409), or the lock
        // goes first and the trip is refused because the driver is locked (409).
        List<Outcome> outcomes = race(
                () -> userService.setActive(driverA, false, seller),
                () -> tripService.create(request(driverA, DEP, DEP.plusHours(3))));

        assertThat(outcomes).containsExactlyInAnyOrder(Outcome.OK, Outcome.CONFLICT);
        boolean locked = Boolean.FALSE.equals(
                jdbc.queryForObject("SELECT is_active FROM users WHERE id = ?", Boolean.class, driverA));
        long trips = count("SELECT COUNT(*) FROM trips WHERE driver_id = ? AND status = 'SCHEDULED'", driverA);
        assertThat(locked && trips > 0).isFalse();
        // driverStatus matches the trips (not overwritten by a stale copy).
        String driverStatus = jdbc.queryForObject("SELECT driver_status FROM users WHERE id = ?", String.class, driverA);
        assertThat(driverStatus).isEqualTo(trips > 0 ? "ACTIVE" : null);
    }

    @RepeatedTest(5)
    void cancellingATicketWhileItIsEditedNeverRevivesIt() throws Exception {
        // Lead review 1.3 L20 (B36 a): both lock the trip first, so the edit either runs before the
        // cancel (and is then cancelled) or after it (and is refused).
        long trip = tripService.create(request(driverA, DEP, DEP.plusHours(3))).id();
        long ticketId = ticketService.createTicket(ticket(trip, 1)).getId();

        List<Outcome> outcomes = race(
                () -> ticketService.cancelTicket(ticketId),
                () -> ticketService.updateTicket(ticketId, ticket(trip, 2)));

        assertThat(outcomes.get(0)).isEqualTo(Outcome.OK);
        assertThat(jdbc.queryForObject("SELECT status_ticket FROM tickets WHERE id = ?", String.class, ticketId))
                .isEqualTo("CANCELLED");
    }

    @RepeatedTest(5)
    void shrinkingABusWhileASeatIsSoldNeverLeavesASeatOutsideTheBus() throws Exception {
        // B36 b: the bus update locks the bus's unfinished trips first, so the sale of seat 2 and the
        // shrink to 1 seat cannot both succeed.
        long trip = tripService.create(request(driverA, DEP, DEP.plusHours(3))).id();
        BusRequest shrink = new BusRequest();
        shrink.setPlateNumber("29A-30001");
        shrink.setCapacity(1);
        shrink.setStatus(BusStatus.IN_USE);

        List<Outcome> outcomes = race(
                () -> busService.update(bus, shrink),
                () -> ticketService.createTicket(ticket(trip, 2)));

        assertThat(outcomes).containsOnlyOnce(Outcome.OK);
        int capacity = jdbc.queryForObject("SELECT capacity FROM buses WHERE id = ?", Integer.class, bus);
        long beyond = count("SELECT COUNT(*) FROM tickets WHERE status_ticket <> 'CANCELLED' AND seat_number > "
                + capacity + " AND trip_id = ?", trip);
        assertThat(beyond).isZero();
    }

    @RepeatedTest(10)
    void shrinkingABusWhileOneOfItsTripsIsRescheduledDoesNotDeadlock() throws Exception {
        // Lead review 1.3 L34: TripService.update holds the trip row and, at commit, rewrites its
        // (bus_id, departure_time) index entry; the bus update must not hold that index entry while it
        // waits for the trip row. Both calls touch different data, so both must succeed.
        long first = tripService.create(request(driverA, DEP, DEP.plusHours(3))).id();
        tripService.create(request(driverB, DEP.plusDays(1), DEP.plusDays(1).plusHours(3)));
        BusRequest shrink = new BusRequest();
        shrink.setPlateNumber("29A-30001");
        shrink.setCapacity(39);
        shrink.setStatus(BusStatus.IN_USE);

        long otherBus = insert("INSERT INTO buses (plate_number, capacity) VALUES ('29A-30002', 40)");
        TripRequest move = request(driverA, DEP.plusHours(1), DEP.plusHours(4));
        move.setBusId(otherBus);

        List<Outcome> outcomes = race(
                () -> busService.update(bus, shrink),
                () -> tripService.update(first, move));

        assertThat(outcomes).containsOnly(Outcome.OK);
        assertThat(jdbc.queryForObject("SELECT capacity FROM buses WHERE id = ?", Integer.class, bus)).isEqualTo(39);
    }

    @RepeatedTest(3)
    void busUpdateWaitingOnATripThatIsMovedAwayDoesNotDeadlock() throws Exception {
        // L34, deterministic order: A holds the trip row (as TripService.update does), the bus update then
        // blocks on that row, and A moves the trip to another bus — rewriting its (bus_id, ...) index entries.
        // A locking scan of the bus index would hold those entries while waiting → deadlock. By primary key
        // it does not.
        long first = tripService.create(request(driverA, DEP, DEP.plusHours(3))).id();
        long otherBus = insert("INSERT INTO buses (plate_number, capacity) VALUES ('29A-30003', 40)");
        BusRequest shrink = new BusRequest();
        shrink.setPlateNumber("29A-30001");
        shrink.setCapacity(39);
        shrink.setStatus(BusStatus.IN_USE);
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        CountDownLatch rowLocked = new CountDownLatch(1);
        CountDownLatch busUpdateWaiting = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<Outcome> holder = pool.submit(() -> run(() -> tx.execute(status -> {
                jdbc.queryForObject("SELECT id FROM trips WHERE id = ? FOR UPDATE", Long.class, first);
                rowLocked.countDown();
                await(busUpdateWaiting);
                jdbc.update("UPDATE trips SET bus_id = ?, departure_time = ? WHERE id = ?",
                        otherBus, DEP.plusHours(1), first);
                return null;
            })));
            await(rowLocked);
            Future<Outcome> busUpdate = pool.submit(() -> run(() -> busService.update(bus, shrink)));
            // The test user cannot read information_schema.innodb_trx: give the update time to reach the
            // trip row lock (well under the 5 s lock wait timeout) and check that it is still waiting.
            Thread.sleep(1000);
            assertThat(busUpdate.isDone()).as("bus update should be blocked on the trip row").isFalse();
            busUpdateWaiting.countDown();

            assertThat(List.of(holder.get(30, TimeUnit.SECONDS), busUpdate.get(30, TimeUnit.SECONDS)))
                    .containsOnly(Outcome.OK);
        } finally {
            pool.shutdownNow();
        }
        assertThat(jdbc.queryForObject("SELECT capacity FROM buses WHERE id = ?", Integer.class, bus)).isEqualTo(39);
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) {
                throw new AssertionError("timed out waiting for the other transaction");
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new AssertionError(ex);
        }
    }

    private static Outcome run(Callable<?> action) throws Exception {
        try {
            action.call();
            return Outcome.OK;
        } catch (ConflictException ex) {
            return Outcome.CONFLICT;
        } catch (PessimisticLockingFailureException ex) {
            return Outcome.LOCK_FAILURE;
        }
    }

    // REJECTED: refused by validation (400), e.g. a seat number above the bus capacity.
    // LOCK_FAILURE: deadlock / lock wait timeout (409 "thử lại" through GlobalExceptionHandler).
    private enum Outcome { OK, CONFLICT, REJECTED, LOCK_FAILURE }

    private static List<Outcome> race(Callable<?> first, Callable<?> second) throws Exception {
        CyclicBarrier start = new CyclicBarrier(2);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<Outcome> a = pool.submit(() -> run(start, first));
            Future<Outcome> b = pool.submit(() -> run(start, second));
            return List.of(a.get(30, TimeUnit.SECONDS), b.get(30, TimeUnit.SECONDS));
        } finally {
            pool.shutdownNow();
        }
    }

    private static Outcome run(CyclicBarrier start, Callable<?> action) throws Exception {
        start.await(10, TimeUnit.SECONDS);
        try {
            action.call();
            return Outcome.OK;
        } catch (ConflictException ex) {
            return Outcome.CONFLICT;
        } catch (IllegalArgumentException ex) {
            return Outcome.REJECTED;
        } catch (PessimisticLockingFailureException ex) {
            return Outcome.LOCK_FAILURE;
        }
    }

    private TripRequest request(long driverId, LocalDateTime departure, LocalDateTime arrival) {
        TripRequest request = new TripRequest();
        request.setRouteId(route);
        request.setBusId(bus);
        request.setDriverId(driverId);
        request.setDepartureTime(departure);
        request.setArrivalTime(arrival);
        return request;
    }

    private TicketRequestDto ticket(long tripId, int seat) {
        TicketRequestDto dto = new TicketRequestDto();
        dto.setTripId(tripId);
        dto.setCustomerId(customer);
        dto.setSellerId(seller);
        dto.setSeatNumber(seat);
        dto.setPrice(new BigDecimal("150000"));
        return dto;
    }

    private long count(String sql, long id) {
        return jdbc.queryForObject(sql, Long.class, id);
    }

    private long insert(String sql) {
        jdbc.update(sql);
        return jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }
}
