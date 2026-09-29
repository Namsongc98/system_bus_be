package com.ticket_system.manage_revenue_ticket.projection;

import com.ticket_system.common.Enum.UserRole;

public interface RoleCountProjection {
    UserRole getRole();
    Long getTotal();
}
