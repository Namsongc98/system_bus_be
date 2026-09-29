package com.ticket_system.manage_revenue_ticket.interceptor;

import com.ticket_system.manage_revenue_ticket.repository.UserRepository;
import org.springframework.stereotype.Component;

/**
 * One primary-key read of users.is_active and users.role. Both columns are NOT NULL
 * (V1__baseline.sql), so an empty result means the user no longer exists.
 */
@Component
public class UserAccountStatusLookup implements AccountStatusLookup {

    private final UserRepository userRepository;

    public UserAccountStatusLookup(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public AccountState stateOf(Long userId) {
        if (userId == null) {
            return AccountState.MISSING;
        }
        return userRepository.findAuthStateById(userId)
                .map(row -> Boolean.FALSE.equals(row.getIsActive())
                        ? AccountState.locked(row.getRole())
                        : AccountState.active(row.getRole()))
                .orElse(AccountState.MISSING);
    }
}
