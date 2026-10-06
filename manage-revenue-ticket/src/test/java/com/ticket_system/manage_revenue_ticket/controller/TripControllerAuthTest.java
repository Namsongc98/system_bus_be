package com.ticket_system.manage_revenue_ticket.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticket_system.common.Dto.response.PageResponse;
import com.ticket_system.common.Enum.UserRole;
import com.ticket_system.common.exception.ConflictException;
import com.ticket_system.common.exception.GlobalExceptionHandler;
import com.ticket_system.common.exception.ResourceNotFoundException;
import com.ticket_system.common.util.JwtUtil;
import com.ticket_system.manage_revenue_ticket.Dto.response.TripResponse;
import com.ticket_system.manage_revenue_ticket.Enum.BusStatus;
import com.ticket_system.manage_revenue_ticket.Enum.TripStatus;
import com.ticket_system.manage_revenue_ticket.interceptor.AccountStatusLookup.AccountState;
import com.ticket_system.manage_revenue_ticket.interceptor.AuthInterceptor;
import com.ticket_system.manage_revenue_ticket.service.TripService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Drives TripController through the real AuthInterceptor: ADMIN-only, request validation, 409 mapping. */
class TripControllerAuthTest {
    private static final String TEST_SIGNING_KEY = "0123456789".repeat(4).substring(0, 32);
    private static final String BODY = """
            {"routeId":1,"busId":2,"driverId":3,"departureTime":"2030-01-01T08:00","arrivalTime":"2030-01-01T11:00"}""";
    private static final String STATUS_BODY = """
            {"status":"ONGOING"}""";
    private static final TripResponse TRIP = new TripResponse(5L, TripStatus.SCHEDULED,
            LocalDateTime.of(2030, 1, 1, 8, 0), LocalDateTime.of(2030, 1, 1, 11, 0),
            new TripResponse.RouteSummary(1L, "HN - HP", "Hà Nội", "Hải Phòng", new BigDecimal("120")),
            new TripResponse.BusSummary(2L, "51A-1", 40, BusStatus.IN_USE),
            new TripResponse.DriverSummary(3L, "d@test.vn", "Tài Xế", UserRole.DRIVER),
            0, 0, BigDecimal.ZERO, null, null);

    private final JwtUtil jwtUtil = new JwtUtil(TEST_SIGNING_KEY);
    private final TripService tripService = mock(TripService.class);
    private final MockMvc mockMvc = MockMvcBuilders
            .standaloneSetup(new TripController(tripService))
            .addInterceptors(new AuthInterceptor(jwtUtil, new ObjectMapper(), userId -> AccountState.active(null)))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

