package com.ticket_system.manage_revenue_ticket.controller;

import com.ticket_system.manage_revenue_ticket.interceptor.AccountStatusLookup.AccountState;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticket_system.common.Dto.response.PageResponse;
import com.ticket_system.common.Enum.UserRole;
import com.ticket_system.common.exception.GlobalExceptionHandler;
import com.ticket_system.common.util.JwtUtil;
import com.ticket_system.manage_revenue_ticket.interceptor.AuthInterceptor;
import com.ticket_system.manage_revenue_ticket.service.BusService;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Drives BusController through the real AuthInterceptor with real tokens, so
 * this covers what a reflection check on @RoleRequired cannot: the status code
 * a caller actually receives, on every endpoint.
 */
class BusControllerAuthTest {
    private static final String SECRET = "01234567890123456789012345678901";
    private static final String VALID_BODY = "{\"plateNumber\":\"51A-00001\",\"capacity\":45,\"status\":\"AVAILABLE\"}";

    private final JwtUtil jwtUtil = new JwtUtil(SECRET);
    private final BusService busService = mock(BusService.class);
    private final MockMvc mockMvc = MockMvcBuilders
            .standaloneSetup(new BusController(busService))
            .addInterceptors(new AuthInterceptor(jwtUtil, new ObjectMapper(), userId -> AccountState.active(null)))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

    static Stream<Arguments> endpoints() {
        return Stream.of(
                Arguments.of("GET list", (Supplier<MockHttpServletRequestBuilder>) () -> get("/api/bus"), 200),
                Arguments.of("GET one", (Supplier<MockHttpServletRequestBuilder>) () -> get("/api/bus/1"), 200),
                Arguments.of("POST", (Supplier<MockHttpServletRequestBuilder>) () -> post("/api/bus")
                        .contentType("application/json").content(VALID_BODY), 201),
                Arguments.of("PUT", (Supplier<MockHttpServletRequestBuilder>) () -> put("/api/bus/1")
                        .contentType("application/json").content(VALID_BODY), 200),
                Arguments.of("DELETE", (Supplier<MockHttpServletRequestBuilder>) () -> delete("/api/bus/1"), 200)
        );
    }

    static Stream<Arguments> endpointsForEachNonAdminRole() {
        return endpoints().flatMap(endpoint -> Stream.of(UserRole.values())
                .filter(role -> role != UserRole.ADMIN)
                .map(role -> Arguments.of(endpoint.get()[0], endpoint.get()[1], role)));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("endpoints")
    void rejectsMissingToken(String name, Supplier<MockHttpServletRequestBuilder> request, int ignored) throws Exception {
        mockMvc.perform(request.get()).andExpect(status().isUnauthorized());

        verifyNoInteractions(busService);
    }

    @ParameterizedTest(name = "{0} as {2}")
    @MethodSource("endpointsForEachNonAdminRole")
    void rejectsNonAdminToken(String name, Supplier<MockHttpServletRequestBuilder> request, UserRole role)
            throws Exception {
        mockMvc.perform(request.get().header("Authorization", bearer(role)))
                .andExpect(status().isForbidden());

        verifyNoInteractions(busService);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("endpoints")
    void allowsAdminToken(String name, Supplier<MockHttpServletRequestBuilder> request, int expected) throws Exception {
        when(busService.getBuses(any(), anyInt(), anyInt()))
                .thenReturn(new PageResponse<>(List.of(), 0, 12, 0, 0));

        mockMvc.perform(request.get().header("Authorization", bearer(UserRole.ADMIN)))
                .andExpect(status().is(expected));
    }

    private String bearer(UserRole role) {
        return "Bearer " + jwtUtil.generateAccessToken(1L, role);
    }
}
