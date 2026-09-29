package com.ticket_system.manage_revenue_ticket.Dto.response;

import com.ticket_system.common.Enum.UserRole;

import java.util.Map;

/** Badge counts for the user-management tabs. byRole has a key for every UserRole (0 when none). */
public record UserCountsResponse(long total, Map<UserRole, Long> byRole) {
}