    static Stream<Arguments> endpoints() {
        return Stream.of(
                Arguments.of("GET list", (Supplier<MockHttpServletRequestBuilder>) () -> get("/api/trip"), 200),
                Arguments.of("GET one", (Supplier<MockHttpServletRequestBuilder>) () -> get("/api/trip/5"), 200),
                Arguments.of("POST", (Supplier<MockHttpServletRequestBuilder>) () -> post("/api/trip")
                        .contentType("application/json").content(BODY), 201),
                Arguments.of("PUT", (Supplier<MockHttpServletRequestBuilder>) () -> put("/api/trip/5")
                        .contentType("application/json").content(BODY), 200),
                Arguments.of("PATCH status", (Supplier<MockHttpServletRequestBuilder>) () -> patch("/api/trip/5/status")
                        .contentType("application/json").content(STATUS_BODY), 200),
                Arguments.of("DELETE", (Supplier<MockHttpServletRequestBuilder>) () -> delete("/api/trip/5"), 200)
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

        verifyNoInteractions(tripService);
    }

    @ParameterizedTest(name = "{0} as {2}")
    @MethodSource("endpointsForEachNonAdminRole")
    void rejectsNonAdminToken(String name, Supplier<MockHttpServletRequestBuilder> request, UserRole role)
            throws Exception {
        mockMvc.perform(request.get().header("Authorization", bearer(role))).andExpect(status().isForbidden());

        verifyNoInteractions(tripService);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("endpoints")
    void allowsAdminToken(String name, Supplier<MockHttpServletRequestBuilder> request, int expected) throws Exception {
        when(tripService.getTrips(any(), any(), any(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(new PageResponse<>(List.of(TRIP), 0, 10, 1, 1));
        when(tripService.getTrip(anyLong())).thenReturn(TRIP);
        when(tripService.create(any())).thenReturn(TRIP);
        when(tripService.update(anyLong(), any())).thenReturn(TRIP);
        when(tripService.changeStatus(anyLong(), any())).thenReturn(TRIP);

        mockMvc.perform(request.get().header("Authorization", bearer(UserRole.ADMIN)))
                .andExpect(status().is(expected));
    }

    @Test
    void listPassesEveryFilter() throws Exception {
        when(tripService.getTrips(any(), any(), any(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(new PageResponse<>(List.of(TRIP), 0, 100, 1, 1));

        mockMvc.perform(get("/api/trip").header("Authorization", bearer(UserRole.ADMIN))
                        .param("status", "SCHEDULED").param("routeId", "1")
                        .param("from", "2030-01-01T00:00:00").param("to", "2030-02-01T00:00:00")
                        .param("page", "0").param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].route.startPoint").value("Hà Nội"))
                .andExpect(jsonPath("$.data.content[0].driver.fullName").value("Tài Xế"));

        verify(tripService).getTrips(TripStatus.SCHEDULED, 1L, null, null,
                LocalDateTime.of(2030, 1, 1, 0, 0), LocalDateTime.of(2030, 2, 1, 0, 0), 0, 100);
    }

    @Test
    void badInputIsBadRequest() throws Exception {
        mockMvc.perform(get("/api/trip").param("status", "scheduled").header("Authorization", bearer(UserRole.ADMIN)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/trip/abc").header("Authorization", bearer(UserRole.ADMIN)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/trip").header("Authorization", bearer(UserRole.ADMIN))
                        .contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.routeId").exists())
                .andExpect(jsonPath("$.errors.departureTime").exists());
        mockMvc.perform(patch("/api/trip/5/status").header("Authorization", bearer(UserRole.ADMIN))
                        .contentType("application/json").content("{\"status\":\"FLYING\"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(tripService);
    }

    @Test
    void conflictFromServiceIs409WithMessage() throws Exception {
        when(tripService.create(any())).thenThrow(new ConflictException("Xe 51A-1 đã có chuyến khác trong khung giờ này"));

        mockMvc.perform(post("/api/trip").header("Authorization", bearer(UserRole.ADMIN))
                        .contentType("application/json").content(BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Xe 51A-1 đã có chuyến khác trong khung giờ này"));
    }

    // B35 f (lead review 1.3 L18): service refusals on update / status / delete keep their status code.
    @Test
    void writesOnAMissingTripAre404() throws Exception {
        when(tripService.update(eq(404L), any())).thenThrow(new ResourceNotFoundException("Không tìm thấy chuyến 404"));
        when(tripService.changeStatus(eq(404L), any())).thenThrow(new ResourceNotFoundException("Không tìm thấy chuyến 404"));
        doThrow(new ResourceNotFoundException("Không tìm thấy chuyến 404")).when(tripService).delete(404L);

        mockMvc.perform(put("/api/trip/404").header("Authorization", bearer(UserRole.ADMIN))
                        .contentType("application/json").content(BODY))
                .andExpect(status().isNotFound());
        mockMvc.perform(patch("/api/trip/404/status").header("Authorization", bearer(UserRole.ADMIN))
                        .contentType("application/json").content(STATUS_BODY))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/trip/404").header("Authorization", bearer(UserRole.ADMIN)))
                .andExpect(status().isNotFound());
    }

    @Test
    void refusedWritesAre409WithTheServiceMessage() throws Exception {
        when(tripService.update(eq(5L), any())).thenThrow(new ConflictException("Chỉ sửa được chuyến SCHEDULED"));
        when(tripService.changeStatus(eq(5L), any())).thenThrow(new ConflictException("Không thể chuyển COMPLETED → ONGOING"));
        doThrow(new ConflictException("Chuyến đã có vé, hãy huỷ chuyến thay vì xoá")).when(tripService).delete(5L);

        mockMvc.perform(put("/api/trip/5").header("Authorization", bearer(UserRole.ADMIN))
                        .contentType("application/json").content(BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Chỉ sửa được chuyến SCHEDULED"));
        mockMvc.perform(patch("/api/trip/5/status").header("Authorization", bearer(UserRole.ADMIN))
                        .contentType("application/json").content(STATUS_BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Không thể chuyển COMPLETED → ONGOING"));
        mockMvc.perform(delete("/api/trip/5").header("Authorization", bearer(UserRole.ADMIN)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Chuyến đã có vé, hãy huỷ chuyến thay vì xoá"));
    }

    @Test
    void pageSizeAboveTheLimitIs400() throws Exception {
        // PageRequests (inside the service) rejects size > 100 with IllegalArgumentException.
        when(tripService.getTrips(any(), any(), any(), any(), any(), any(), anyInt(), eq(101)))
                .thenThrow(new IllegalArgumentException("size phải từ 1 đến 100"));

        mockMvc.perform(get("/api/trip").param("size", "101").header("Authorization", bearer(UserRole.ADMIN)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void removedEndpointsAreGone() throws Exception {
        mockMvc.perform(get("/api/trip/scheduled").header("Authorization", bearer(UserRole.ADMIN)))
                .andExpect(status().isBadRequest()); // "scheduled" is not a trip id
        mockMvc.perform(get("/api/trip/5/revenue").header("Authorization", bearer(UserRole.ADMIN)))
                .andExpect(status().isNotFound());
    }

    private String bearer(UserRole role) {
        return "Bearer " + jwtUtil.generateAccessToken(1L, role);
    }
}
