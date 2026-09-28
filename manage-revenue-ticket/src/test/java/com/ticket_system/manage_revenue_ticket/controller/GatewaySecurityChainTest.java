package com.ticket_system.manage_revenue_ticket.controller;

import com.ticket_system.common.exception.CustomAccessDeniedHandler;
import com.ticket_system.common.exception.CustomAuthEntryPoint;
import com.ticket_system.common.filter.JwtAuthFilter;
import com.ticket_system.common.security.SecurityConfig;
import com.ticket_system.common.util.JwtUtil;
import com.ticket_system.manage_revenue_ticket.config.WebConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Pins the Spring Security rules Kong depends on. Actuator is not part of the
 * WebMvcTest slice, so a stub stands in for /actuator/health. The real endpoint
 * is served by Boot's own actuator handler mapping, which WebConfig's
 * interceptor registry does not touch, so WebConfig (AuthInterceptor) is
 * excluded here on purpose.
 */
@WebMvcTest(
        controllers = GatewaySecurityChainTest.StubController.class,
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = WebConfig.class))
@Import({
        GatewaySecurityChainTest.StubController.class,
        SecurityConfig.class,
        JwtAuthFilter.class,
        JwtUtil.class,
        CustomAuthEntryPoint.class,
        CustomAccessDeniedHandler.class
})
@TestPropertySource(properties = "jwt.secret=01234567890123456789012345678901")
class GatewaySecurityChainTest {

    private static final String FE_ORIGIN = "http://localhost:5173";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void healthIsReachableWithoutTokenForKongProbe() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/actuator/health/liveness"))
                .andExpect(status().isOk());
        // Path Kong probes (Infrastructure/kong/kong.yml)
        mockMvc.perform(get("/actuator/health/readiness"))
                .andExpect(status().isOk());
    }

    @Test
    void otherActuatorEndpointsStillNeedToken() throws Exception {
        mockMvc.perform(get("/actuator/metrics"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void corsPreflightAllowsPatchFromFrontend() throws Exception {
        mockMvc.perform(options("/api/trip/1/status")
                        .header("Origin", FE_ORIGIN)
                        .header("Access-Control-Request-Method", "PATCH"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", FE_ORIGIN));
    }

    @RestController
    static class StubController {
        @GetMapping({"/actuator/health", "/actuator/health/liveness", "/actuator/health/readiness",
                "/actuator/metrics"})
        String up() {
            return "{\"status\":\"UP\"}";
        }
    }
}
