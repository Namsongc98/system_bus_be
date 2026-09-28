package com.ticket_system.manage_revenue_ticket.controller;

import com.ticket_system.common.Enum.UserRole;
import com.ticket_system.common.annotation.RoleRequired;
import com.ticket_system.common.Dto.response.BaseResponseDto;
import com.ticket_system.manage_revenue_ticket.entity.LoyaltyReward;
import com.ticket_system.manage_revenue_ticket.service.LoyaltyRewardService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/loyalty/rewards")
@RoleRequired(UserRole.ADMIN)
public class LoyaltyRewardController {

    private final LoyaltyRewardService rewardService;

    public LoyaltyRewardController(LoyaltyRewardService rewardService) {
        this.rewardService = rewardService;
    }

    @PostMapping
    @RoleRequired(UserRole.ADMIN)
    public ResponseEntity<BaseResponseDto<LoyaltyReward>> create(@RequestBody LoyaltyReward request) {
        LoyaltyReward created = rewardService.create(request);
        return ResponseEntity.ok(BaseResponseDto.success(201,"Cteate succes", created));
    }

    @PutMapping("/{id}")
    @RoleRequired(UserRole.ADMIN)
    public ResponseEntity<BaseResponseDto<LoyaltyReward>> update(@PathVariable Long id, @RequestBody LoyaltyReward request) {
        LoyaltyReward updated = rewardService.update(id, request);
        return ResponseEntity.ok(BaseResponseDto.success(200, "Update succes",updated));
    }
}
