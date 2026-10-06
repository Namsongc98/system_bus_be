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
import org.junit.jupiter.params.provider.EnumSource;
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
        lenient().when(tripRepository.findByIdForUpdate(TRIP_ID)).thenReturn(Optional.of(trip));
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
    @EnumSource(value = TripStatus.class, names = {"ONGOING", "COMPLETED", "CANCELLED"})
    void createTicketOnATripThatIsNotScheduledIsConflict(TripStatus status) {
        // Spec 1.3 S10: a cancelled or finished trip must not sell tickets.
        trip.setStatus(status);

        assertThatThrownBy(() -> service.createTicket(request(1)))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining(status.name());
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

    @ParameterizedTest
    @EnumSource(value = TripStatus.class, names = {"ONGOING", "COMPLETED", "CANCELLED"})
    void updateTicketOnATripThatIsNotScheduledIsConflict(TripStatus status) {
        // Spec 1.3 S10: the ticket's own trip decides, and it is read with the row lock.
        Ticket existing = existingTicket(1);
        trip.setStatus(status);

        assertThatThrownBy(() -> service.updateTicket(50L, request(2)))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining(status.name());
        assertThat(existing.getSeatNumber()).isEqualTo(1);
        verify(tripRepository).findByIdForUpdate(TRIP_ID);
        verify(ticketRepository, never()).saveAndFlush(any());
    }

    @ParameterizedTest
    @EnumSource(value = TripStatus.class, names = {"ONGOING", "COMPLETED", "CANCELLED"})
    void loyaltyTicketOnATripThatIsNotScheduledIsConflict(TripStatus status) {
        trip.setStatus(status);

        assertThatThrownBy(() -> service.createTicketByLoyalty(request(1), 1L))
                .isInstanceOf(ConflictException.class);
        verify(loyaltyPointRepository, never()).save(any());
        verify(ticketRepository, never()).saveAndFlush(any());
    }

    @Test
    void updateTicketLocksTheTripBeforeReadingTheTicket() {
        // Lead review 1.3 L13: reading the ticket first would let a concurrent trip cancel be overwritten.
        existingTicket(1);

        service.updateTicket(50L, request(1));

        org.mockito.InOrder order = org.mockito.Mockito.inOrder(ticketRepository, tripRepository);
        order.verify(ticketRepository).findTripIdById(50L);
        order.verify(tripRepository).findByIdForUpdate(TRIP_ID);
        order.verify(ticketRepository).findById(50L);
    }

    @Test
    void updatingACancelledTicketIsConflict() {
        Ticket cancelled = existingTicket(1);
        cancelled.setStatusTicket(TicketStatus.CANCELLED);

        assertThatThrownBy(() -> service.updateTicket(50L, request(2))).isInstanceOf(ConflictException.class);
        verify(userRepository, never()).save(any());
        verify(ticketRepository, never()).saveAndFlush(any());
    }

    @Test
    void ticketMovedToAnotherTripAfterTheLockIsConflict() {
        // Lead review 1.3 L22: the ticket must still belong to the trip that was locked.
        Ticket ticket = existingTicket(1);
        Trip other = new Trip();
        other.setId(TRIP_ID + 1);
        ticket.setTrip(other);

        assertThatThrownBy(() -> service.updateTicket(50L, request(1)))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("thử lại");
        verify(ticketRepository, never()).saveAndFlush(any());
    }

    // ─── cancelTicket (lead review 1.3 L20, B36 a) ─────────────────────────

    @Test
    void cancelTicketLocksTheTripBeforeReadingTheTicket() {
        existingTicket(1);

        service.cancelTicket(50L);

        org.mockito.InOrder order = org.mockito.Mockito.inOrder(ticketRepository, tripRepository);
        order.verify(ticketRepository).findTripIdById(50L);
        order.verify(tripRepository).findByIdForUpdate(TRIP_ID);
        order.verify(ticketRepository).findById(50L);
        order.verify(ticketRepository).saveAndFlush(any(Ticket.class));
    }

    @Test
    void cancellingTheLastActiveTicketFreesTheCustomer() {
        Ticket ticket = existingTicket(1);
        customer.setUserStatus(CustomerStatus.BOOKED);
        when(ticketRepository.existsByCustomerIdAndStatusTicketNot(CUSTOMER_ID, TicketStatus.CANCELLED)).thenReturn(false);

        Ticket cancelled = service.cancelTicket(50L);

        assertThat(cancelled.getStatusTicket()).isEqualTo(TicketStatus.CANCELLED);
        assertThat(ticket.getCustomer().getUserStatus()).isEqualTo(CustomerStatus.NOT_BOOKED);
        verify(userRepository).save(customer);
    }

    @Test
    void cancellingKeepsTheCustomerBookedWhileAnotherTicketIsActive() {
        existingTicket(1);
        customer.setUserStatus(CustomerStatus.BOOKED);
        when(ticketRepository.existsByCustomerIdAndStatusTicketNot(CUSTOMER_ID, TicketStatus.CANCELLED)).thenReturn(true);

        service.cancelTicket(50L);

        assertThat(customer.getUserStatus()).isEqualTo(CustomerStatus.BOOKED);
        verify(userRepository, never()).save(any());
    }

    @Test
    void cancellingAnAlreadyCancelledTicketIsConflict() {
        // Same 409 as editing a cancelled ticket (L22: one error code for both).
        existingTicket(1).setStatusTicket(TicketStatus.CANCELLED);

        assertThatThrownBy(() -> service.cancelTicket(50L)).isInstanceOf(ConflictException.class);
        verify(ticketRepository, never()).saveAndFlush(any());
    }

    @Test
    void ticketSalesReadTheTripWithTheRowLock() {
        when(ticketRepository.countActiveTicketsByTripId(TRIP_ID)).thenReturn(0L);
        when(ticketRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        service.createTicket(request(1));

        verify(tripRepository).findByIdForUpdate(TRIP_ID);
        verify(tripRepository, never()).findById(anyLong());
    }

    private Ticket existingTicket(int seat) {
        Ticket ticket = new Ticket();
        ticket.setId(50L);
        ticket.setTrip(trip);
        ticket.setCustomer(customer);
        ticket.setSeatNumber(seat);
        ticket.setStatusTicket(TicketStatus.PENDING);
        lenient().when(ticketRepository.findTripIdById(50L)).thenReturn(Optional.of(TRIP_ID));
        // Not read when the trip is closed: the trip lock and check come first (L13).
        lenient().when(ticketRepository.findById(50L)).thenReturn(Optional.of(ticket));
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
