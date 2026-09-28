package com.ticket_system.manage_revenue_ticket;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Real actuator endpoints with the full chain (Spring Security + AuthInterceptor
 * via WebConfig), using the prod actuator settings Kong relies on. Pins what
 * GatewaySecurityChainTest can only assume: AuthInterceptor does not apply to
 * Boot's actuator handler mapping. No Redis here, so readiness (db + redis) may
 * be DOWN — the assertion is that it is reachable, not that it is UP.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@TestPropertySource(properties = {
        "jwt.secret=01234567890123456789012345678901",
        "spring.kafka.listener.auto-startup=false",
        "management.endpoints.web.exposure.include=health,info,metrics",
        "management.endpoint.health.show-details=when_authorized",
        "management.endpoint.health.roles=ADMIN",
        "management.endpoint.health.probes.enabled=true",
        "management.endpoint.health.group.readiness.include=readinessState,db,redis"
})
class ActuatorExposureTest {

    @Container
    @ServiceConnection
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0");

    @Autowired
    private MockMvc mockMvc;

    @Test
    void livenessIsUpWithoutToken() throws Exception {
        mockMvc.perform(get("/actuator/health/liveness"))
                .andExpect(status().isOk());
    }

    @Test
    void kongProbePathsAreNotBlockedWithoutToken() throws Exception {
        for (String path : new String[]{"/actuator/health", "/actuator/health/readiness"}) {
            int status = mockMvc.perform(get(path)).andReturn().getResponse().getStatus();

            assertThat(status).as(path).isIn(200, 503);
        }
    }

    @Test
    void metricsStillNeedToken() throws Exception {
        mockMvc.perform(get("/actuator/metrics"))
                .andExpect(status().isUnauthorized());
    }
}
