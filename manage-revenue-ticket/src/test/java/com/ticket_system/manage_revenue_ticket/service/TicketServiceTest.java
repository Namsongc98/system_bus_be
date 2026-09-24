package com.ticket_system.manage_revenue_ticket.service;

import com.ticket_system.common.Dto.request.TicketRequestDto;
import com.ticket_system.common.exception.ConflictException;
import com.ticket_system.manage_revenue_ticket.Enum.CustomerStatus;
import com.ticket_system.manage_revenue_ticket.Enum.TicketStatus;
import com.ticket_system.manage_revenue_ticket.Enum.TripStatus;
import com.ticket_system.manage_revenue_ticket.entity.Buses;
import com.ticket_system.manage_revenue_ticket.entity.Ticket;
import com.ticket_system.manage_revenue_ticket.entity.Trip;
import com.ticket_system.manage_revenue_ticket.entity.User;
import com.ticket_system.manage_revenue_ticket.repository.LoyaltyPointRepository;
import com.ticket_system.manage_revenue_ticket.repository.LoyaltyRewardRepository;
import com.ticket_system.manage_revenue_ticket.repository.TicketRepository;
import com.ticket_system.manage_revenue_ticket.repository.TripRepository;
import com.ticket_system.manage_revenue_ticket.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.sql.SQLIntegrityConstraintViolationException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TicketServiceTest {
    private static final long TRIP_ID = 12L;
    private static final long CUSTOMER_ID = 1L;
    private static final long SELLER_ID = 2L;
    private static final int CAPACITY = 2;

    @Mock private TicketRepository ticketRepository;
    @Mock private TripRepository tripRepository;
    @Mock private UserRepository userRepository;
    @Mock private LoyaltyPointRepository loyaltyPointRepository;
    @Mock private LoyaltyRewardRepository loyaltyRewardRepository;
    @Mock private ApplicationEventPublisher eventPublisher;

    private TicketService service;
    private Trip trip;
    private User customer;

    @BeforeEach
    void setUp() {
        service = new TicketService(ticketRepository, tripRepository, userRepository,
                loyaltyPointRepository, loyaltyRewardRepository, eventPublisher);
        ReflectionTestUtils.setField(service, "entityManager", mock(EntityManager.class));

        Buses bus = new Buses();
        bus.setId(7L);
        bus.setCapacity(CAPACITY);
        trip = new Trip();
        trip.setId(TRIP_ID);
        trip.setBus(bus);
        trip.setStatus(TripStatus.SCHEDULED);
        customer = user(CUSTOMER_ID);

        lenient().when(tripRepository.findById(TRIP_ID)).thenReturn(Optional.of(trip));
        lenient().when(userRepository.findById(CUSTOMER_ID)).thenReturn(Optional.of(customer));
        lenient().when(userRepository.findById(SELLER_ID)).thenReturn(Optional.of(user(SELLER_ID)));
        lenient().when(ticketRepository.saveAndFlush(any(Ticket.class))).thenAnswer(inv -> {
            Ticket saved = inv.getArgument(0);
            if (saved.getId() == null) {
                saved.setId(99L);
            }
            return saved;
        });
    }

    @Test
    void createTicketRejectsFullTrip() {
        when(ticketRepository.countActiveTicketsByTripId(TRIP_ID)).thenReturn((long) CAPACITY);

        assertThatThrownBy(() -> service.createTicket(request(1)))
                .isInstanceOf(ConflictException.class);
        verify(ticketRepository, never()).saveAndFlush(any());
    }

    @Test
    void createTicketRejectsTripAlreadyOverCapacity() {
        // Old code compared with ==, so a trip past capacity was never blocked again.
        when(ticketRepository.countActiveTicketsByTripId(TRIP_ID)).thenReturn(CAPACITY + 1L);

        assertThatThrownBy(() -> service.createTicket(request(1)))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void createTicketSavesFreeSeatWithoutNotification() {
        when(ticketRepository.countActiveTicketsByTripId(TRIP_ID)).thenReturn(1L);
        when(ticketRepository.existsActiveSeat(TRIP_ID, 1, null)).thenReturn(false);
        when(ticketRepository.existsCancelledSeat(TRIP_ID, 1)).thenReturn(false);

        Ticket ticket = service.createTicket(request(1));

        assertThat(ticket.getSeatNumber()).isEqualTo(1);
        assertThat(ticket.getStatusTicket()).isEqualTo(TicketStatus.PENDING);
        verify(eventPublisher, never()).publishEvent(any(Object.class));
    }

    @Test
    void createTicketRejectsTakenSeat() {
        when(ticketRepository.countActiveTicketsByTripId(TRIP_ID)).thenReturn(1L);
        when(ticketRepository.existsActiveSeat(TRIP_ID, 1, null)).thenReturn(true);

        assertThatThrownBy(() -> service.createTicket(request(1)))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Ghế 1");
        verify(ticketRepository, never()).saveAndFlush(any());
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(ints = {0, -1, CAPACITY + 1})
    void createTicketRejectsSeatOutsideBus(Integer seat) {
        when(ticketRepository.countActiveTicketsByTripId(TRIP_ID)).thenReturn(0L);

        assertThatThrownBy(() -> service.createTicket(request(seat)))
                .isInstanceOf(IllegalArgumentException.class);
        verify(ticketRepository, never()).saveAndFlush(any());
    }

    @Test
    void createTicketTurnsUniqueSeatViolationIntoConflict() {
        when(ticketRepository.countActiveTicketsByTripId(TRIP_ID)).thenReturn(0L);
        when(ticketRepository.existsActiveSeat(TRIP_ID, 1, null)).thenReturn(false);
        when(ticketRepository.saveAndFlush(any(Ticket.class))).thenThrow(integrityViolation(
                "Duplicate entry '12-1' for key 'tickets.uk_tickets_trip_active_seat'"));

        assertThatThrownBy(() -> service.createTicket(request(1)))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void createTicketRethrowsOtherIntegrityViolations() {
        when(ticketRepository.countActiveTicketsByTripId(TRIP_ID)).thenReturn(0L);
        when(ticketRepository.existsActiveSeat(TRIP_ID, 1, null)).thenReturn(false);
        when(ticketRepository.saveAndFlush(any(Ticket.class))).thenThrow(integrityViolation(
                "Cannot add or update a child row: a foreign key constraint fails"));

        assertThatThrownBy(() -> service.createTicket(request(1)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void createTicketOnPreviouslyCancelledSeatPublishesRebookedEvent() {
        when(ticketRepository.countActiveTicketsByTripId(TRIP_ID)).thenReturn(0L);
        when(ticketRepository.existsActiveSeat(TRIP_ID, 1, null)).thenReturn(false);
        when(ticketRepository.existsCancelledSeat(TRIP_ID, 1)).thenReturn(true);

        service.createTicket(request(1));

        verify(eventPublisher).publishEvent(new SeatRebookedEvent(TRIP_ID, 1, 99L, CUSTOMER_ID));
    }

    @Test
    void updateTicketRejectsMoveToTakenSeatAndKeepsOldSeat() {
        Ticket existing = existingTicket(1);
        when(ticketRepository.existsActiveSeat(TRIP_ID, 2, 50L)).thenReturn(true);

        assertThatThrownBy(() -> service.updateTicket(50L, request(2)))
                .isInstanceOf(ConflictException.class);
        assertThat(existing.getSeatNumber()).isEqualTo(1);
        verify(ticketRepository, never()).saveAndFlush(any());
    }

    @Test
    void updateTicketKeepingSeatSkipsSeatChecksAndNotification() {
        existingTicket(1);

        service.updateTicket(50L, request(1));

        verify(ticketRepository, never()).existsActiveSeat(anyLong(), anyInt(), any());
        verify(ticketRepository, never()).existsCancelledSeat(anyLong(), anyInt());
        verify(eventPublisher, never()).publishEvent(any(Object.class));
    }

    @Test
    void updateTicketMovingToPreviouslyCancelledSeatPublishesRebookedEvent() {
        Ticket existing = existingTicket(1);
        when(ticketRepository.existsActiveSeat(TRIP_ID, 2, 50L)).thenReturn(false);
        when(ticketRepository.existsCancelledSeat(TRIP_ID, 2)).thenReturn(true);

        service.updateTicket(50L, request(2));

        assertThat(existing.getSeatNumber()).isEqualTo(2);
        verify(eventPublisher).publishEvent(new SeatRebookedEvent(TRIP_ID, 2, 50L, CUSTOMER_ID));
    }

    private Ticket existingTicket(int seat) {
        Ticket ticket = new Ticket();
        ticket.setId(50L);
        ticket.setTrip(trip);
        ticket.setCustomer(customer);
        ticket.setSeatNumber(seat);
        ticket.setStatusTicket(TicketStatus.PENDING);
        when(ticketRepository.findById(50L)).thenReturn(Optional.of(ticket));
        return ticket;
    }

    private static TicketRequestDto request(Integer seat) {
        TicketRequestDto dto = new TicketRequestDto();
        dto.setTripId(TRIP_ID);
        dto.setCustomerId(CUSTOMER_ID);
        dto.setSellerId(SELLER_ID);
        dto.setSeatNumber(seat);
        dto.setPrice(new BigDecimal("100000"));
        return dto;
    }

    private static User user(long id) {
        User user = new User();
        user.setId(id);
        user.setUserStatus(CustomerStatus.NOT_BOOKED);
        return user;
    }

    private static DataIntegrityViolationException integrityViolation(String sqlMessage) {
        return new DataIntegrityViolationException("could not execute statement",
                new SQLIntegrityConstraintViolationException(sqlMessage));
    }
}
