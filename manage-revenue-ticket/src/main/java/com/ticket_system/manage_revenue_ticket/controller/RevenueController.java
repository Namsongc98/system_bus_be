package com.ticket_system.manage_revenue_ticket.controller;

import com.ticket_system.common.Dto.response.BaseResponseDto;
import com.ticket_system.manage_revenue_ticket.projection.CustomerRevenueByRouteProjection;
import com.ticket_system.manage_revenue_ticket.service.RevenueService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/revenue")
public class RevenueController {

    @Autowired
    private RevenueService revenueService;

    @GetMapping("/bus")
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
    public ResponseEntity<?> getRevenueByBusAndRange(
            @PathVariable Long busId,
            @RequestParam("from") LocalDate from,
            @RequestParam("to") LocalDate to) {

        BigDecimal total = revenueService.getRevenueByBusAndRange(busId, from, to);
        return ResponseEntity.ok(BaseResponseDto.success(200, "Revenue fetched", total));
    }

    @GetMapping("/bus/all")
    public ResponseEntity<?> getTotalRevenueByAllBuses(@RequestParam LocalDate date) {
        List<Map<String, Object>> data = revenueService.getRevenueAllBusesInDate(date);
        return ResponseEntity.ok(BaseResponseDto.success(200, "Revenue fetched", data));
    }

}
