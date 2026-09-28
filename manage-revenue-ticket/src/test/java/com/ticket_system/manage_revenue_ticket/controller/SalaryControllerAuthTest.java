package com.ticket_system.manage_revenue_ticket.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticket_system.common.Enum.UserRole;
import com.ticket_system.common.exception.GlobalExceptionHandler;
import com.ticket_system.common.util.JwtUtil;
import com.ticket_system.manage_revenue_ticket.interceptor.AuthInterceptor;
import com.ticket_system.manage_revenue_ticket.service.SalaryService;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.anyByte;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyShort;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * AuthInterceptor lets any valid JWT through an endpoint without @RoleRequired, so
 * salary generation must carry it explicitly (lead review L12).
 */
class SalaryControllerAuthTest {
    private static final String SECRET = "01234567890123456789012345678901";

    private final JwtUtil jwtUtil = new JwtUtil(SECRET);
    private final SalaryService salaryService = mock(SalaryService.class);
    private final MockMvc mockMvc = MockMvcBuilders
            .standaloneSetup(new SalaryController(salaryService))
            .addInterceptors(new AuthInterceptor(jwtUtil, new ObjectMapper()))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

    @Test
    void rejectsCustomerGeneratingSalaryForAnotherUser() throws Exception {
        mockMvc.perform(post("/api/salary")
                        .param("month", "9").param("year", "2026").param("userId", "3")
                        .header("Authorization", bearer(UserRole.CUSTOMER)))
                .andExpect(status().isForbidden());

        verify(salaryService, never()).generateMonthlySalariesRoleUser(anyByte(), anyShort(), anyLong());
    }

    @Test
    void allowsAdmin() throws Exception {
        mockMvc.perform(post("/api/salary")
                        .param("month", "9").param("year", "2026").param("userId", "3")
                        .header("Authorization", bearer(UserRole.ADMIN)))
                .andExpect(status().isOk());

        verify(salaryService).generateMonthlySalariesRoleUser((byte) 9, (short) 2026, 3L);
    }

    private String bearer(UserRole role) {
        return "Bearer " + jwtUtil.generateAccessToken(1L, role);
    }
}
