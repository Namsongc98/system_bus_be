package com.ticket_system.manage_revenue_ticket.controller;

import com.ticket_system.common.Enum.UserRole;
import com.ticket_system.common.exception.CustomAccessDeniedHandler;
import com.ticket_system.common.exception.CustomAuthEntryPoint;
import com.ticket_system.common.filter.JwtAuthFilter;
import com.ticket_system.common.security.SecurityConfig;
import com.ticket_system.common.util.JwtUtil;
import com.ticket_system.manage_revenue_ticket.config.WebConfig;
import com.ticket_system.manage_revenue_ticket.interceptor.AuthInterceptor;
import com.ticket_system.manage_revenue_ticket.service.FileService;
import com.ticket_system.manage_revenue_ticket.service.TicketService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.io.ByteArrayOutputStream;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Runs /api/ticket/summary/excel through the real Spring Security chain and the
 * real AuthInterceptor, so each 0.4 change is pinned by a test:
 * the 401 must come from Spring Security (not permitAll), a CUSTOMER token must
 * be authenticated by JwtAuthFilter (not bypassed) and then refused by
 * @RoleRequired(ADMIN).
 */
@WebMvcTest(TicketController.class)
@Import({
        SecurityConfig.class,
        JwtAuthFilter.class,
        JwtUtil.class,
        CustomAuthEntryPoint.class,
        CustomAccessDeniedHandler.class,
        WebConfig.class,
        AuthInterceptor.class
})
@TestPropertySource(properties = "jwt.secret=01234567890123456789012345678901")
class TicketExcelSecurityChainTest {
    private static final String SPRING_SECURITY_401_MESSAGE = "Token không hợp lệ hoặc đã hết hạn";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtUtil jwtUtil;

    @MockBean
    private TicketService ticketService;

    @MockBean
    private FileService fileService;

    @Test
    void missingTokenIsRejectedBySpringSecurityItself() throws Exception {
        // With the old permitAll entry the request would pass Spring Security and
        // be stopped only by AuthInterceptor, whose 401 carries a different message.
        mockMvc.perform(excelRequest())
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value(SPRING_SECURITY_401_MESSAGE));

        verify(ticketService, never()).getTicketSummaryByBusAndTime(any(), any(), any());
    }

    @Test
    void malformedTokenIsRejectedBySpringSecurityItself() throws Exception {
        mockMvc.perform(excelRequest().header("Authorization", "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value(SPRING_SECURITY_401_MESSAGE));

        verify(ticketService, never()).getTicketSummaryByBusAndTime(any(), any(), any());
    }

    @Test
    void trailingSlashVariantIsNotPublic() throws Exception {
        mockMvc.perform(get("/api/ticket/summary/excel/")
                        .param("fromDate", "2026-06-01:00:00:00")
                        .param("toDate", "2026-06-30:23:59:59"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value(SPRING_SECURITY_401_MESSAGE));

        verify(ticketService, never()).getTicketSummaryByBusAndTime(any(), any(), any());
    }

    @ParameterizedTest
    @EnumSource(value = UserRole.class, names = {"CUSTOMER", "DRIVER", "COLLECTOR"})
    void nonAdminTokenIsAuthenticatedThenForbidden(UserRole role) throws Exception {
        // With the old JwtAuthFilter bypass the token would never be read, so
        // Spring Security would answer 401 instead of letting the role check run.
        mockMvc.perform(excelRequest().header("Authorization", bearer(role)))
                .andExpect(status().isForbidden());

        verify(ticketService, never()).getTicketSummaryByBusAndTime(any(), any(), any());
    }

    @Test
    void adminTokenDownloadsTheReport() throws Exception {
        when(ticketService.getTicketSummaryByBusAndTime(any(), any(), any())).thenReturn(List.of());
        when(fileService.exportTicketSummaryToExcel(any())).thenReturn(new ByteArrayOutputStream());

        mockMvc.perform(excelRequest().header("Authorization", bearer(UserRole.ADMIN)))
                .andExpect(status().isOk());
    }

    private MockHttpServletRequestBuilder excelRequest() {
        return get("/api/ticket/summary/excel")
                .param("fromDate", "2026-06-01:00:00:00")
                .param("toDate", "2026-06-30:23:59:59");
    }

    private String bearer(UserRole role) {
        return "Bearer " + jwtUtil.generateAccessToken(1L, role);
    }
}
