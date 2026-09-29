package com.ticket_system.manage_revenue_ticket.repository;

import com.ticket_system.common.Enum.UserRole;
import com.ticket_system.manage_revenue_ticket.Enum.TripStatus;
import com.ticket_system.manage_revenue_ticket.entity.User;
import com.ticket_system.manage_revenue_ticket.projection.RoleCountProjection;
import com.ticket_system.manage_revenue_ticket.projection.UserAuthStateProjection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Task 1.2: the JPQL behind /api/user (entity join with profiles, LIKE escaping, count query),
 * the role counts, the is_active lookup of the auth interceptor and the driver trip check,
 * against real MySQL. Requires a running Docker daemon.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=true"
})
class UserRepositoryQueryTest {

    private static final List<TripStatus> UNFINISHED = List.of(TripStatus.SCHEDULED, TripStatus.ONGOING);

    @Container
    @ServiceConnection
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0");

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TripRepository tripRepository;

    private long admin;
    private long busyDriver;
    private long freeDriver;
    private long lockedCustomer;
    private long noProfileCustomer;

    @BeforeEach
    void seed() {
        admin = user("admin@test.vn", "ADMIN", true);
        busyDriver = user("tai.xe1@test.vn", "DRIVER", true);
        freeDriver = user("tai.xe2@test.vn", "DRIVER", true);
        lockedCustomer = user("khach_a@test.vn", "CUSTOMER", false);
        noProfileCustomer = user("khach%b@test.vn", "CUSTOMER", true);
        profile(admin, "Quản Trị");
        profile(busyDriver, "Nguyễn Văn Lái");
        profile(freeDriver, "Trần Văn Rảnh");
        profile(lockedCustomer, "Lê Thị Khoá");

        long route = insert("INSERT INTO routes (route_name) VALUES ('HN - HP')");
        long bus = insert("INSERT INTO buses (plate_number, capacity) VALUES ('29A-10001', 40)");
        trip(route, bus, busyDriver, "SCHEDULED");
        trip(route, bus, freeDriver, "COMPLETED");
    }

    @Test
    void searchReturnsEveryUserNewestFirstWithProfileOrNull() {
        Page<UserWithProfile> page = userRepository.search(null, null, PageRequest.of(0, 10));

        assertThat(page.getTotalElements()).isEqualTo(5);
        assertThat(page.getContent()).extracting(row -> row.user().getId())
                .containsExactly(noProfileCustomer, lockedCustomer, freeDriver, busyDriver, admin);
        assertThat(page.getContent().get(0).profile()).isNull();
        assertThat(page.getContent().get(1).profile().getFullName()).isEqualTo("Lê Thị Khoá");
    }

    @Test
    void searchFiltersByRoleAndPagesWithMatchingCount() {
        Page<UserWithProfile> page = userRepository.search(UserRole.DRIVER, null, PageRequest.of(0, 1));

        assertThat(page.getTotalElements()).isEqualTo(2);
        assertThat(page.getTotalPages()).isEqualTo(2);
        assertThat(page.getContent()).extracting(row -> row.user().getId()).containsExactly(freeDriver);
    }

    @Test
    void searchMatchesKeywordOnEmailOrFullNameCaseInsensitively() {
        assertThat(ids(userRepository.search(null, "%văn%", PageRequest.of(0, 10))))
                .containsExactlyInAnyOrder(busyDriver, freeDriver);
        assertThat(ids(userRepository.search(null, "%admin@%", PageRequest.of(0, 10))))
                .containsExactly(admin);
        assertThat(ids(userRepository.search(UserRole.CUSTOMER, "%lê thị%", PageRequest.of(0, 10))))
                .containsExactly(lockedCustomer);
    }

    @Test
    void escapedWildcardsMatchLiterally() {
        // "\%" must match a literal % (only khach%b), "\_" a literal _ (only khach_a).
        assertThat(ids(userRepository.search(null, "%khach\\%%", PageRequest.of(0, 10))))
                .containsExactly(noProfileCustomer);
        assertThat(ids(userRepository.search(null, "%khach\\_%", PageRequest.of(0, 10))))
                .containsExactly(lockedCustomer);
    }

