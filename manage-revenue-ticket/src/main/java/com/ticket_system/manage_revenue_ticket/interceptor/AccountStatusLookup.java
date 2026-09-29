package com.ticket_system.manage_revenue_ticket.interceptor;

import com.ticket_system.common.Enum.UserRole;

/**
 * State of the account behind a valid JWT, read on every authenticated request so a lock or a
 * role change takes effect at once instead of when the token expires (spec 1.2 D3).
 */
@FunctionalInterface
public interface AccountStatusLookup {

    enum AccountStatus { ACTIVE, LOCKED, MISSING }

    /**
     * {@code role} is the role stored in the database, which wins over the token's claim.
     * A null role means the lookup does not know it (test doubles only; users.role is NOT NULL)
     * and the token's claim is used.
     */
    record AccountState(AccountStatus status, UserRole role) {
        public static final AccountState MISSING = new AccountState(AccountStatus.MISSING, null);

        public static AccountState active(UserRole role) {
            return new AccountState(AccountStatus.ACTIVE, role);
        }

        public static AccountState locked(UserRole role) {
            return new AccountState(AccountStatus.LOCKED, role);
        }
    }

    AccountState stateOf(Long userId);
}
