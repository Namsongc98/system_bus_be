package com.ticket_system.manage_revenue_ticket.interceptor;

import com.ticket_system.common.Enum.UserRole;
import com.ticket_system.manage_revenue_ticket.interceptor.AccountStatusLookup.AccountState;
import com.ticket_system.manage_revenue_ticket.interceptor.AccountStatusLookup.AccountStatus;
import com.ticket_system.manage_revenue_ticket.projection.UserAuthStateProjection;
import com.ticket_system.manage_revenue_ticket.repository.UserRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** The D3 gate behind every authenticated request: users row → ACTIVE / LOCKED / MISSING + DB role. */
class UserAccountStatusLookupTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final UserAccountStatusLookup lookup = new UserAccountStatusLookup(userRepository);

    @Test
    void activeUserCarriesTheDatabaseRole() {
        when(userRepository.findAuthStateById(1L)).thenReturn(Optional.of(row(true, UserRole.CUSTOMER)));

        assertThat(lookup.stateOf(1L)).isEqualTo(new AccountState(AccountStatus.ACTIVE, UserRole.CUSTOMER));
    }

    @Test
    void lockedUserIsLocked() {
        when(userRepository.findAuthStateById(2L)).thenReturn(Optional.of(row(false, UserRole.ADMIN)));

        assertThat(lookup.stateOf(2L).status()).isEqualTo(AccountStatus.LOCKED);
    }

    @Test
    void deletedUserIsMissing() {
        when(userRepository.findAuthStateById(3L)).thenReturn(Optional.empty());

        assertThat(lookup.stateOf(3L)).isEqualTo(AccountState.MISSING);
    }

    @Test
    void nullIdIsMissingWithoutAQuery() {
        assertThat(lookup.stateOf(null)).isEqualTo(AccountState.MISSING);
        verifyNoInteractions(userRepository);
    }

    private static UserAuthStateProjection row(Boolean active, UserRole role) {
        return new UserAuthStateProjection() {
            @Override public Boolean getIsActive() { return active; }
            @Override public UserRole getRole() { return role; }
        };
    }
}
