package com.ticket_system.manage_revenue_ticket.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.ticket_system.common.Enum.UserRole;
import com.ticket_system.common.annotation.RoleRequired;
import com.ticket_system.manage_revenue_ticket.Dto.response.AdminDashboardResponse;
import com.ticket_system.manage_revenue_ticket.Dto.response.RevenueTrendResponse;
import com.ticket_system.manage_revenue_ticket.exception.InvalidDashboardPeriodException;
import com.ticket_system.manage_revenue_ticket.service.AdminDashboardService;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminDashboardControllerTest {
    private final AdminDashboardService service = mock(AdminDashboardService.class);
    private final MockMvc mockMvc = MockMvcBuilders
            .standaloneSetup(new AdminDashboardController(service))
            .setMessageConverters(new MappingJackson2HttpMessageConverter(
                    new ObjectMapper()
                            .findAndRegisterModules()
                            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            ))
            .build();

    @Test
    void controllerRequiresAdminRole() {
        RoleRequired annotation = AdminDashboardController.class.getAnnotation(RoleRequired.class);

        assertThat(annotation).isNotNull();
        assertThat(annotation.value()).containsExactly(UserRole.ADMIN);
        assertThat(methodRole("getDashboard").value()).containsExactly(UserRole.ADMIN);
        assertThat(methodRole("getRevenueTrend", String.class).value())
                .containsExactly(UserRole.ADMIN);
    }

    @Test
    void getDashboardReturnsWrappedResponse() throws Exception {
        when(service.getDashboard()).thenReturn(new AdminDashboardResponse(
                new AdminDashboardResponse.Period(
                        LocalDate.of(2026, 6, 1),
                        LocalDate.of(2026, 6, 30)
                ),
                new AdminDashboardResponse.Kpis(
                        BigDecimal.TEN,
                        null,
                        0,
                        null,
                        0,
                        null,
                        0,
                        null
                ),
                List.of(),
                List.of(),
                List.of()
        ));

        mockMvc.perform(get("/api/admin/dashboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.message").value("Dashboard fetched"))
                .andExpect(jsonPath("$.data.period.startDate").value("2026-06-01"))
                .andExpect(jsonPath("$.data.kpis.totalRevenue").value(10));
    }

    @Test
    void getRevenueReturnsRequestedPeriodData() throws Exception {
        when(service.getRevenueTrend("weekly")).thenReturn(List.of(
                new RevenueTrendResponse(
                        "08 Jun",
                        LocalDate.of(2026, 6, 8),
                        LocalDate.of(2026, 6, 14),
                        BigDecimal.ONE
                )
        ));

        mockMvc.perform(get("/api/admin/revenue").param("period", "weekly"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].label").value("08 Jun"))
                .andExpect(jsonPath("$.data[0].amount").value(1));
    }

    @Test
    void getRevenueRejectsUnsupportedPeriod() throws Exception {
        when(service.getRevenueTrend("daily")).thenThrow(new InvalidDashboardPeriodException());

        mockMvc.perform(get("/api/admin/revenue").param("period", "daily"))
                .andExpect(status().isBadRequest());
    }

    private RoleRequired methodRole(String name, Class<?>... parameterTypes) {
        try {
            return AdminDashboardController.class
                    .getMethod(name, parameterTypes)
                    .getAnnotation(RoleRequired.class);
        } catch (NoSuchMethodException exception) {
            throw new AssertionError(exception);
        }
    }
}
