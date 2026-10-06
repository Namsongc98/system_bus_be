package com.ticket_system.booking_ticket.security;

import com.ticket_system.booking_ticket.repository.UserRepository;
import com.ticket_system.booking_ticket.repository.UserRepository.BookingAccount;
import com.ticket_system.common.Enum.UserRole;
import com.ticket_system.common.exception.AccountLockedException;
import com.ticket_system.common.exception.UnauthorizedRoleException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** B33: a token outlives a lock or a role change; the database decides on every booking. */
class BookingAccountGuardTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final BookingAccountGuard guard = new BookingAccountGuard(userRepository);

    @Test
    void activeCustomerMayBook() {
        when(userRepository.findBookingAccountById(7L)).thenReturn(Optional.of(account(UserRole.CUSTOMER, true)));

        assertThat(guard.requireActiveCustomer(7L).getEmail()).isEqualTo("c@test.vn");
    }

    @Test
    void lockedCustomerIsForbidden() {
        when(userRepository.findBookingAccountById(7L)).thenReturn(Optional.of(account(UserRole.CUSTOMER, false)));

        assertThatThrownBy(() -> guard.requireActiveCustomer(7L)).isInstanceOf(AccountLockedException.class);
    }

    @ParameterizedTest
    @EnumSource(value = UserRole.class, names = "CUSTOMER", mode = EnumSource.Mode.EXCLUDE)
    void otherRolesAreForbiddenEvenWithACustomerToken(UserRole role) {
        // The role comes from the database: a customer demoted (or promoted) since login cannot book.
        when(userRepository.findBookingAccountById(7L)).thenReturn(Optional.of(account(role, true)));

        assertThatThrownBy(() -> guard.requireActiveCustomer(7L)).isInstanceOf(UnauthorizedRoleException.class);
    }

    @Test
    void deletedAccountIsUnauthorized() {
        when(userRepository.findBookingAccountById(7L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> guard.requireActiveCustomer(7L)).isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> guard.requireActiveCustomer(null)).isInstanceOf(SecurityException.class);
    }

    private static BookingAccount account(UserRole role, boolean active) {
        return new BookingAccount() {
            @Override public String getEmail() { return "c@test.vn"; }
            @Override public UserRole getRole() { return role; }
            @Override public Boolean getIsActive() { return active; }
        };
    }
}
