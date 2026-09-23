package com.ticket_system.manage_revenue_ticket.controller;

import com.ticket_system.common.Enum.UserRole;
import com.ticket_system.common.annotation.RoleRequired;
import com.ticket_system.common.Dto.response.BaseResponseDto;
import com.ticket_system.manage_revenue_ticket.Dto.request.BaseSalaryRequestDTO;
import com.ticket_system.manage_revenue_ticket.entity.BaseSalary;
import com.ticket_system.manage_revenue_ticket.service.BaseSalaryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/base_salary")
@RoleRequired(UserRole.ADMIN)
public class BaseSalaryController {

    private final BaseSalaryService baseSalaryService;

    public BaseSalaryController(BaseSalaryService baseSalaryService) {
        this.baseSalaryService = baseSalaryService;
    }

    @PostMapping
    @RoleRequired(UserRole.ADMIN)
    public ResponseEntity<BaseResponseDto<BaseSalary>> createBaseSalary(@RequestBody BaseSalaryRequestDTO request) {
        BaseSalary bs = baseSalaryService.save(request.getUserId(), request.getBaseSalary(), request.getEffectiveFrom());
        return ResponseEntity.ok(BaseResponseDto.success(201,"create success", bs));
    }

    @PutMapping("/{id}")
    @RoleRequired(UserRole.ADMIN)
    public ResponseEntity<BaseResponseDto<BaseSalary>> updateBaseSalary(@PathVariable String id, @RequestBody BaseSalaryRequestDTO request) {
        baseSalaryService.updateBaseSalary(Long.parseLong(id), request.getBaseSalary());
        return ResponseEntity.ok(BaseResponseDto.success(200,"update success", null));
    }

    @GetMapping
    @RoleRequired(UserRole.ADMIN)
    public List<BaseSalary> getAll() {
        return baseSalaryService.getAll();
    }
}
