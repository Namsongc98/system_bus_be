package com.ticket_system.manage_revenue_ticket.controller;

import com.ticket_system.common.Dto.response.BaseResponseDto;
import com.ticket_system.manage_revenue_ticket.Dto.request.LoyaltyPointsRequest;
import com.ticket_system.manage_revenue_ticket.entity.LoyaltyPoint;
import com.ticket_system.manage_revenue_ticket.service.LoyaltyPointsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/loyalty_point")
public class LoyaltyPointsController {

    @Autowired
    private LoyaltyPointsService loyaltyPointsService;

    // 🆕 Tạo mới lịch sử điểm
    @PostMapping
    public ResponseEntity<BaseResponseDto<LoyaltyPoint>> create(@RequestBody LoyaltyPointsRequest request) {
        LoyaltyPoint loyaltyPoint = loyaltyPointsService.create(request);
        return ResponseEntity.ok(BaseResponseDto.success(201,"create", loyaltyPoint));
    }

    // ✏️ Cập nhật lịch sử điểm
    @PutMapping("/{id}")
    public ResponseEntity<BaseResponseDto<LoyaltyPoint>> update(
            @PathVariable Long id,
            @RequestBody LoyaltyPointsRequest request
    ) {
        LoyaltyPoint loyaltyPoint = loyaltyPointsService.update(id, request);
        return ResponseEntity.ok(BaseResponseDto.success(200,"create", loyaltyPoint));
    }
}
