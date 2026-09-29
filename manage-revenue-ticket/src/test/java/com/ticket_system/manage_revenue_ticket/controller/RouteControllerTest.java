package com.ticket_system.manage_revenue_ticket.controller;

import com.ticket_system.common.Dto.response.PageResponse;
import com.ticket_system.common.exception.ConflictException;
import com.ticket_system.common.exception.GlobalExceptionHandler;
import com.ticket_system.common.exception.ResourceNotFoundException;
import com.ticket_system.manage_revenue_ticket.Dto.response.RouteResponse;
import com.ticket_system.manage_revenue_ticket.Enum.RouteStatus;
import com.ticket_system.manage_revenue_ticket.service.RouteService;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** RouteController contract (task 1.1, T2). Auth is in RouteControllerAuthTest. */
class RouteControllerTest {

    private static final String VALID_BODY = "{\"routeName\":\"HN - HP\",\"startPoint\":\"Hà Nội\","
            + "\"endPoint\":\"Hải Phòng\",\"distanceKm\":120.5,\"status\":\"ACTIVE\"}";
    private static final RouteResponse ROUTE = new RouteResponse(
            3L, "HN - HP", "Hà Nội", "Hải Phòng", new BigDecimal("120.50"), RouteStatus.ACTIVE, null, null);

    private final RouteService routeService = mock(RouteService.class);
    private final MockMvc mockMvc = MockMvcBuilders
            .standaloneSetup(new RouteController(routeService))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

    @Test
    void listReturnsPageResponseWithDefaults() throws Exception {
        when(routeService.getRoutes(null, 0, 10)).thenReturn(new PageResponse<>(List.of(ROUTE), 0, 10, 1, 1));

        mockMvc.perform(get("/api/route"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].routeName").value("HN - HP"))
                .andExpect(jsonPath("$.data.totalElements").value(1));
    }

    @Test
    void listPassesStatusFilterAndRejectsUnknownStatus() throws Exception {
        when(routeService.getRoutes(RouteStatus.ACTIVE, 0, 10)).thenReturn(new PageResponse<>(List.of(), 0, 10, 0, 0));

        mockMvc.perform(get("/api/route").param("status", "ACTIVE")).andExpect(status().isOk());
        verify(routeService).getRoutes(RouteStatus.ACTIVE, 0, 10);

        mockMvc.perform(get("/api/route").param("status", "DELETED")).andExpect(status().isBadRequest());
    }

    @Test
    void getOneReturnsRouteOr404() throws Exception {
        when(routeService.getRoute(3L)).thenReturn(ROUTE);
        when(routeService.getRoute(4L)).thenThrow(new ResourceNotFoundException("Không tìm thấy tuyến với id: 4"));

        mockMvc.perform(get("/api/route/3")).andExpect(status().isOk()).andExpect(jsonPath("$.data.id").value(3));
        mockMvc.perform(get("/api/route/4"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Không tìm thấy tuyến với id: 4"));
    }

    @Test
    void createReturns201() throws Exception {
        when(routeService.createRoute(any())).thenReturn(ROUTE);

        mockMvc.perform(post("/api/route").contentType("application/json").content(VALID_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").value(3));
    }

    @Test
    void invalidBodyIs400() throws Exception {
        mockMvc.perform(post("/api/route").contentType("application/json")
                        .content("{\"routeName\":\"\",\"startPoint\":\"A\",\"endPoint\":\"B\",\"distanceKm\":0,\"status\":\"ACTIVE\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.routeName").value("Tên tuyến không được để trống"))
                .andExpect(jsonPath("$.errors.distanceKm").value("Quãng đường phải lớn hơn 0"));

        verifyNoInteractions(routeService);
    }

    @Test
    void sameStartAndEndIs400() throws Exception {
        when(routeService.createRoute(any())).thenThrow(new IllegalArgumentException("Điểm đi và điểm đến phải khác nhau"));

        mockMvc.perform(post("/api/route").contentType("application/json").content(VALID_BODY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Điểm đi và điểm đến phải khác nhau"));
    }

    @Test
    void updateReturns200Or404() throws Exception {
        when(routeService.updateRoute(eq(3L), any())).thenReturn(ROUTE);
        when(routeService.updateRoute(eq(4L), any())).thenThrow(new ResourceNotFoundException("Không tìm thấy tuyến với id: 4"));

        mockMvc.perform(put("/api/route/3").contentType("application/json").content(VALID_BODY))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/route/4").contentType("application/json").content(VALID_BODY))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteReturns200Or409() throws Exception {
        doThrow(new ConflictException("Tuyến đã có lịch sử chuyến, hãy chuyển sang INACTIVE"))
                .when(routeService).deleteRoute(4L);

        mockMvc.perform(delete("/api/route/3")).andExpect(status().isOk());
        mockMvc.perform(delete("/api/route/4"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Tuyến đã có lịch sử chuyến, hãy chuyển sang INACTIVE"));
    }

    @Test
    void nonNumericIdAndInvalidDistanceAre400() throws Exception {
        mockMvc.perform(get("/api/route/abc")).andExpect(status().isBadRequest());
        mockMvc.perform(put("/api/route/3").contentType("application/json")
                        .content("{\"routeName\":\"R\",\"startPoint\":\"A\",\"endPoint\":\"B\","
                                + "\"distanceKm\":1.234,\"status\":\"ACTIVE\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.distanceKm").exists());

        verifyNoInteractions(routeService);
    }
}
