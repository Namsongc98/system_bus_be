package com.ticket_system.manage_revenue_ticket.projection;

import com.ticket_system.common.Enum.UserRole;

/** What the auth interceptor needs about the caller on every request. */
public interface UserAuthStateProjection {
    Boolean getIsActive();
    UserRole getRole();
}
