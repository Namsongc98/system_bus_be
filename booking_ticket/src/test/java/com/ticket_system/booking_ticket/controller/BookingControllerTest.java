package com.ticket_system.booking_ticket.controller;

import com.ticket_system.booking_ticket.repository.UserRepository.BookingAccount;
import com.ticket_system.booking_ticket.security.BookingAccountGuard;
import com.ticket_system.booking_ticket.service.BookingProducer;
import com.ticket_system.common.Dto.request.TicketRequestDto;
import com.ticket_system.common.Enum.UserRole;
import com.ticket_system.common.exception.AccountLockedException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** B33: whoever is in the body, the booking is made for the caller of the JWT. */
class BookingControllerTest {

    private final BookingProducer producer = mock(BookingProducer.class);
    private final BookingAccountGuard guard = mock(BookingAccountGuard.class);
    private final BookingController controller = new BookingController(producer, guard);

    @Test
    void bookingUsesTheCallerIdentityNotTheBody() {
        when(guard.requireActiveCustomer(7L)).thenReturn(account("me@test.vn"));
        TicketRequestDto body = new TicketRequestDto();
        body.setTripId(3L);
        body.setSeatNumber(5);
        body.setPrice(new BigDecimal("150000"));
        body.setCustomerId(99L);
        body.setCustomerEmail("victim@test.vn");
        body.setSellerId(42L);

        controller.addTicket(7L, body);

        ArgumentCaptor<TicketRequestDto> sent = ArgumentCaptor.forClass(TicketRequestDto.class);
        verify(producer).sendBookingEvent(sent.capture());
        assertThat(sent.getValue().getCustomerId()).isEqualTo(7L);
        assertThat(sent.getValue().getCustomerEmail()).isEqualTo("me@test.vn");
        assertThat(sent.getValue().getSellerId()).isNull();
        assertThat(sent.getValue().getTripId()).isEqualTo(3L);
        assertThat(sent.getValue().getSeatNumber()).isEqualTo(5);
    }

    @Test
    void lockedAccountPublishesNothing() {
        when(guard.requireActiveCustomer(7L)).thenThrow(new AccountLockedException());

        assertThatThrownBy(() -> controller.addTicket(7L, new TicketRequestDto()))
                .isInstanceOf(AccountLockedException.class);
        verify(producer, never()).sendBookingEvent(any());
    }

    private static BookingAccount account(String email) {
        return new BookingAccount() {
            @Override public String getEmail() { return email; }
            @Override public UserRole getRole() { return UserRole.CUSTOMER; }
            @Override public Boolean getIsActive() { return true; }
        };
    }
}
