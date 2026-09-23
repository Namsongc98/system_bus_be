package com.ticket_system.manage_revenue_ticket.controller;

import com.ticket_system.common.Dto.response.BaseResponseDto;
import com.ticket_system.common.Enum.UserRole;
import com.ticket_system.common.annotation.RoleRequired;
import com.ticket_system.manage_revenue_ticket.projection.CustomerRevenueByRouteProjection;
import com.ticket_system.manage_revenue_ticket.service.RevenueService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/revenue")
@RoleRequired(UserRole.ADMIN)
public class RevenueController {

    private final RevenueService revenueService;

    public RevenueController(RevenueService revenueService) {
        this.revenueService = revenueService;
    }

    @GetMapping("/bus")
    @RoleRequired(UserRole.ADMIN)
    public ResponseEntity<?> getRevenueByBusAndDate(
            @RequestParam (required = false) Long busId,
            @RequestParam LocalDate date) {
        List<Map<String, Object>> revenueByBusAndDate = revenueService.getRevenueByBusAndDate(busId, date);
        if(revenueByBusAndDate.isEmpty()){
            return ResponseEntity.ok(BaseResponseDto.success(200, "xe không có doanh thu", null));
        }
        return ResponseEntity.ok(BaseResponseDto.success(200, "Revenue fetched", revenueByBusAndDate));
    }

    @GetMapping("/employee")
    @RoleRequired(UserRole.ADMIN)
    public ResponseEntity<?> getRevenueByCollectorAndDate(
            @RequestParam (required = false) Long busId,
            @RequestParam LocalDate date) {
        List<Map<String, Object>> revenueByBusAndDate = revenueService.getRevenueByEmployeeAndDate(busId, date);
        if(revenueByBusAndDate.isEmpty()){
            return ResponseEntity.ok(BaseResponseDto.success(200, "bán hàng không có doanh thu", null));
        }
        return ResponseEntity.ok(BaseResponseDto.success(200, "Revenue fetched", revenueByBusAndDate));
    }

    @GetMapping("/user")
    @RoleRequired(UserRole.ADMIN)
    public ResponseEntity<?> getRevenueByUserAndDate(
            @RequestParam (required = false) Long busId,
            @RequestParam (required = false) Integer date,
            @RequestParam (required = false) Integer month,
            @RequestParam Integer year
    ) {
        List<CustomerRevenueByRouteProjection> revenueByBusAndDate = revenueService.getRevenueByUserAndDate(busId, date,month,year);
        if(revenueByBusAndDate.isEmpty()){
            return ResponseEntity.ok(BaseResponseDto.success(200, "người dùng không có doanh thu", null));
        }
        return ResponseEntity.ok(BaseResponseDto.success(200, "Revenue fetched", revenueByBusAndDate));
    }

    @GetMapping("/bus/{busId}/range")
    @RoleRequired(UserRole.ADMIN)
    public ResponseEntity<?> getRevenueByBusAndRange(
            @PathVariable Long busId,
            @RequestParam("from") LocalDate from,
            @RequestParam("to") LocalDate to) {

        BigDecimal total = revenueService.getRevenueByBusAndRange(busId, from, to);
        return ResponseEntity.ok(BaseResponseDto.success(200, "Revenue fetched", total));
    }

    @GetMapping("/bus/all")
    @RoleRequired(UserRole.ADMIN)
    public ResponseEntity<?> getTotalRevenueByAllBuses(@RequestParam LocalDate date) {
        List<Map<String, Object>> data = revenueService.getRevenueAllBusesInDate(date);
        return ResponseEntity.ok(BaseResponseDto.success(200, "Revenue fetched", data));
    }

}