    @Test
    void countsGroupByRole() {
        Map<UserRole, Long> counts = userRepository.countGroupByRole().stream()
                .collect(Collectors.toMap(RoleCountProjection::getRole, RoleCountProjection::getTotal));

        assertThat(counts).containsExactlyInAnyOrderEntriesOf(Map.of(
                UserRole.ADMIN, 1L, UserRole.DRIVER, 2L, UserRole.CUSTOMER, 2L));
    }

    @Test
    void authStateLookupReturnsActiveFlagAndRole() {
        UserAuthStateProjection adminState = userRepository.findAuthStateById(admin).orElseThrow();
        assertThat(adminState.getIsActive()).isTrue();
        assertThat(adminState.getRole()).isEqualTo(UserRole.ADMIN);
        UserAuthStateProjection lockedState = userRepository.findAuthStateById(lockedCustomer).orElseThrow();
        assertThat(lockedState.getIsActive()).isFalse();
        assertThat(lockedState.getRole()).isEqualTo(UserRole.CUSTOMER);
        assertThat(userRepository.findAuthStateById(999_999L)).isEmpty();
    }

    @Test
    void lockActiveAdminsReturnsOnlyActiveAdmins() {
        user("admin2@test.vn", "ADMIN", true);
        user("admin3@test.vn", "ADMIN", false);

        assertThat(userRepository.lockActiveAdmins()).extracting(u -> u.getEmail())
                .containsExactlyInAnyOrder("admin@test.vn", "admin2@test.vn");
    }

    @Test
    void driverUnfinishedTripLookup() {
        long ongoingDriver = user("tai.xe3@test.vn", "DRIVER", true);
        long cancelledDriver = user("tai.xe4@test.vn", "DRIVER", true);
        long route = insert("INSERT INTO routes (route_name) VALUES ('HN - ND')");
        long bus = insert("INSERT INTO buses (plate_number, capacity) VALUES ('29A-10002', 40)");
        trip(route, bus, ongoingDriver, "ONGOING");
        trip(route, bus, cancelledDriver, "CANCELLED");

        assertThat(tripRepository.existsByDriverIdAndStatusIn(busyDriver, UNFINISHED)).isTrue();
        assertThat(tripRepository.existsByDriverIdAndStatusIn(ongoingDriver, UNFINISHED)).isTrue();
        assertThat(tripRepository.existsByDriverIdAndStatusIn(freeDriver, UNFINISHED)).isFalse();
        assertThat(tripRepository.existsByDriverIdAndStatusIn(cancelledDriver, UNFINISHED)).isFalse();
        assertThat(tripRepository.existsByDriverIdAndStatusIn(admin, UNFINISHED)).isFalse();
    }

    @Test
    void duplicateEmailViolationNamesTheEmailUniqueConstraint() {
        // UserService maps a race on create to 409 by finding "email_unique" in the cause chain.
        User duplicate = User.builder().email("admin@test.vn").password("x").role(UserRole.CUSTOMER).isActive(true).build();

        assertThatThrownBy(() -> userRepository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class)
                .satisfies(ex -> assertThat(causeMessages(ex)).contains("email_unique"));
    }

    @Test
    void duplicateProfileViolationDoesNotLookLikeADuplicateEmail() {
        assertThatThrownBy(() -> profile(admin, "Hai Hồ Sơ"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .satisfies(ex -> assertThat(causeMessages(ex)).doesNotContain("email_unique"));
    }

    private static String causeMessages(Throwable ex) {
        StringBuilder messages = new StringBuilder();
        for (Throwable cause = ex; cause != null; cause = cause.getCause()) {
            messages.append(cause.getMessage()).append('\n');
        }
        return messages.toString();
    }

    private static List<Long> ids(Page<UserWithProfile> page) {
        return page.getContent().stream().map(row -> row.user().getId()).toList();
    }

    private long user(String email, String role, boolean active) {
        jdbc.update("INSERT INTO users (email, password, role, is_active) VALUES (?, 'x', ?, ?)", email, role, active);
        return jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    private void profile(long userId, String fullName) {
        jdbc.update("INSERT INTO profiles (user_id, full_name) VALUES (?, ?)", userId, fullName);
    }

    private void trip(long routeId, long busId, long driverId, String status) {
        jdbc.update("INSERT INTO trips (route_id, bus_id, driver_id, departure_time, status) VALUES (?, ?, ?, NOW(), ?)",
                routeId, busId, driverId, status);
    }

    private long insert(String sql) {
        jdbc.update(sql);
        return jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }
}
