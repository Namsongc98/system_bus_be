package com.ticket_system.booking_ticket.repository;

import com.ticket_system.booking_ticket.entity.User;
import com.ticket_system.common.Enum.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    /** What a booking needs to know about the caller: one primary-key read, no entity loaded. */
    interface BookingAccount {
        String getEmail();
        UserRole getRole();
        Boolean getIsActive();
    }

    @Query("select u.email as email, u.role as role, u.isActive as isActive from User u where u.id = :id")
    Optional<BookingAccount> findBookingAccountById(@Param("id") Long id);
}
