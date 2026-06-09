package com.ticket_system.booking_ticket.service;

import com.ticket_system.booking_ticket.client.RevenueClient;
import com.ticket_system.booking_ticket.dto.request.*;
import com.ticket_system.booking_ticket.entity.*;
import com.ticket_system.booking_ticket.repository.*;
import com.ticket_system.common.Enum.UserRole;
import com.ticket_system.common.exception.ResourceNotFoundException;
import com.ticket_system.booking_ticket.dto.response.TicketResponseDto;
import com.ticket_system.booking_ticket.Enum.CustomerStatus;
import com.ticket_system.booking_ticket.Enum.TransactionType;
import com.ticket_system.booking_ticket.Enum.TripStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class TicketService {
    @Autowired
    private TicketRepository ticketRepository;

//    @Autowired
//    private TripRepository tripRepository;
//
//    @Autowired
//    private UserRepository userRepository;
//
//    @Autowired
//    private BusesRepository busesRepository;
//
//    @Autowired
//    private BaseLoyaltyPointsService baseLoyaltyPointsService;
//
//    @Autowired
//    private LoyaltyPointRepository loyaltyPointRepository;
//
//    @Autowired
//    private BaseLoyaltyPointsRepository baseLoyaltyPointsRepository;
//
//    @Autowired
//    private LoyaltyPointsService loyaltyPointsService;
//
//    @Autowired
//    LoyaltyRewardRepository loyaltyRewardRepository;

  @Autowired
  private RevenueClient revenueClient;

    // tạo vé
    public Ticket createTicket(TicketResponseDto responseDto){

      Trip trip = revenueClient.getTrip(responseDto.getTripId());

      if(trip.getStatus() == TripStatus.ONGOING){
        throw new IllegalArgumentException("Chuyến xe này đang trong chuyến!");
      }

      // ===== CALL USER SERVICE =====

      User customer = revenueClient.getUser(responseDto.getCustomerId());

      User seller = revenueClient.getUser(responseDto.getSellerId());

      if(customer.getUserStatus().equals(CustomerStatus.BOOKED.name())){
        throw new IllegalArgumentException("Khách hàng đã đặt vé");
      }

      // ===== CHECK BUS CAPACITY =====

      Buses bus = trip.getBus();

      int countTicket = ticketRepository.countTicketsByBusId(bus.getId());

      if(countTicket == bus.getCapacity()){
        throw new ResourceNotFoundException(
          "Chuyến đi có " + bus.getCapacity() +
            " vé, hiện đã bán " + countTicket);
      }

      // ===== CREATE TICKET =====

      Ticket ticket = Ticket.builder()
        .trip(trip)
        .seller(seller)
        .customer(customer)
        .price(responseDto.getPrice())
        .seatNumber(responseDto.getSeatNumber())
        .issuedAt(responseDto.getIssuedAt())
        .build();

      ticketRepository.save(ticket);

      // ===== UPDATE USER STATUS =====

      revenueClient.updateUserStatus(
        customer.getId(),
        CustomerStatus.BOOKED.name()
      );

      // ===== GET LOYALTY POINT =====

      List<LoyaltyPointsRequest> loyaltyPoints =
        revenueClient.getLoyaltyPoints(customer.getId());

      BaseLoyaltyPointsRequest basePoint =
        revenueClient.getBasePoint(UserRole.CUSTOMER.name());

      int ticketsPerPoint = basePoint.getTicketsPerPoint();

      // ===== UPDATE POINT =====

      if(loyaltyPoints != null && !loyaltyPoints.isEmpty()){

        LoyaltyPointsRequest latest = loyaltyPoints.get(0);

        latest.setPoints(latest.getPoints() + ticketsPerPoint);

        latest.setTransactionType(TransactionType.valueOf(TransactionType.EARN.name()));

        revenueClient.savePoint(latest);

      } else {

        LoyaltyPointsRequest dto = new LoyaltyPointsRequest();

        dto.setCustomerId(customer.getId());
        dto.setPoints(ticketsPerPoint);
        dto.setTransactionType(TransactionType.valueOf(TransactionType.EARN.name()));
        dto.setDescription("Cộng điểm khi mua thêm vé");

        revenueClient.savePoint(dto);
      }

      return ticket;
    }

}
