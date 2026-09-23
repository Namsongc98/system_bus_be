package com.ticket_system.manage_revenue_ticket.controller;

import com.ticket_system.common.Dto.response.BaseResponseDto;
import com.ticket_system.common.Enum.UserRole;
import com.ticket_system.common.annotation.RoleRequired;
import com.ticket_system.manage_revenue_ticket.Dto.response.AdminDashboardResponse;
import com.ticket_system.manage_revenue_ticket.Dto.response.RevenueTrendResponse;
import com.ticket_system.manage_revenue_ticket.service.AdminDashboardService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
@RoleRequired(UserRole.ADMIN)
public class AdminDashboardController {
    private final AdminDashboardService adminDashboardService;

    public AdminDashboardController(AdminDashboardService adminDashboardService) {
        this.adminDashboardService = adminDashboardService;
    }

    @GetMapping("/dashboard")
    @RoleRequired(UserRole.ADMIN)
    public ResponseEntity<BaseResponseDto<AdminDashboardResponse>> getDashboard() {
        return ResponseEntity.ok(BaseResponseDto.success(
                200,
                "Dashboard fetched",
                adminDashboardService.getDashboard()
        ));
    }

    @GetMapping("/revenue")
    @RoleRequired(UserRole.ADMIN)
    public ResponseEntity<BaseResponseDto<List<RevenueTrendResponse>>> getRevenueTrend(
            @RequestParam(defaultValue = "monthly") String period
    ) {
        return ResponseEntity.ok(BaseResponseDto.success(
                200,
                "Revenue trend fetched",
                adminDashboardService.getRevenueTrend(period)
        ));
    }
}
