package com.ticket_system.manage_revenue_ticket.controller;

import com.ticket_system.common.Dto.response.BaseResponseDto;
import com.ticket_system.manage_revenue_ticket.entity.LoyaltyReward;
import com.ticket_system.manage_revenue_ticket.service.LoyaltyRewardService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/loyalty/rewards")
public class LoyaltyRewardController {

    @Autowired
    private LoyaltyRewardService rewardService;

    @PostMapping
    public ResponseEntity<BaseResponseDto<LoyaltyReward>> create(@RequestBody LoyaltyReward request) {
        LoyaltyReward created = rewardService.create(request);
        return ResponseEntity.ok(BaseResponseDto.success(201,"Cteate succes", created));
    }

    @PutMapping("/{id}")
    public ResponseEntity<BaseResponseDto<LoyaltyReward>> update(@PathVariable Long id, @RequestBody LoyaltyReward request) {
        LoyaltyReward updated = rewardService.update(id, request);
        return ResponseEntity.ok(BaseResponseDto.success(200, "Update succes",updated));
    }
}
