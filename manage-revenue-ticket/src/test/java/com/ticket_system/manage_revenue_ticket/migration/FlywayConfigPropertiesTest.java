package com.ticket_system.manage_revenue_ticket.migration;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.PropertiesLoaderUtils;

import java.io.IOException;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 0.3 (L5): the baseline settings live once, in application.properties, and the prod profile keeps them.
 * Loads the two files in the order Spring applies them (prod on top of the default). No Docker needed.
 */
class FlywayConfigPropertiesTest {

    @Test
    void defaultProfileBaselinesOldDbAndForbidsClean() throws IOException {
        Properties app = load("application.properties");
        assertFlywaySafety(app);
        // Without JPA_DDL_AUTO set, Hibernate only validates (the env files set validate too, L9)
        assertThat(app.getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("${JPA_DDL_AUTO:validate}");
    }

    @Test
    void prodProfileKeepsTheSameSettingsAndValidates() throws IOException {
        Properties prod = load("application-prod.properties");
        assertThat(prod).doesNotContainKeys(
                "spring.flyway.baseline-on-migrate", "spring.flyway.baseline-version", "spring.flyway.clean-disabled");

        Properties effective = load("application.properties");
        effective.putAll(prod);
        assertFlywaySafety(effective);
        assertThat(effective.getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("validate");
    }

    private static void assertFlywaySafety(Properties props) {
        assertThat(props.getProperty("spring.flyway.baseline-on-migrate")).isEqualTo("true");
        assertThat(props.getProperty("spring.flyway.baseline-version")).isEqualTo("1");
        assertThat(props.getProperty("spring.flyway.clean-disabled")).isEqualTo("true");
    }

    private static Properties load(String file) throws IOException {
        return PropertiesLoaderUtils.loadProperties(new ClassPathResource(file));
    }
}
