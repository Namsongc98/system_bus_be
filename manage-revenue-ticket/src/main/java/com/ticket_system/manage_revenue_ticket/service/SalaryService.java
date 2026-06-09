package com.ticket_system.manage_revenue_ticket.service;

import com.ticket_system.manage_revenue_ticket.entity.BaseSalary;
import com.ticket_system.manage_revenue_ticket.entity.Salary;
import com.ticket_system.manage_revenue_ticket.entity.User;
import com.ticket_system.manage_revenue_ticket.repository.*;
import com.ticket_system.manage_revenue_ticket.repository.*;
import com.ticket_system.common.Enum.UserRole;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Service
public class SalaryService {
    @Autowired
    private SalaryRepository salaryRepository;

    @Autowired
    private TripRepository tripRepository;

    @Autowired
    private BaseSalaryRepository baseSalaryRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TicketRepository ticketRepository;

    @Autowired
    private BaseLoyaltyPointsRepository baseLoyaltyPointsRepository;

    // tính lương của Driver theo tháng
    @Transactional
    public void generateMonthlySalaries(byte month, short year) {
        LocalDate salaryDate = LocalDate.of(year, month, 1);

        List<Object[]> driverTrips = tripRepository.countCompletedTripsByDriver(month, year, null);
        for (Object[] row : driverTrips) {
            Long driverId = ((Number) row[0]).longValue();
            User user = userRepository.findById(driverId)
                    .orElseThrow(() -> new RuntimeException("Driver not found with id: " + driverId));
            Long totalTrips = ((Number) row[1]).longValue();

            // 1️⃣ Lấy base salary từ base_salaries
            BigDecimal baseSalary = baseSalaryRepository.findLatestByUser(driverId, salaryDate)
                    .stream()
                    .findFirst()
                    .map(BaseSalary::getBaseSalary)
                    .orElseGet(() -> baseSalaryRepository.findLatestByRole(UserRole.DRIVER, salaryDate)
                            .stream()
                            .findFirst()
                            .map(BaseSalary::getBaseSalary)
                            .orElse(BigDecimal.ZERO));

            // 2️⃣ Phụ cấp = số chuyến * 100000
            BigDecimal allowance = BigDecimal.valueOf(totalTrips * 100000);

            // 3️⃣ Tổng lương
            BigDecimal total = baseSalary.add(allowance);

            Salary salary = new Salary();
            salary.setUser(user);
            salary.setSalary(total);
            salary.setPeriodMonth(month);
            salary.setPeriodYear(year);
            salaryRepository.save(salary);
        };
    }

    public Page<Salary> selectSalaryMonth(byte month, short year, Pageable pageable){
        return salaryRepository.selectSalaryMonth(month, year, pageable);
    }

    // tính lương tháng của từng nhân viên
    @Transactional
    public Salary generateMonthlySalariesRoleUser(byte month, short year, Long userId){

        User user = userRepository.findById(userId)
                .orElseThrow(()-> new RuntimeException("Không tìm thấy người dùng với mã: " + userId));
        List<BaseSalary> salaryRepo = baseSalaryRepository.findLatestByUserId(user.getId());
        List<Salary> salaries =  salaryRepository.findByPeriodMonthAndPeriodYearAndUserId( month, year, user.getId());
        if(!salaries.isEmpty()){
            throw new RuntimeException("Đã tính lương cho "+ user.getEmail() +" này");
        }
        BigDecimal commissionTotal =  new BigDecimal("0.00");
        BigDecimal commission = salaryRepo.getFirst().getCommission();
        if(user.getRole() == UserRole.DRIVER){
            Long  totalTrip =  tripRepository.countCompletedTripsByDriverForOne( month, year, user.getId());
            if (totalTrip == null) totalTrip = 0L;
            commissionTotal = commission.multiply(BigDecimal.valueOf(totalTrip));
        }
        if(user.getRole() == UserRole.COLLECTOR){
            Long totalTickets =  ticketRepository.countCompletedTripsBySeller( month, year, user.getId());
            if (totalTickets == null) totalTickets = 0L;
            commissionTotal = commission.multiply(BigDecimal.valueOf(totalTickets));
        }
        BigDecimal baseSalary = salaryRepo.getFirst().getBaseSalary();
        BigDecimal bonus = salaryRepo.getFirst().getBonus();
        BigDecimal allowance = salaryRepo.getFirst().getAllowance();
        BigDecimal salaryTotal = baseSalary
                .add(bonus)
                .add(commissionTotal)
                .add(allowance);

        Salary salary = new Salary();
        salary.setUser(user);
        salary.setSalary(salaryTotal);
        salary.setPeriodMonth(month);
        salary.setPeriodYear(year);
        Salary saved = salaryRepository.save(salary);
        return saved;
    }
}
