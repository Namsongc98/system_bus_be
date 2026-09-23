package com.ticket_system.manage_revenue_ticket.controller;

import com.ticket_system.common.Enum.UserRole;
import com.ticket_system.common.annotation.RoleRequired;
import com.ticket_system.common.Dto.response.BaseResponseDto;
import com.ticket_system.manage_revenue_ticket.entity.Salary;
import com.ticket_system.manage_revenue_ticket.service.SalaryService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/salary")
@RoleRequired(UserRole.ADMIN)
public class SalaryController {

    private final SalaryService salaryService;

    public SalaryController(SalaryService salaryService) {
        this.salaryService = salaryService;
    }

    @PostMapping("/driver")
    @RoleRequired(UserRole.ADMIN)
    public ResponseEntity<BaseResponseDto<Page<Salary>>> fineSalaryMonth(
            @RequestParam byte month,
            @RequestParam short year
    ){
         salaryService.generateMonthlySalaries( month,  year);
        return ResponseEntity.ok(BaseResponseDto.success(200,"Create success", null));
    };

    @GetMapping
    @RoleRequired(UserRole.ADMIN)
    public  ResponseEntity<BaseResponseDto<Page<Salary>>> getSalaryMonth(
            @RequestParam byte month,
            @RequestParam short year,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ){
        Pageable pageable = PageRequest.of(page,size);
        Page<Salary> listSalary = salaryService.selectSalaryMonth(month,year,pageable);
        return ResponseEntity.ok(BaseResponseDto.success(201,"Create success", listSalary));
    }

    @PostMapping
    @RoleRequired(UserRole.ADMIN)
    public ResponseEntity<BaseResponseDto<Salary>> fineSalaryMonth(@RequestParam byte month, @RequestParam short year, @RequestParam Long userId){
        Salary salary = salaryService.generateMonthlySalariesRoleUser( month,  year, userId);
        return ResponseEntity.ok(BaseResponseDto.success(201,"Create success", salary));
    };

}
