package com.ticket_system.manage_revenue_ticket.Dto.response;

/** Result of PUT /api/profile/avatar: the stored type (detected from the file bytes) and size. */
public record AvatarResponse(
        String contentType,
        long size
) {
}
