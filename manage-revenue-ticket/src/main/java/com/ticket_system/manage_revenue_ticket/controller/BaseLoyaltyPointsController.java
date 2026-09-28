package com.ticket_system.manage_revenue_ticket.controller;

import com.ticket_system.common.Enum.UserRole;
import com.ticket_system.common.annotation.RoleRequired;
import com.ticket_system.common.Dto.response.BaseResponseDto;
import com.ticket_system.manage_revenue_ticket.Dto.request.BaseLoyaltyPointsRequest;
import com.ticket_system.manage_revenue_ticket.entity.BaseLoyaltyPoints;
import com.ticket_system.manage_revenue_ticket.service.BaseLoyaltyPointsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/base_loyalty_point")
@RoleRequired(UserRole.ADMIN)
public class BaseLoyaltyPointsController {

    private static final Logger log = LoggerFactory.getLogger(BaseLoyaltyPointsController.class);

    private final BaseLoyaltyPointsService baseLoyaltyPointsService;

    public BaseLoyaltyPointsController(BaseLoyaltyPointsService baseLoyaltyPointsService) {
        this.baseLoyaltyPointsService = baseLoyaltyPointsService;
    }

    // 🆕 Tạo mới chính sách tích điểm
    @PostMapping
    @RoleRequired(UserRole.ADMIN)
    public ResponseEntity<BaseResponseDto<BaseLoyaltyPoints>> create(@RequestBody BaseLoyaltyPointsRequest request) {
        log.debug("Creating base loyalty policy: {}", request);
        BaseLoyaltyPoints created = baseLoyaltyPointsService.create(request);
        return ResponseEntity.ok(BaseResponseDto.success(201, "Tạo thành công",created));
    }

    // ✏️ Cập nhật chính sách tích điểm
    @PutMapping("/{id}")
    @RoleRequired(UserRole.ADMIN)
    public ResponseEntity<BaseResponseDto<BaseLoyaltyPoints>> update(
            @PathVariable Long id,
            @RequestBody BaseLoyaltyPointsRequest request
    ) {
        BaseLoyaltyPoints updated = baseLoyaltyPointsService.update(id, request);
        return ResponseEntity.ok(BaseResponseDto.success(201, "Tạo thành công",updated));
    }
}
