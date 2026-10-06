package com.ticket_system.booking_ticket.security;

import com.ticket_system.booking_ticket.repository.UserRepository;
import com.ticket_system.booking_ticket.repository.UserRepository.BookingAccount;
import com.ticket_system.common.Enum.UserRole;
import com.ticket_system.common.exception.AccountLockedException;
import com.ticket_system.common.exception.UnauthorizedRoleException;
import org.springframework.stereotype.Component;

/**
 * B33: the JWT filter of this service only checks the signature and expiry, so a locked or re-roled
 * account could keep booking with an old token. The database decides on every booking, as the
 * AuthInterceptor of manage-revenue-ticket does (spec 1.2 D3): missing → 401, locked → 403,
 * not a CUSTOMER → 403.
 */
@Component
public class BookingAccountGuard {

    static final String NOT_A_CUSTOMER = "Chỉ tài khoản khách hàng được đặt vé";
    static final String UNKNOWN_ACCOUNT = "Unauthorized - Invalid or missing JWT";

    private final UserRepository userRepository;

    public BookingAccountGuard(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /** The caller's own account, if it may book; its email is where the confirmation goes. */
    public BookingAccount requireActiveCustomer(Long userId) {
        if (userId == null) {
            throw new SecurityException(UNKNOWN_ACCOUNT);
        }
        BookingAccount account = userRepository.findBookingAccountById(userId)
                .orElseThrow(() -> new SecurityException(UNKNOWN_ACCOUNT));
        if (Boolean.FALSE.equals(account.getIsActive())) {
            throw new AccountLockedException();
        }
        if (account.getRole() != UserRole.CUSTOMER) {
            throw new UnauthorizedRoleException(NOT_A_CUSTOMER);
        }
        return account;
    }
}
