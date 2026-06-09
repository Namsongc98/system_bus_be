package com.ticket_system.manage_revenue_ticket.service;

import com.ticket_system.manage_revenue_ticket.entity.BaseSalary;
import com.ticket_system.manage_revenue_ticket.entity.User;
import com.ticket_system.manage_revenue_ticket.repository.BaseSalaryRepository;
import com.ticket_system.manage_revenue_ticket.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class BaseSalaryService {

    @Autowired
    private  BaseSalaryRepository baseSalaryRepository;

    @Autowired
    private UserRepository userRepository;

    // tạo base lương
    public BaseSalary save(Long userId, BigDecimal salary, LocalDate effectiveFrom) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User không tồn tại"));

        BaseSalary baseSalary = new BaseSalary();
        baseSalary.setUser(user);
        baseSalary.setRole(user.getRole());
        baseSalary.setBaseSalary(salary);
        baseSalary.setUpdatedAt(LocalDateTime.now());
        baseSalary.setEffectiveFrom(effectiveFrom);
        return baseSalaryRepository.save(baseSalary);
    }

    // update base lương
    public BaseSalary updateBaseSalary(Long id, BigDecimal newSalary) {
        BaseSalary baseSalary = baseSalaryRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy base salary với id: " + id));

        baseSalary.setBaseSalary(newSalary);
        baseSalary.setUpdatedAt(LocalDateTime.now());
        return baseSalaryRepository.save(baseSalary);
    }

    public List<BaseSalary> getAll() {
        return baseSalaryRepository.findAll();
    }
}
