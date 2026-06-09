package com.ticket_system.manage_revenue_ticket.service;

import com.ticket_system.manage_revenue_ticket.Dto.request.BaseLoyaltyPointsRequest;
import com.ticket_system.manage_revenue_ticket.Enum.BaseLoyaltyPointStatus;
import com.ticket_system.manage_revenue_ticket.entity.BaseLoyaltyPoints;
import com.ticket_system.manage_revenue_ticket.repository.BaseLoyaltyPointsRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class BaseLoyaltyPointsService {

    @Autowired
    private BaseLoyaltyPointsRepository loyaltyRepo;

    public int calculatePoints(String role, int ticketsBought) {
        Pageable limitOne = PageRequest.of(0, 1);
        List<BaseLoyaltyPoints> config = loyaltyRepo.findActiveByRole(role,limitOne);
        if(config.isEmpty()){
            throw new RuntimeException("Not Found Base Point");
        }
        BaseLoyaltyPoints  firstElement = config.getFirst();
        int points = ticketsBought / firstElement.getTicketsPerPoint();
        return points * firstElement.getPointValue().intValue();
    }

    public BaseLoyaltyPoints create(BaseLoyaltyPointsRequest request) {
        System.out.println("request"+request);
        BaseLoyaltyPoints config = new BaseLoyaltyPoints();
        config.setRoleName(request.getRoleName());
        config.setTicketsPerPoint(request.getTicketsPerPoint());
        config.setPointValue(request.getPointValue());
        config.setMaxPointsPerMonth(request.getMaxPointsPerMonth());
        config.setStartDate(request.getStartDate());
        config.setEndDate(request.getEndDate());
        config.setStatus(request.getStatus() != null ? request.getStatus() : BaseLoyaltyPointStatus.ACTIVE);
        return loyaltyRepo.save(config);
    }

    // UPDATE
    public BaseLoyaltyPoints update(Long id, BaseLoyaltyPointsRequest request) {
        BaseLoyaltyPoints existing = loyaltyRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy cấu hình tích điểm với id: " + id));

        existing.setRoleName(request.getRoleName());
        existing.setTicketsPerPoint(request.getTicketsPerPoint());
        existing.setPointValue(request.getPointValue());
        existing.setMaxPointsPerMonth(request.getMaxPointsPerMonth());
        existing.setStartDate(request.getStartDate());
        existing.setEndDate(request.getEndDate());
        existing.setStatus(request.getStatus());

        return loyaltyRepo.save(existing);
    }
}
