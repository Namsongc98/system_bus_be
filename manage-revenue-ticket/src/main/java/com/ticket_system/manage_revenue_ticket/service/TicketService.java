package com.ticket_system.manage_revenue_ticket.service;


import com.ticket_system.common.Dto.request.TicketRequestDto;
import com.ticket_system.manage_revenue_ticket.Enum.TicketStatus;
import com.ticket_system.manage_revenue_ticket.entity.*;
import com.ticket_system.manage_revenue_ticket.repository.*;
import com.ticket_system.manage_revenue_ticket.entity.*;
import com.ticket_system.common.exception.ResourceNotFoundException;
import com.ticket_system.manage_revenue_ticket.repository.*;
import com.ticket_system.manage_revenue_ticket.Enum.CustomerStatus;
import com.ticket_system.manage_revenue_ticket.Enum.TransactionType;
import com.ticket_system.manage_revenue_ticket.Enum.TripStatus;
import com.ticket_system.common.Enum.UserRole;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class TicketService {
    @Autowired
    private TicketRepository ticketRepository;

    @Autowired
    private TripRepository tripRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private BusesRepository busesRepository;

    @Autowired
    private BaseLoyaltyPointsService baseLoyaltyPointsService;

    @Autowired
    private LoyaltyPointRepository loyaltyPointRepository;

    @Autowired
    private BaseLoyaltyPointsRepository baseLoyaltyPointsRepository;

    @Autowired
    private LoyaltyPointsService loyaltyPointsService;

    @Autowired
    LoyaltyRewardRepository loyaltyRewardRepository;

    @PersistenceContext
    private EntityManager entityManager;

    // tạo vé
    public Ticket createTicket(TicketRequestDto responseDto){
        // tách các service thay vì truy cập vào repository
        Trip trip =  tripRepository.findById(responseDto.getTripId())
                .orElseThrow(()->new ResourceNotFoundException("Không tìm thấy chuyến xe " + responseDto.getTripId()));
        if(trip.getStatus() == TripStatus.ONGOING){
            throw new IllegalArgumentException("Chuyến xe này đang trong chuyến!");
        };
        User customer = userRepository.findById(responseDto.getCustomerId())
                .orElseThrow(()->new ResourceNotFoundException("Không thấy người dùng này"));

        User seller = userRepository.findById(responseDto.getSellerId())
                .orElseThrow(()->new ResourceNotFoundException("Không thấy người bán này"));

        if(customer.getUserStatus() == CustomerStatus.BOOKED){
            throw new IllegalArgumentException("Khách hàng đã đặt vé");
        };
        Buses bus = trip.getBus();
        int countTicket = ticketRepository.countTicketsByBusId(bus.getId());
        if(countTicket == bus.getCapacity()){
            throw  new ResourceNotFoundException("chuyến đi có số lượng "+ bus.getCapacity() + " số vé "+countTicket+" đã hết vé");
        }
        Ticket ticket = Ticket.builder()
                .trip(trip)
                .seller(seller)
                .customer(customer)
                .price(responseDto.getPrice())
                .seatNumber(responseDto.getSeatNumber())
                .statusTicket(TicketStatus.PENDING)
                .issuedAt(responseDto.getIssuedAt())
                .build();
        Ticket savedTicket = ticketRepository.save(ticket);
        entityManager.flush();
        entityManager.refresh(savedTicket);

//        List<LoyaltyPoint> loyaltyPoint = loyaltyPointRepository.findAllByCustomerIdOrderByUpdatedAtDesc(customer.getId());
//        customer.setUserStatus(CustomerStatus.BOOKED);
//        userRepository.save(customer);
//        List<BaseLoyaltyPoints> baseLoyaltyPoints = baseLoyaltyPointsRepository.findLatestActiveByRole(UserRole.CUSTOMER.name());
//
//        if(baseLoyaltyPoints.isEmpty()){
//            throw new RuntimeException("Not Found");
//        }
//        BaseLoyaltyPoints  firstElement = baseLoyaltyPoints.getFirst();
//        int ticketsPerPoint = firstElement.getTicketsPerPoint();
//
//        if (loyaltyPoint != null && !loyaltyPoint.isEmpty()) {
//            LoyaltyPoint latestPoint = loyaltyPoint.get(0); // lấy phần tử đầu tiên
//            // 3️⃣ Nếu đã có, kiểm tra tổng số vé để cộng thêm điểm
//            latestPoint.setPoints(latestPoint.getPoints() + ticketsPerPoint);
//            latestPoint.setTransactionType(TransactionType.EARN);
//            loyaltyPointRepository.save(latestPoint);
//        } else {
//            LoyaltyPoint loyaltyPoints = LoyaltyPoint.builder()
//                    .customer(customer)
//                    .points(ticketsPerPoint )
//                    .transactionType(TransactionType.EARN)
//                    .description("Cộng điểm khi mua thêm vé")
//                    .build();
//            loyaltyPointRepository.save(loyaltyPoints);
//        }
        return savedTicket;
    };
    // update vé
    public Ticket updateTicket(Long ticketId, TicketRequestDto responseDto){

        Ticket ticket;
        ticket = ticketRepository.findById(ticketId)
                .orElseThrow(()->new ResourceNotFoundException("Không tìm thấy vé xe " + responseDto.getTripId()));

        Trip trip =  tripRepository.findById(responseDto.getTripId())
                .orElseThrow(()->new ResourceNotFoundException("Không tìm thấy chuyến xe " + responseDto.getTripId()));
        if(trip.getStatus() == TripStatus.ONGOING){
            throw new IllegalArgumentException("Chuyến xe này đang trong chuyến!");
        };
        User customer = userRepository.findById(responseDto.getCustomerId())
                .orElseThrow(()->new ResourceNotFoundException("Không thấy người dùng này"));

        User seller = userRepository.findById(responseDto.getSellerId())
                .orElseThrow(()->new ResourceNotFoundException("Không thấy người bán này"));
        if(responseDto.getPrice() != null) ticket.setPrice(responseDto.getPrice());
        if(responseDto.getSeatNumber() !=null ) ticket.setSeatNumber(responseDto.getSeatNumber());
        ticket.setSeller(seller);
        ticket.setCustomer(customer);
        customer.setUserStatus(CustomerStatus.BOOKED);
        userRepository.save(customer);
        ticketRepository.save(ticket);
        return ticket;
    };

    public List<Map<String, Object>> getTicketSummaryByBusAndTime(Long busId, LocalDateTime fromDate, LocalDateTime toDate) {
        return ticketRepository.getTicketSummaryByBusAndTime(busId, fromDate, toDate);
    }
    public Ticket createTicketByLoyalty(TicketRequestDto responseDto, Long idReward){
        Trip trip =  tripRepository.findById(responseDto.getTripId())
                .orElseThrow(()->new ResourceNotFoundException("Không tìm thấy chuyến xe " + responseDto.getTripId()));
        if(trip.getStatus() == TripStatus.ONGOING){
            throw new IllegalArgumentException("Chuyến xe này đang trong chuyến!");
        };
        User customer = userRepository.findById(responseDto.getCustomerId())
                .orElseThrow(()->new ResourceNotFoundException("Không thấy người dùng này"));

        User seller = userRepository.findById(responseDto.getSellerId())
                .orElseThrow(()->new ResourceNotFoundException("Không thấy người bán này"));

        if(customer.getUserStatus() == CustomerStatus.BOOKED){
            throw new IllegalArgumentException("Khách hàng đã đặt vé");
        };
        Buses bus = trip.getBus();
        int countTicket = ticketRepository.countTicketsByBusId(bus.getId());
        if(countTicket == bus.getCapacity()){
            throw  new ResourceNotFoundException("chuyến đi có số lượng "+ bus.getCapacity() + " số vé "+countTicket+" đã hết vé");
        }

        LoyaltyReward reward = loyaltyRewardRepository.findById(idReward)
                .orElseThrow(()->new ResourceNotFoundException("Không thấy ưu đãi này"));
         if(reward.getStatus() == LoyaltyReward.Status.INACTIVE){
             throw  new RuntimeException("Phần thưởng không còn hoạt động");
         }
        LoyaltyPoint loyaltyPoint = loyaltyPointRepository.findAllByCustomerIdOrderByUpdatedAtDesc(customer.getId())
                .stream()
                .findFirst()
                .orElseThrow(() -> new RuntimeException("bạn chưa có điểm nào"));
// Lấy LoyaltyPoint thực tế

// Lấy tổng điểm hiện có của khách hàng
        int totalPointCustomer = loyaltyPoint.getPoints();

// So sánh
        if (totalPointCustomer < reward.getPointsRequired()) {
            throw new RuntimeException("Bạn không đủ điểm để đổi phần thưởng này.");
        }else {
            int currentPoint = totalPointCustomer - reward.getPointsRequired();
            LoyaltyPoint newLoyaltyPoints = new LoyaltyPoint();
            newLoyaltyPoints.setCustomer(customer);
            newLoyaltyPoints.setPoints(currentPoint);
            newLoyaltyPoints.setDescription("Cộng điểm khi mua thêm vé");
            newLoyaltyPoints.setStatus(LoyaltyPoint.status.REDEEMED);
            newLoyaltyPoints.setTransactionType(TransactionType.EARN);
            loyaltyPointRepository.save(newLoyaltyPoints);
        }
        BigDecimal priceCustomer = new BigDecimal(0);
        if(reward.getRewardType() == LoyaltyReward.RewardType.TICKET_DISCOUNT){
            priceCustomer = responseDto.getPrice().multiply(new BigDecimal("0.5"));
        }
        Ticket ticket = Ticket.builder()
                .trip(trip)
                .seller(seller)
                .customer(customer)
                .price(priceCustomer)
                .seatNumber(responseDto.getSeatNumber())
                .issuedAt(responseDto.getIssuedAt())
                .build();
        ticketRepository.save(ticket);

        customer.setUserStatus(CustomerStatus.BOOKED);
        userRepository.save(customer);
        return ticket;
    }

    // hủy vé
    public Ticket cancelTicket(Long ticketId){
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(()->new ResourceNotFoundException("Không tìm thấy vé xe " + ticketId));

        if(ticket.getStatusTicket() == TicketStatus.CANCELLED){
            throw new IllegalArgumentException("Vé này đã bị hủy trước đó");
        }

        ticket.setStatusTicket(TicketStatus.CANCELLED);
        ticketRepository.save(ticket);

        // Cập nhật trạng thái khách hàng
        User customer = ticket.getCustomer();
        customer.setUserStatus(CustomerStatus.NOT_BOOKED);
        userRepository.save(customer);

        return ticket;
    }
}
