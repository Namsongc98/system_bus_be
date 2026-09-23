package com.ticket_system.manage_revenue_ticket.Dto.response;

/**
 * The caller of GET /api/auth/me. fullName and phone come from the user's
 * profile and are null when the user has no profile yet.
 */
public record CurrentUserResponse(
        Long id,
        String email,
        String role,
        String fullName,
        String phone
) {
}
