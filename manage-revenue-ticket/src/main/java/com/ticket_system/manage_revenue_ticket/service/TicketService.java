package com.ticket_system.manage_revenue_ticket.service;


import com.ticket_system.common.Dto.request.TicketRequestDto;
import com.ticket_system.common.exception.ConflictException;
import com.ticket_system.manage_revenue_ticket.Enum.TicketStatus;
import com.ticket_system.manage_revenue_ticket.entity.*;
import com.ticket_system.common.exception.ResourceNotFoundException;
import com.ticket_system.manage_revenue_ticket.repository.*;
import com.ticket_system.manage_revenue_ticket.Enum.CustomerStatus;
import com.ticket_system.manage_revenue_ticket.Enum.TransactionType;
import com.ticket_system.manage_revenue_ticket.Enum.TripStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class TicketService {
    // Tên constraint trong V2__tickets_unique_active_seat.sql
    static final String ACTIVE_SEAT_CONSTRAINT = "uk_tickets_trip_active_seat";

    private final TicketRepository ticketRepository;
    private final TripRepository tripRepository;
    private final UserRepository userRepository;
    private final LoyaltyPointRepository loyaltyPointRepository;
    private final LoyaltyRewardRepository loyaltyRewardRepository;
    private final ApplicationEventPublisher eventPublisher;

    @PersistenceContext
    private EntityManager entityManager;

    public TicketService(TicketRepository ticketRepository,
                         TripRepository tripRepository,
                         UserRepository userRepository,
                         LoyaltyPointRepository loyaltyPointRepository,
                         LoyaltyRewardRepository loyaltyRewardRepository,
                         ApplicationEventPublisher eventPublisher) {
        this.ticketRepository = ticketRepository;
        this.tripRepository = tripRepository;
        this.userRepository = userRepository;
        this.loyaltyPointRepository = loyaltyPointRepository;
        this.loyaltyRewardRepository = loyaltyRewardRepository;
        this.eventPublisher = eventPublisher;
    }

    // tạo vé
    @Transactional
    public Ticket createTicket(TicketRequestDto responseDto){
        // tách các service thay vì truy cập vào repository
        // Trip row lock: a concurrent cancel / start of the trip waits (spec 1.3, TripService).
        Trip trip =  tripRepository.findByIdForUpdate(responseDto.getTripId())
                .orElseThrow(()->new ResourceNotFoundException("Không tìm thấy chuyến xe " + responseDto.getTripId()));
        assertTripOpenForBooking(trip);
        User customer = userRepository.findById(responseDto.getCustomerId())
                .orElseThrow(()->new ResourceNotFoundException("Không thấy người dùng này"));

        User seller = userRepository.findById(responseDto.getSellerId())
                .orElseThrow(()->new ResourceNotFoundException("Không thấy người bán này"));

        if(customer.getUserStatus() == CustomerStatus.BOOKED){
            throw new IllegalArgumentException("Khách hàng đã đặt vé");
        };
        assertCanBookSeat(trip, responseDto.getSeatNumber());
        Ticket ticket = Ticket.builder()
                .trip(trip)
                .seller(seller)
                .customer(customer)
                .price(responseDto.getPrice())
                .seatNumber(responseDto.getSeatNumber())
                .statusTicket(TicketStatus.PENDING)
                .issuedAt(responseDto.getIssuedAt())
                .build();
        Ticket savedTicket = saveSeat(ticket);
        entityManager.refresh(savedTicket);
        publishIfSeatRebooked(savedTicket);

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
    // Lead review 1.3 L13: khoá row chuyến của vé TRƯỚC khi đọc vé (READ_COMMITTED) — nếu đọc vé trước,
    // một lần huỷ chuyến đang chạy sẽ bị ghi đè bằng bản vé PENDING cũ.
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Ticket updateTicket(Long ticketId, TicketRequestDto responseDto){

        Long ticketTripId = ticketRepository.findTripIdById(ticketId)
                .orElseThrow(()->new ResourceNotFoundException("Không tìm thấy vé xe " + ticketId));
        // Ghế/giá chỉ sửa được khi chuyến của chính vé còn mở bán (spec 1.3 S10).
        assertTripOpenForBooking(tripRepository.findByIdForUpdate(ticketTripId)
                .orElseThrow(()->new ResourceNotFoundException("Không tìm thấy chuyến xe " + ticketTripId)));
        Ticket ticket = lockedTicketOn(ticketTripId, ticketId);

        tripRepository.findById(responseDto.getTripId())
                .orElseThrow(()->new ResourceNotFoundException("Không tìm thấy chuyến xe " + responseDto.getTripId()));
        User customer = userRepository.findById(responseDto.getCustomerId())
                .orElseThrow(()->new ResourceNotFoundException("Không thấy người dùng này"));

        User seller = userRepository.findById(responseDto.getSellerId())
                .orElseThrow(()->new ResourceNotFoundException("Không thấy người bán này"));
        if(responseDto.getPrice() != null) ticket.setPrice(responseDto.getPrice());
        boolean seatChanged = responseDto.getSeatNumber() != null
                && !responseDto.getSeatNumber().equals(ticket.getSeatNumber());
        if(seatChanged){
            // Ghế thuộc chuyến của chính vé, không phải tripId trong body.
            assertSeatFree(ticket.getTrip(), responseDto.getSeatNumber(), ticket.getId());
            ticket.setSeatNumber(responseDto.getSeatNumber());
        }
        ticket.setSeller(seller);
        ticket.setCustomer(customer);
        customer.setUserStatus(CustomerStatus.BOOKED);
        userRepository.save(customer);
        saveSeat(ticket);
        if(seatChanged){
            publishIfSeatRebooked(ticket);
        }
        return ticket;
    };

    public List<Map<String, Object>> getTicketSummaryByBusAndTime(Long busId, LocalDateTime fromDate, LocalDateTime toDate) {
        return ticketRepository.getTicketSummaryByBusAndTime(busId, fromDate, toDate);
    }
    @Transactional
    public Ticket createTicketByLoyalty(TicketRequestDto responseDto, Long idReward){
        // Trip row lock: a concurrent cancel / start of the trip waits (spec 1.3, TripService).
        Trip trip =  tripRepository.findByIdForUpdate(responseDto.getTripId())
                .orElseThrow(()->new ResourceNotFoundException("Không tìm thấy chuyến xe " + responseDto.getTripId()));
        assertTripOpenForBooking(trip);
        User customer = userRepository.findById(responseDto.getCustomerId())
                .orElseThrow(()->new ResourceNotFoundException("Không thấy người dùng này"));

        User seller = userRepository.findById(responseDto.getSellerId())
                .orElseThrow(()->new ResourceNotFoundException("Không thấy người bán này"));

        if(customer.getUserStatus() == CustomerStatus.BOOKED){
            throw new IllegalArgumentException("Khách hàng đã đặt vé");
        };
        assertCanBookSeat(trip, responseDto.getSeatNumber());

        LoyaltyReward reward = loyaltyRewardRepository.findById(idReward)
                .orElseThrow(()->new ResourceNotFoundException("Không thấy ưu đãi này"));
         if(reward.getStatus() == LoyaltyReward.Status.INACTIVE){
             throw new ConflictException("Phần thưởng không còn hoạt động");
         }
        LoyaltyPoint loyaltyPoint = loyaltyPointRepository.findAllByCustomerIdOrderByUpdatedAtDesc(customer.getId())
                .stream()
                .findFirst()
                .orElseThrow(() -> new ConflictException("Bạn chưa có điểm nào"));
// Lấy LoyaltyPoint thực tế

// Lấy tổng điểm hiện có của khách hàng
        int totalPointCustomer = loyaltyPoint.getPoints();

// So sánh
        if (totalPointCustomer < reward.getPointsRequired()) {
            throw new ConflictException("Bạn không đủ điểm để đổi phần thưởng này.");
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
        saveSeat(ticket);
        publishIfSeatRebooked(ticket);

        customer.setUserStatus(CustomerStatus.BOOKED);
        userRepository.save(customer);
        return ticket;
    }

    // Chỉ chuyến SCHEDULED nhận đặt / sửa vé: chuyến đã chạy, xong hoặc bị huỷ thì không (spec 1.3 S10).
    private static void assertTripOpenForBooking(Trip trip) {
        if(trip.getStatus() != TripStatus.SCHEDULED){
            throw new ConflictException("Chuyến xe không còn nhận đặt vé (trạng thái " + trip.getStatus() + ")");
        }
    }

    // Tạo vé mới: số ghế hợp lệ, chuyến còn chỗ, ghế chưa có người (spec review 0.8: D2, D3).
    private void assertCanBookSeat(Trip trip, Integer seatNumber) {
        Buses bus = trip.getBus();
        long activeTickets = ticketRepository.countActiveTicketsByTripId(trip.getId());
        if(activeTickets >= bus.getCapacity()){
            throw new ConflictException("Chuyến xe đã hết chỗ (" + activeTickets + "/" + bus.getCapacity() + ")");
        }
        assertSeatFree(trip, seatNumber, null);
    }

    private void assertSeatFree(Trip trip, Integer seatNumber, Long excludeTicketId) {
        int capacity = trip.getBus().getCapacity();
        if(seatNumber == null || seatNumber < 1 || seatNumber > capacity){
            throw new IllegalArgumentException("Số ghế phải từ 1 đến " + capacity);
        }
        if(ticketRepository.existsActiveSeat(trip.getId(), seatNumber, excludeTicketId)){
            throw new ConflictException("Ghế " + seatNumber + " đã có người đặt");
        }
    }

    // Lớp chặn cuối: 2 request cùng ghế lọt qua check trên → unique constraint của DB.
    private Ticket saveSeat(Ticket ticket) {
        try {
            return ticketRepository.saveAndFlush(ticket);
        } catch (DataIntegrityViolationException ex) {
            String cause = ex.getMostSpecificCause().getMessage();
            if(cause != null && cause.contains(ACTIVE_SEAT_CONSTRAINT)){
                throw new ConflictException("Ghế " + ticket.getSeatNumber() + " đã có người đặt");
            }
            throw ex;
        }
    }

    // N1: ghế từng có vé bị huỷ được đặt lại → báo ADMIN gọi cho người vừa đặt (sau commit).
    private void publishIfSeatRebooked(Ticket ticket) {
        Long tripId = ticket.getTrip().getId();
        if(ticketRepository.existsCancelledSeat(tripId, ticket.getSeatNumber())){
            Long customerId = ticket.getCustomer() == null ? null : ticket.getCustomer().getId();
            eventPublisher.publishEvent(
                    new SeatRebookedEvent(tripId, ticket.getSeatNumber(), ticket.getId(), customerId));
        }
    }

    // Vé đọc SAU khi đã khoá row chuyến của nó. Vé đã huỷ → 409 (sửa hay huỷ lại đều vậy). Chuyến của vé
    // không đổi được qua API; nếu khác chuyến vừa khoá thì từ chối để người gọi thử lại (lead review 1.3 L22).
    private Ticket lockedTicketOn(Long lockedTripId, Long ticketId) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(()->new ResourceNotFoundException("Không tìm thấy vé xe " + ticketId));
        if(!lockedTripId.equals(ticket.getTrip().getId())){
            throw new ConflictException("Vé " + ticketId + " vừa thay đổi, vui lòng thử lại");
        }
        if(ticket.getStatusTicket() == TicketStatus.CANCELLED){
            throw new ConflictException("Vé " + ticketId + " đã bị huỷ");
        }
        return ticket;
    }

    // hủy vé — cùng thứ tự khoá với updateTicket / TripService: khoá row chuyến rồi mới đọc vé (lead review 1.3 L20).
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Ticket cancelTicket(Long ticketId){
        Long ticketTripId = ticketRepository.findTripIdById(ticketId)
                .orElseThrow(()->new ResourceNotFoundException("Không tìm thấy vé xe " + ticketId));
        tripRepository.findByIdForUpdate(ticketTripId)
                .orElseThrow(()->new ResourceNotFoundException("Không tìm thấy chuyến xe " + ticketTripId));
        Ticket ticket = lockedTicketOn(ticketTripId, ticketId);

        ticket.setStatusTicket(TicketStatus.CANCELLED);
        ticketRepository.saveAndFlush(ticket);

        // Khách chỉ về NOT_BOOKED khi không còn vé chưa huỷ nào khác (như TripService.cancelTickets).
        User customer = ticket.getCustomer();
        if(customer != null
                && !ticketRepository.existsByCustomerIdAndStatusTicketNot(customer.getId(), TicketStatus.CANCELLED)){
            customer.setUserStatus(CustomerStatus.NOT_BOOKED);
            userRepository.save(customer);
        }
        return ticket;
    }
}
