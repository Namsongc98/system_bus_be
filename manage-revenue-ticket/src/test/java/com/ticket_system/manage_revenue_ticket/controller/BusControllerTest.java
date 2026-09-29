package com.ticket_system.manage_revenue_ticket.controller;

import com.ticket_system.common.Dto.response.PageResponse;
import com.ticket_system.common.exception.ConflictException;
import com.ticket_system.common.exception.GlobalExceptionHandler;
import com.ticket_system.common.exception.ResourceNotFoundException;
import com.ticket_system.manage_revenue_ticket.Dto.request.BusRequest;
import com.ticket_system.manage_revenue_ticket.Dto.response.BusResponse;
import com.ticket_system.manage_revenue_ticket.Enum.BusStatus;
import com.ticket_system.manage_revenue_ticket.service.BusService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
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

/** BusController contract (task 1.1, T1): status codes, @Valid and the 400 body shape (S10). Auth is in BusControllerAuthTest. */
class BusControllerTest {

    private final BusService busService = mock(BusService.class);
    private final MockMvc mockMvc = MockMvcBuilders
            .standaloneSetup(new BusController(busService))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

    private static final BusResponse BUS = new BusResponse(7L, "51A-00001", 45, BusStatus.AVAILABLE, null, null);

    @Test
    void listReturnsPageResponseAndPassesStatusFilter() throws Exception {
        when(busService.getBuses(BusStatus.AVAILABLE, 1, 5))
                .thenReturn(new PageResponse<>(List.of(BUS), 1, 5, 6, 2));

        mockMvc.perform(get("/api/bus").param("status", "AVAILABLE").param("page", "1").param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].plateNumber").value("51A-00001"))
                .andExpect(jsonPath("$.data.content[0].status").value("AVAILABLE"))
                .andExpect(jsonPath("$.data.totalElements").value(6))
                .andExpect(jsonPath("$.data.page").value(1));
    }

    @Test
    void listWithoutStatusUsesDefaults() throws Exception {
        when(busService.getBuses(null, 0, 12)).thenReturn(new PageResponse<>(List.of(), 0, 12, 0, 0));

        mockMvc.perform(get("/api/bus")).andExpect(status().isOk());

        verify(busService).getBuses(null, 0, 12);
    }

    @Test
    void unknownStatusIs400() throws Exception {
        mockMvc.perform(get("/api/bus").param("status", "FOO"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Giá trị 'FOO' không hợp lệ cho 'status'"));

        verifyNoInteractions(busService);
    }

    @Test
    void outOfRangeSizeIs400() throws Exception {
        when(busService.getBuses(any(), anyInt(), eq(101)))
                .thenThrow(new IllegalArgumentException("size must be between 1 and 100"));

        mockMvc.perform(get("/api/bus").param("size", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("size must be between 1 and 100"));
    }

    @Test
    void getOneReturnsBusOr404() throws Exception {
        when(busService.getBus(7L)).thenReturn(BUS);
        when(busService.getBus(8L)).thenThrow(new ResourceNotFoundException("Không tìm thấy xe với id: 8"));

        mockMvc.perform(get("/api/bus/7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(7));
        mockMvc.perform(get("/api/bus/8")).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/bus/abc")).andExpect(status().isBadRequest());
    }

    @Test
    void createReturns201WithBody() throws Exception {
        when(busService.create(any())).thenReturn(BUS);

        mockMvc.perform(post("/api/bus").contentType("application/json")
                        .content("{\"plateNumber\":\"51A-00001\",\"capacity\":45,\"status\":\"AVAILABLE\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value(201))
                .andExpect(jsonPath("$.data.plateNumber").value("51A-00001"));

        ArgumentCaptor<BusRequest> captor = ArgumentCaptor.forClass(BusRequest.class);
        verify(busService).create(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(BusStatus.AVAILABLE);
    }

    @Test
    void invalidBodyIs400WithFieldErrors() throws Exception {
        mockMvc.perform(post("/api/bus").contentType("application/json")
                        .content("{\"plateNumber\":\" \",\"capacity\":0,\"status\":\"AVAILABLE\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").isString())
                .andExpect(jsonPath("$.path").value("/api/bus"))
                .andExpect(jsonPath("$.errors.plateNumber").value("Biển số xe không được để trống"))
                .andExpect(jsonPath("$.errors.capacity").value("Sức chứa phải lớn hơn 0"));

        verifyNoInteractions(busService);
    }

    @Test
    void oldStatusValueInBodyIs400() throws Exception {
        mockMvc.perform(post("/api/bus").contentType("application/json")
                        .content("{\"plateNumber\":\"51A-00001\",\"capacity\":45,\"status\":\"PENDING\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Giá trị 'PENDING' không hợp lệ cho 'status'"));

        verifyNoInteractions(busService);
    }

    @Test
    void duplicatePlateIs409() throws Exception {
        when(busService.create(any())).thenThrow(new ConflictException("Biển số xe đã tồn tại: 51A-00001"));

        mockMvc.perform(post("/api/bus").contentType("application/json")
                        .content("{\"plateNumber\":\"51A-00001\",\"capacity\":45,\"status\":\"AVAILABLE\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Biển số xe đã tồn tại: 51A-00001"));
    }

    @Test
    void updateReturns200Or404() throws Exception {
        when(busService.update(eq(7L), any())).thenReturn(BUS);
        when(busService.update(eq(8L), any())).thenThrow(new ResourceNotFoundException("Không tìm thấy xe với id: 8"));
        String body = "{\"plateNumber\":\"51A-00001\",\"capacity\":45,\"status\":\"MAINTENANCE\"}";

        mockMvc.perform(put("/api/bus/7").contentType("application/json").content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(7));
        mockMvc.perform(put("/api/bus/8").contentType("application/json").content(body))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteReturns200Or404Or409() throws Exception {
        doThrow(new ResourceNotFoundException("Không tìm thấy xe với id: 8")).when(busService).delete(8L);
        doThrow(new ConflictException("Xe đang được gán cho chuyến chưa kết thúc")).when(busService).delete(9L);

        mockMvc.perform(delete("/api/bus/7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Delete successfully"));
        mockMvc.perform(delete("/api/bus/8")).andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/bus/9"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Xe đang được gán cho chuyến chưa kết thúc"));
    }

    @Test
    void updateWithInvalidBodyIs400() throws Exception {
        mockMvc.perform(put("/api/bus/7").contentType("application/json")
                        .content("{\"plateNumber\":\"" + "X".repeat(21) + "\",\"capacity\":null,\"status\":null}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.plateNumber").value("Biển số xe tối đa 20 ký tự"))
                .andExpect(jsonPath("$.errors.capacity").value("Sức chứa không được để trống"))
                .andExpect(jsonPath("$.errors.status").value("Trạng thái xe không được để trống"));

        verifyNoInteractions(busService);
    }

    @Test
    void malformedJsonIs400WithStandardBody() throws Exception {
        mockMvc.perform(post("/api/bus").contentType("application/json").content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Dữ liệu gửi lên không đúng định dạng"))
                .andExpect(jsonPath("$.path").value("/api/bus"));
    }

    @Test
    void echoedRejectedValueIsStrippedAndCapped() throws Exception {
        String longValue = "A".repeat(80);

        mockMvc.perform(get("/api/bus").param("status", longValue + "\u0007"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Giá trị '" + "A".repeat(50) + "…' không hợp lệ cho 'status'"));
    }
}
