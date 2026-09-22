package com.ticket_system.manage_revenue_ticket.controller;

import com.ticket_system.common.Enum.UserRole;
import com.ticket_system.common.annotation.RoleRequired;
import com.ticket_system.manage_revenue_ticket.service.FileService;
import com.ticket_system.manage_revenue_ticket.service.TicketService;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TicketControllerTest {
    private final TicketService ticketService = mock(TicketService.class);
    private final FileService excelService = mock(FileService.class);
    private final MockMvc mockMvc = MockMvcBuilders
            .standaloneSetup(new TicketController(ticketService, excelService))
            .build();

    @Test
    void excelExportRequiresAdminRole() throws Exception {
        RoleRequired annotation = TicketController.class
                .getMethod(
                        "exportTicketSummaryToExcel",
                        Long.class, LocalDateTime.class, LocalDateTime.class
                )
                .getAnnotation(RoleRequired.class);

        assertThat(annotation).isNotNull();
        assertThat(annotation.value()).containsExactly(UserRole.ADMIN);
    }

    @Test
    void excelExportReturnsXlsxBytesWhenServicesSucceed() throws Exception {
        List<Map<String, Object>> summary = List.of(Map.of("bus_id", 1));
        when(ticketService.getTicketSummaryByBusAndTime(isNull(), any(), any())).thenReturn(summary);
        when(excelService.exportTicketSummaryToExcel(eq(summary)))
                .thenReturn(new ByteArrayOutputStream());

        mockMvc.perform(get("/api/ticket/summary/excel")
                        .param("fromDate", "2026-06-01:00:00:00")
                        .param("toDate", "2026-06-30:23:59:59"))
                .andExpect(status().isOk());
    }

    @Test
    void excelExportReturns500WithoutBodyWhenWorkbookFails() throws Exception {
        when(ticketService.getTicketSummaryByBusAndTime(isNull(), any(), any())).thenReturn(List.of());
        when(excelService.exportTicketSummaryToExcel(any())).thenThrow(new java.io.IOException("disk full"));

        mockMvc.perform(get("/api/ticket/summary/excel")
                        .param("fromDate", "2026-06-01:00:00:00")
                        .param("toDate", "2026-06-30:23:59:59"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().bytes(new byte[0]));
    }
}
