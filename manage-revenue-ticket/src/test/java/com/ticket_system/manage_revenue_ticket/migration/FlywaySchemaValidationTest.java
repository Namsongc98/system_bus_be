package com.ticket_system.manage_revenue_ticket.migration;

import com.ticket_system.manage_revenue_ticket.entity.Buses;
import com.ticket_system.manage_revenue_ticket.repository.BusesRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Proves V1__baseline.sql builds a schema that Hibernate {@code validate} accepts for all
 * 13 entities. The context only starts if Flyway migrated and validation passed.
 * Requires a running Docker daemon.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=true"
})
class FlywaySchemaValidationTest {

    @Container
    @ServiceConnection
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0");

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private BusesRepository busesRepository;

    @Test
    void flywayAppliesV1AndHibernateValidatePasses() {
        Integer migrations = jdbc.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE version IS NOT NULL", Integer.class);
        Integer successfulV1 = jdbc.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE version = '1' AND success = 1", Integer.class);

        assertThat(migrations).isEqualTo(1);
        assertThat(successfulV1).isEqualTo(1);
    }

    @Test
    void duplicatePlateNumberIsRejectedByUniqueConstraint() {
        busesRepository.saveAndFlush(bus("51A-00001"));

        // Not builder(): @Builder leaves status null and would fail on NOT NULL instead of UNIQUE.
        assertThatThrownBy(() -> busesRepository.saveAndFlush(bus("51A-00001")))
                .isInstanceOf(DataIntegrityViolationException.class)
                .satisfies(e -> assertThat(((DataIntegrityViolationException) e).getMostSpecificCause().getMessage())
                        .contains("Duplicate entry")
                        .contains("plate_number"));
    }

    @Test
    void declaredForeignKeysAndUniqueConstraintsExist() {
        // Hibernate validate does not check constraints, so assert them directly.
        List<String> foreignKeys = constraintNames("FOREIGN KEY");
        List<String> uniques = constraintNames("UNIQUE");

        assertThat(foreignKeys).contains(
                "trips_fk_route", "trips_fk_bus", "trips_fk_driver",
                "tickets_fk_trip", "tickets_fk_customer", "tickets_fk_seller",
                "revenues_fk_trip", "salaries_fk_user", "fk_profiles_users");
        assertThat(uniques).contains("email_unique", "plate_number");
    }

    private List<String> constraintNames(String type) {
        return jdbc.queryForList(
                "SELECT constraint_name FROM information_schema.table_constraints "
                        + "WHERE constraint_schema = DATABASE() AND constraint_type = ?",
                String.class, type);
    }

    private static Buses bus(String plateNumber) {
        Buses bus = new Buses();
        bus.setPlateNumber(plateNumber);
        bus.setCapacity(40);
        return bus;
    }
}
