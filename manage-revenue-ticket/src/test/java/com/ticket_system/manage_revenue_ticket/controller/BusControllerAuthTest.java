package com.ticket_system.manage_revenue_ticket.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticket_system.common.Enum.UserRole;
import com.ticket_system.common.exception.GlobalExceptionHandler;
import com.ticket_system.common.util.JwtUtil;
import com.ticket_system.manage_revenue_ticket.entity.Buses;
import com.ticket_system.manage_revenue_ticket.interceptor.AuthInterceptor;
import com.ticket_system.manage_revenue_ticket.service.BusService;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Drives BusController through the real AuthInterceptor with real tokens, so
 * this covers what a reflection check on @RoleRequired cannot: the status code
 * a caller actually receives.
 */
class BusControllerAuthTest {
    private static final String SECRET = "01234567890123456789012345678901";

    private final JwtUtil jwtUtil = new JwtUtil(SECRET);
    private final BusService busService = mock(BusService.class);
    private final MockMvc mockMvc = MockMvcBuilders
            .standaloneSetup(new BusController(busService))
            .addInterceptors(new AuthInterceptor(jwtUtil, new ObjectMapper()))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

    @Test
    void rejectsRequestWithoutToken() throws Exception {
        mockMvc.perform(get("/api/bus"))
                .andExpect(status().isUnauthorized());

        verify(busService, never()).getBuses(any());
    }

    @Test
    void rejectsCustomerToken() throws Exception {
        mockMvc.perform(get("/api/bus")
                        .header("Authorization", bearer(UserRole.CUSTOMER)))
                .andExpect(status().isForbidden());

        verify(busService, never()).getBuses(any());
    }

    @Test
    void rejectsCustomerTokenOnBusCreation() throws Exception {
        mockMvc.perform(post("/api/bus")
                        .header("Authorization", bearer(UserRole.CUSTOMER))
                        .contentType("application/json")
                        .content("{\"plateNumber\":\"51A-00001\",\"capacity\":45}"))
                .andExpect(status().isForbidden());

        verify(busService, never()).save(any());
    }

    @Test
    void allowsAdminToken() throws Exception {
        // An explicit PageRequest matters: PageImpl's single-arg constructor
        // defaults to Pageable.unpaged(), whose getPageSize() throws when Jackson
        // serializes it.
        Page<Buses> empty = new PageImpl<>(List.of(), PageRequest.of(0, 10), 0);
        when(busService.getBuses(any())).thenReturn(empty);

        mockMvc.perform(get("/api/bus")
                        .header("Authorization", bearer(UserRole.ADMIN)))
                .andExpect(status().isOk());
    }

    private String bearer(UserRole role) {
        return "Bearer " + jwtUtil.generateAccessToken(1L, role);
    }
}
