package com.ticket_system.manage_revenue_ticket.Dto.response;

import com.ticket_system.common.Enum.UserRole;
import com.ticket_system.manage_revenue_ticket.Enum.DriverStatus;
import com.ticket_system.manage_revenue_ticket.entity.Profile;
import com.ticket_system.manage_revenue_ticket.entity.User;

import java.time.LocalDateTime;

/**
 * A user as returned by /api/user. Never expose the User entity (it carries the password hash).
 * fullName and phone come from the profile and are null when the user has none.
 */
public record UserResponse(
        Long id,
        String email,
        UserRole role,
        boolean active,
        String fullName,
        String phone,
        DriverStatus driverStatus,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static UserResponse from(User user, Profile profile) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getRole(),
                !Boolean.FALSE.equals(user.getIsActive()),
                profile == null ? null : profile.getFullName(),
                profile == null ? null : profile.getPhone(),
                user.getDriverStatus(),
                user.getCreatedAt(),
                user.getUpdatedAt()
        );
    }
}
