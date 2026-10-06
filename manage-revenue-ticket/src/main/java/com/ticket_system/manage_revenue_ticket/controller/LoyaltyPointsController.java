package com.ticket_system.manage_revenue_ticket.controller;

import com.ticket_system.common.Dto.response.BaseResponseDto;
import com.ticket_system.common.Enum.UserRole;
import com.ticket_system.common.annotation.RoleRequired;
import com.ticket_system.manage_revenue_ticket.Dto.request.LoyaltyPointsRequest;
import com.ticket_system.manage_revenue_ticket.entity.LoyaltyPoint;
import com.ticket_system.manage_revenue_ticket.service.LoyaltyPointsService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

// B20: point history is changed by an admin only until task 4.3 decides whether a customer acts on their own
// points (then customerId comes from the JWT, never from the body).
@RestController
@RequestMapping("/api/loyalty_point")
@RoleRequired(UserRole.ADMIN)
public class LoyaltyPointsController {

    private final LoyaltyPointsService loyaltyPointsService;

    public LoyaltyPointsController(LoyaltyPointsService loyaltyPointsService) {
        this.loyaltyPointsService = loyaltyPointsService;
    }

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
