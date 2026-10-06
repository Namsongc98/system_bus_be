package com.ticket_system.booking_ticket.repository;

import com.ticket_system.booking_ticket.repository.UserRepository.BookingAccount;
import com.ticket_system.common.Enum.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Lead review 1.3 L24: the JPQL projection behind BookingAccountGuard (B33) against real MySQL. The schema
 * is built by manage-revenue-ticket's Flyway migrations (the schema owner) and booking_ticket's entities
 * are validated against it, as at startup with ddl-auto=validate. Requires a running Docker daemon.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=true"
})
class UserRepositoryBookingAccountTest {

    @Container
    @ServiceConnection
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0");

    // manage-revenue-ticket owns the schema. Located from this class's output dir
    // (booking_ticket/target/test-classes), so the test does not depend on the working directory.
    @DynamicPropertySource
    static void migrations(DynamicPropertyRegistry registry) throws URISyntaxException {
        Path testClasses = Path.of(UserRepositoryBookingAccountTest.class.getProtectionDomain()
                .getCodeSource().getLocation().toURI());
        Path migrations = testClasses.getParent().getParent().getParent()
                .resolve("manage-revenue-ticket/src/main/resources/db/migration");
        if (!Files.isDirectory(migrations)) {
            throw new IllegalStateException("Flyway migrations not found at " + migrations);
        }
        registry.add("spring.flyway.locations", () -> "filesystem:" + migrations);
    }

    // Only the JPA layer of this module: BookingTicketApplication would also pull in Feign, Kafka and Redis.
    @SpringBootConfiguration
    @EntityScan(basePackages = "com.ticket_system.booking_ticket.entity")
    @EnableJpaRepositories(basePackageClasses = UserRepository.class)
    static class JpaOnly {
    }

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private UserRepository userRepository;

    @Test
    void activeCustomerIsReadWithEmailRoleAndActiveFlag() {
        long id = insertUser("c@test.vn", "CUSTOMER", 1);

        BookingAccount account = userRepository.findBookingAccountById(id).orElseThrow();

        assertThat(account.getEmail()).isEqualTo("c@test.vn");
        assertThat(account.getRole()).isEqualTo(UserRole.CUSTOMER);
        assertThat(account.getIsActive()).isTrue();
    }

    @Test
    void lockedAccountAndOtherRolesComeBackAsStored() {
        long locked = insertUser("locked@test.vn", "CUSTOMER", 0);
        long driver = insertUser("d@test.vn", "DRIVER", 1);

        assertThat(userRepository.findBookingAccountById(locked).orElseThrow().getIsActive()).isFalse();
        assertThat(userRepository.findBookingAccountById(driver).orElseThrow().getRole()).isEqualTo(UserRole.DRIVER);
    }

    @Test
    void unknownIdIsEmpty() {
        Optional<BookingAccount> account = userRepository.findBookingAccountById(Long.MAX_VALUE);

        assertThat(account).isEmpty();
    }

    private long insertUser(String email, String role, int active) {
        jdbc.update("INSERT INTO users (email, password, role, is_active) VALUES (?, 'x', ?, ?)", email, role, active);
        return jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }
}
